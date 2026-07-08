/*
 * Copyright 2025 EMBL - European Bioinformatics Institute
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package uk.ac.ebi.embl.gff3tools.gff3toff;

import java.nio.ByteBuffer;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.embl.api.entry.Entry;
import uk.ac.ebi.embl.api.entry.EntryFactory;
import uk.ac.ebi.embl.api.entry.Text;
import uk.ac.ebi.embl.api.entry.XRef;
import uk.ac.ebi.embl.api.entry.feature.Feature;
import uk.ac.ebi.embl.api.entry.feature.FeatureFactory;
import uk.ac.ebi.embl.api.entry.feature.SourceFeature;
import uk.ac.ebi.embl.api.entry.location.*;
import uk.ac.ebi.embl.api.entry.qualifier.Qualifier;
import uk.ac.ebi.embl.api.entry.qualifier.QualifierFactory;
import uk.ac.ebi.embl.api.entry.reference.*;
import uk.ac.ebi.embl.api.entry.sequence.Sequence;
import uk.ac.ebi.embl.api.entry.sequence.SequenceFactory;
import uk.ac.ebi.embl.fastareader.SequenceRangeOption;
import uk.ac.ebi.embl.fastareader.SequenceStats;
import uk.ac.ebi.embl.gff3tools.exception.ReadException;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.gff3.TranslationKey;
import uk.ac.ebi.embl.gff3tools.gff3.directives.*;
import uk.ac.ebi.embl.gff3tools.gff3.reader.GFF3FileReader;
import uk.ac.ebi.embl.gff3tools.gff3.reader.OffsetRange;
import uk.ac.ebi.embl.gff3tools.metadata.AuthorData;
import uk.ac.ebi.embl.gff3tools.metadata.CrossReference;
import uk.ac.ebi.embl.gff3tools.metadata.MasterMetadata;
import uk.ac.ebi.embl.gff3tools.metadata.MasterMetadataProvider;
import uk.ac.ebi.embl.gff3tools.metadata.ReferenceData;
import uk.ac.ebi.embl.gff3tools.metadata.SubmissionAccount;
import uk.ac.ebi.embl.gff3tools.metadata.SubmitterDetails;
import uk.ac.ebi.embl.gff3tools.sequence.SequenceLookup;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.FastaHeaderProvider;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.utils.ControlledVocabularyUtils;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.utils.FastaHeader;
import uk.ac.ebi.embl.gff3tools.utils.ConversionEntry;
import uk.ac.ebi.embl.gff3tools.utils.ConversionUtils;
import uk.ac.ebi.embl.gff3tools.utils.OntologyTerm;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;
import uk.ac.ebi.ena.taxonomy.taxon.Taxon;
import uk.ac.ebi.ena.taxonomy.taxon.TaxonFactory;

public class GFF3Mapper {

    private final Map<String, String> qmap = ConversionUtils.getGFF32FFQualifierMap();
    private final EntryFactory entryFactory = new EntryFactory();
    private final FeatureFactory featureFactory = new FeatureFactory();
    private final QualifierFactory qualifierFactory = new QualifierFactory();
    private final LocationFactory locationFactory = new LocationFactory();
    private final SequenceFactory sequenceFactory = new SequenceFactory();
    private final ReferenceFactory referenceFactory = new ReferenceFactory();
    private static final Logger LOGGER = LoggerFactory.getLogger(GFF3Mapper.class);

    Map<String, GFF3Feature> parentFeatures;
    // Used to keep track of features that will be merged using a location join
    Map<String, Feature> joinableFeatureMap;

    Entry entry;
    GFF3FileReader gff3FileReader;
    private StreamingSequenceContext streamingContext;
    private final MasterMetadataProvider metadataProvider;
    private final FastaHeaderProvider headerProvider;
    private final SequenceLookup sequenceLookup;

    public GFF3Mapper(GFF3FileReader gff3FileReader, ValidationContext context) {
        this(gff3FileReader, context, null);
    }

    public GFF3Mapper(GFF3FileReader gff3FileReader, ValidationContext context, SequenceLookup sequenceLookup) {
        parentFeatures = new HashMap<>();
        joinableFeatureMap = new HashMap<>();
        entry = null;
        this.gff3FileReader = gff3FileReader;
        this.metadataProvider =
                context.contains(MasterMetadataProvider.class) ? context.get(MasterMetadataProvider.class) : null;
        this.headerProvider =
                context.contains(FastaHeaderProvider.class) ? context.get(FastaHeaderProvider.class) : null;
        this.sequenceLookup = sequenceLookup;
    }

    StreamingSequenceContext getStreamingContext() {
        return streamingContext;
    }

    public Entry mapGFF3ToEntry(GFF3Annotation gff3Annotation) throws ValidationException, ReadException {

        parentFeatures.clear();
        joinableFeatureMap.clear();
        streamingContext = null;
        entry = entryFactory.createEntry();
        Sequence sequence = sequenceFactory.createSequence();

        SourceFeature sourceFeature = this.featureFactory.createSourceFeature();

        GFF3SequenceRegion sequenceRegion = gff3Annotation.getSequenceRegion();
        if (sequenceRegion != null) {
            entry.setPrimaryAccession(sequenceRegion.accessionId());
            sequence.setAccession(sequenceRegion.accessionId());
            sequence.setVersion(sequenceRegion.accessionVersion().orElse(1));
            Location location = this.locationFactory.createLocalRange(sequenceRegion.start(), sequenceRegion.end());
            Join<Location> compoundJoin = new Join<>();
            compoundJoin.addLocation(location);
            sourceFeature.setLocations(compoundJoin);
        }

        entry.addFeature(sourceFeature);
        entry.setSequence(sequence);

        applyMasterMetadata(sequenceRegion, entry, sequence, sourceFeature);
        applyFastaHeader(sequenceRegion, entry, sequence, sourceFeature);

        for (GFF3Feature gff3Feature : gff3Annotation.getFeatures()) {
            if (gff3Feature.getId().isPresent()) {
                parentFeatures.put(gff3Feature.getId().get(), gff3Feature);
            }

            mapGFF3Feature(gff3Feature, gff3FileReader.getTranslationOffsetMap());
        }

        // Load sequence data after feature mapping to minimise time it spends in memory.
        applySequenceData(sequenceRegion, sequence);

        return entry;
    }

    private void mapGFF3Feature(GFF3Feature gff3Feature, Map<String, OffsetRange> translationMap)
            throws ValidationException {

        String existingID = gff3Feature.getAttribute("ID").orElse(null);
        String featureHashId = existingID == null ? gff3Feature.hashCodeString() : existingID;

        Location location = mapGFF3Location(gff3Feature);
        Feature ffFeature = joinableFeatureMap.get(featureHashId);
        if (ffFeature != null) {
            CompoundLocation<Location> parentFeatureLocation = ffFeature.getLocations();
            // If the compoundlocation isComplement but the new location we are adding is
            // not complement
            // we need to restructure the locations that it contains
            if (parentFeatureLocation.isComplement() && !location.isComplement()) {
                parentFeatureLocation.getLocations().forEach((l) -> {
                    l.setComplement(true);
                    setLocationPartiality(l);
                });
                parentFeatureLocation.setComplement(false);
            } else if (parentFeatureLocation.isComplement() && location.isComplement()) {
                location.setComplement(false);
            } else if (!parentFeatureLocation.isComplement() && location.isComplement()) {
                setLocationPartiality(location);
            }
            parentFeatureLocation.addLocation(location);
        } else {
            String gff3FeatureName = gff3Feature.getName();
            String gff3Id;
            if (ConversionUtils.getOntologyClient().isValidOntologyId(gff3FeatureName)) {
                gff3Id = gff3FeatureName;
            } else {
                gff3Id = ConversionUtils.getOntologyClient()
                        .findTermByNameOrSynonym(gff3FeatureName)
                        .orElse(null);
            }
            if (gff3Feature.getAttribute("Is_circular").isPresent()
                    && OntologyTerm.REGION.ID.equalsIgnoreCase(gff3Id)) {
                // Do not convert "region" features. These are added when doing EMBL->GFF3 mapping to
                // represent circular topologies. The topology in the EMBL mapping will be provided
                // by the fasta headers.
                return;
            }
            LOGGER.debug("Found GFF3ID: \"%s\" for feature \"%s\"".formatted(gff3Id, gff3FeatureName));
            ConversionEntry conversionEntry =
                    ConversionUtils.getINSDCFeatureForSOTerm(gff3Id, getAttributesMap(gff3Feature));
            if (conversionEntry != null) {
                ffFeature = featureFactory.createFeature(conversionEntry.getFeature());
                CompoundLocation<Location> locations = new Join();
                if (location.isComplement()) {
                    locations.setComplement(true);
                    location.setComplement(false);
                }
                locations.addLocation(location);
                ffFeature.setLocations(locations);

                for (String key : gff3Feature.getAttributeKeys()) {
                    List<String> attributes = gff3Feature.getAttributeList(key).orElse(new ArrayList<>());
                    ffFeature.addQualifiers(mapGFF3Attributes(key, attributes));
                }

                // Add qualifiers from feature mapping when it's not present in the flat file qualifier
                for (Map.Entry<String, String> entry :
                        conversionEntry.getQualifiers().entrySet()) {
                    if (ffFeature.getQualifiers(entry.getKey()).isEmpty()) {
                        String value = entry.getValue();
                        if (value == null || value.isEmpty()) {
                            ffFeature.addQualifier(entry.getKey());
                        } else {
                            ffFeature.addQualifier(entry.getKey(), value);
                        }
                    }
                }

                joinableFeatureMap.put(featureHashId, ffFeature);

                // Transform GFF3 translation to /translation qualifier
                mapTranslation(gff3Feature, ffFeature, featureHashId, translationMap);

                entry.addFeature(ffFeature);
            } else {
                throw new ValidationException(
                        "GFF3_UNMAPPED_FEATURE",
                        "The gff3 feature \"%s\" has no equivalent INSDC mapping".formatted(gff3FeatureName));
            }
        }
        if (ffFeature.getQualifiers("gene").isEmpty()) {
            String gene = getGeneForFeature(gff3Feature);
            if (gene != null) {
                ffFeature.addQualifier("gene", gene);
            }
        }
    }

    /**
     * Transform GFF3 translation to /translation qualifier
     */
    private void mapTranslation(
            GFF3Feature gff3Feature, Feature ffFeature, String featureId, Map<String, OffsetRange> translationMap) {
        String translationKey = TranslationKey.of(gff3Feature.accession(), featureId);
        if (translationMap.get(translationKey) != null) {
            ffFeature.addQualifier("translation", gff3FileReader.getTranslation(translationMap.get(translationKey)));
        }
    }

    private void setLocationPartiality(Location location) {
        // Swap partiality in case of individual location complement. This should be
        // done because the location
        // writer swaps partiality in case of the complement of the inner location.
        boolean fivePrime = location.isThreePrimePartial();
        boolean threePrime = location.isFivePrimePartial();

        location.setFivePrimePartial(fivePrime);
        location.setThreePrimePartial(threePrime);
    }

    private String getGeneForFeature(GFF3Feature gff3Feature) {
        if (gff3Feature == null) {
            return null;
        }
        if (gff3Feature.hasAttribute("gene")) {
            return gff3Feature.getAttribute("gene").get();
        } else if (gff3Feature.getParentId().isPresent()) {
            GFF3Feature parent = parentFeatures.get(gff3Feature.getParentId().get());
            return getGeneForFeature(parent);
        } else {
            return null;
        }
    }

    private Location mapGFF3Location(GFF3Feature gff3Feature) {

        long start = gff3Feature.getStart();
        long end = gff3Feature.getEnd();
        List<String> partials = gff3Feature.getAttributeList("partial").orElse(new ArrayList<>());

        boolean isComplement = gff3Feature.getStrand().equals("-");
        Location location = this.locationFactory.createLocalRange(start, end, isComplement);

        // Set location partiality
        if (partials.contains("start")) {
            location.setFivePrimePartial(true);
        }
        if (partials.contains("end")) {
            location.setThreePrimePartial(true);
        }
        return location;
    }

    private Collection<Qualifier> mapGFF3Attributes(String attributeKey, List<String> attributes) {
        Collection<Qualifier> qualifierList = new ArrayList<>();

        if (qmap.containsKey(attributeKey)) {
            attributeKey = qmap.get(attributeKey);
        }

        if (!attributeKey.isBlank()) {
            for (String val : attributes) {
                qualifierList.add(createQualifier(attributeKey, val));
            }
        }

        return qualifierList;
    }

    private Qualifier createQualifier(String name, String value) {
        if ("true".equals(value)) {
            // Create qualifier without value when value is "true"
            return qualifierFactory.createQualifier(name);
        } else {
            return qualifierFactory.createQualifier(name, value);
        }
    }

    /**
     * Extracts the attributes from a GFF3Feature as a map for use in feature mapping lookup.
     *
     * @param gff3Feature the GFF3 feature to extract attributes from
     * @return a map of attribute names to their values
     */
    /**
     * Populates the nucleotide sequence data on the Sequence object from the SequenceLookup.
     * Gracefully skips if no lookup is available or no sequence region is defined.
     */
    private void applySequenceData(GFF3SequenceRegion sequenceRegion, Sequence sequence) throws ReadException {
        if (sequenceLookup == null || sequenceRegion == null) {
            LOGGER.info(
                    "No sequence source provided — skipping sequence data for '{}'",
                    sequenceRegion != null ? sequenceRegion.accessionId() : "unknown");
            return;
        }
        String seqId = sequenceRegion.accessionId();
        if (!sequenceLookup.knownSeqIds().contains(seqId)) {
            LOGGER.info("Sequence source cannot serve seqId '{}' \u2014 skipping sequence data", seqId);
            return;
        }
        try {
            SequenceStats stats = sequenceLookup.getSequenceStats(seqId);
            long totalBases = stats.totalBases();
            // Stream only whole-sequence regions; sub-ranges fall back to the byte[] path below,
            // whose per-slice base counting is already correct for sub-ranges.
            if (sequenceRegion.start() == 1 && sequenceRegion.end() == totalBases && totalBases > 0) {
                Map<Character, Long> lc = new HashMap<>();
                for (char base : new char[] {'a', 'c', 'g', 't'}) {
                    lc.put(base, 0L);
                }
                for (Map.Entry<Character, Long> e : stats.baseCount().entrySet()) {
                    char lower = Character.toLowerCase(e.getKey());
                    if (lc.containsKey(lower)) {
                        lc.put(lower, e.getValue());
                    }
                }
                sequence.setLength(totalBases);
                this.streamingContext = new StreamingSequenceContext(seqId, totalBases, lc);
                return;
            }
            String nucleotides = sequenceLookup.getSequenceSlice(
                    seqId, sequenceRegion.start(), sequenceRegion.end(), SequenceRangeOption.WHOLE_SEQUENCE);
            if (nucleotides == null || nucleotides.isEmpty()) {
                throw new ReadException("No sequence data returned for '" + sequenceRegion.accessionId() + "'");
            }
            // Convert to lowercase bytes in a single pass to avoid an intermediate
            // String allocation from toLowerCase() (saves ~2N bytes of peak memory).
            byte[] seqBytes = new byte[nucleotides.length()];
            for (int i = 0; i < nucleotides.length(); i++) {
                seqBytes[i] = (byte) Character.toLowerCase(nucleotides.charAt(i));
            }
            sequence.setSequence(ByteBuffer.wrap(seqBytes));
            sequence.setLength((long) seqBytes.length);
        } catch (ReadException e) {
            throw e;
        } catch (Exception e) {
            throw new ReadException(
                    "Failed to retrieve sequence for '" + sequenceRegion.accessionId() + "': " + e.getMessage(),
                    ReadException.wrapAsIOException(e));
        }
    }

    private Map<String, List<String>> getAttributesMap(GFF3Feature gff3Feature) {
        Map<String, List<String>> attributesMap = new HashMap<>();
        for (String key : gff3Feature.getAttributeKeys()) {
            gff3Feature.getAttributeList(key).ifPresent(values -> attributesMap.put(key, values));
        }
        return attributesMap;
    }

    /**
     * Applies FASTA header metadata (description, molecule type, topology, chromosome) to the EMBL
     * entry, sequence, and source feature. Master metadata takes precedence: fields already set by
     * {@link #applyMasterMetadata} are left untouched and the header only fills the gaps.
     */
    private void applyFastaHeader(
            GFF3SequenceRegion sequenceRegion, Entry entry, Sequence sequence, SourceFeature sourceFt) {
        if (headerProvider == null || sequenceRegion == null) {
            return;
        }

        Optional<FastaHeader> opt = headerProvider.getHeader(sequenceRegion.accessionId());
        if (opt.isEmpty()) {
            return;
        }
        FastaHeader h = opt.get();

        // DE line: description
        if (h.getDescription() != null
                && (entry.getDescription() == null || entry.getDescription().getText() == null)) {
            entry.setDescription(new Text(h.getDescription()));
        }

        // Molecule type: ID line field 4 + /mol_type source qualifier
        if (h.getMoleculeType() != null && sequence.getMoleculeType() == null) {
            sequence.setMoleculeType(h.getMoleculeType());
            sourceFt.addQualifier("mol_type", h.getMoleculeType());
        }

        // Topology: ID line field 3
        if (h.getTopology() != null && sequence.getTopology() == null) {
            mapTopology(h.getTopology(), sequence);
        }

        // Chromosome type and name -> /chromosome, /plasmid, etc.
        if (!hasChromosomeQualifier(sourceFt)) {
            if (h.getChromosomeType() != null) {
                mapChromosomeType(h.getChromosomeType(), h.getChromosomeName(), sourceFt);
            } else if (h.getChromosomeName() != null) {
                sourceFt.addQualifier("chromosome", h.getChromosomeName());
            }
        }

        // Chromosome location -> /organelle qualifier
        if (h.getChromosomeLocation() != null
                && sourceFt.getQualifiers("organelle").isEmpty()) {
            mapChromosomeLocation(h.getChromosomeLocation(), sourceFt);
        }
    }

    private boolean hasChromosomeQualifier(SourceFeature sourceFt) {
        return !sourceFt.getQualifiers("chromosome").isEmpty()
                || !sourceFt.getQualifiers("plasmid").isEmpty()
                || !sourceFt.getQualifiers("segment").isEmpty()
                || !sourceFt.getQualifiers("linkage_group").isEmpty();
    }

    /**
     * Applies MasterMetadata to the EMBL entry, sequence, and source feature.
     * Gracefully skips if no metadata provider is available or no metadata is found for the seqId.
     */
    private void applyMasterMetadata(
            GFF3SequenceRegion sequenceRegion, Entry entry, Sequence sequence, SourceFeature sourceFt) {
        if (metadataProvider == null) {
            return;
        }
        if (sequenceRegion == null) {
            return;
        }

        String seqId = sequenceRegion.accessionId();
        Optional<MasterMetadata> opt = metadataProvider.getMetadata(seqId);
        if (opt.isEmpty()) {
            LOGGER.debug("No master metadata found for seqId '{}'; skipping metadata mapping", seqId);
            return;
        }

        MasterMetadata m = opt.get();

        // WGS set master entries describe a SET that contains many per-contig entries.
        // When converting one of those per-contig entries we must materialise contig-
        // level fields (dataClass=WGS, per-contig length, RP, SET cross-references)
        // instead of inheriting the SET-level master fields verbatim.
        boolean isWgsContig = "WGS".equalsIgnoreCase(m.getContigDataclass());
        long contigLength = isWgsContig ? sequenceRegion.end() - sequenceRegion.start() + 1 : 0L;

        // DE line: description (title as fallback)
        if (m.getDescription() != null) {
            entry.setDescription(new Text(m.getDescription()));
        } else if (m.getTitle() != null) {
            entry.setDescription(new Text(m.getTitle()));
        }

        // Molecule type: ID line field 4 + /mol_type source qualifier
        if (m.getMoleculeType() != null) {
            sequence.setMoleculeType(m.getMoleculeType());
            sourceFt.addQualifier("mol_type", m.getMoleculeType());
        }

        // Topology: ID line field 3 (defaults to LINEAR when absent)
        if (m.getTopology() != null) {
            mapTopology(m.getTopology(), sequence);
        }

        // Chromosome type and name
        if (m.getChromosomeType() != null) {
            mapChromosomeType(m.getChromosomeType(), m.getChromosomeName(), sourceFt);
        } else if (m.getChromosomeName() != null) {
            sourceFt.addQualifier("chromosome", m.getChromosomeName());
        }

        // Chromosome location -> /organelle qualifier
        if (m.getChromosomeLocation() != null) {
            mapChromosomeLocation(m.getChromosomeLocation(), sourceFt);
        }

        // Division: ID line taxonomic division field
        if (m.getDivision() != null) {
            entry.setDivision(m.getDivision());
        }

        // Data class: ID line data class field. For WGS contig entries the master's
        // dataClass is "SET" (it describes the containing set), so prefer the contig
        // dataClass when present.
        String effectiveDataClass = isWgsContig ? m.getContigDataclass() : m.getDataClass();
        if (effectiveDataClass != null) {
            entry.setDataClass(effectiveDataClass);
        }

        // Accession: primary accession (only when GFF3 ##sequence-region provides no accession)
        if (m.getAccession() != null && entry.getPrimaryAccession() == null) {
            entry.setPrimaryAccession(m.getAccession());
        }

        // Secondary accessions: AC line
        if (m.getSecondaryAccessions() != null) {
            for (String acc : m.getSecondaryAccessions()) {
                entry.addSecondaryAccession(new Text(acc));
            }
        }

        // Version: ID line version and DT line version
        if (m.getVersion() != null) {
            entry.setVersion(m.getVersion());
            if (sequenceRegion.accessionVersion().isEmpty()) {
                sequence.setVersion(m.getVersion());
            }
        }

        // Keywords: KW line
        if (m.getKeywords() != null) {
            for (String kw : m.getKeywords()) {
                entry.addKeyword(new Text(kw));
            }
        }
        // For WGS contigs the reference flatfile carries `KW   WGS.`; add it when absent.
        if (isWgsContig) {
            boolean hasWgsKeyword = entry.getKeywords().stream()
                    .map(Text::getText)
                    .filter(Objects::nonNull)
                    .anyMatch("WGS"::equalsIgnoreCase);
            if (!hasWgsKeyword) {
                entry.addKeyword(new Text("WGS"));
            }
        }

        // Comment: CC line
        if (m.getComment() != null) {
            // Pre-wrap to EMBL's ~75-col content width. CCWriter preserves
            // submitter-provided line breaks but only force-breaks at 200 cols,
            // so an unwrapped master.json comment would emit as one or two
            // very long CC lines.
            entry.setComment(new Text(wrapCommentText(m.getComment(), CC_LINE_WIDTH)));
        }

        // Taxon: source feature /db_xref "taxon:<value>"
        if (m.getTaxon() != null) {
            sourceFt.addQualifier("db_xref", "taxon:" + m.getTaxon());
        }

        // Scientific name: source feature /organism
        if (m.getScientificName() != null) {
            sourceFt.addQualifier("organism", m.getScientificName());
        }

        // Common name: source feature /note with prefix.
        // WGS contig source features omit the common-name note in the reference output
        // (the common name is implied by /organism + /db_xref). Suppress it to match.
        if (m.getCommonName() != null && !isWgsContig) {
            sourceFt.addQualifier("note", "common name: " + m.getCommonName());
        }

        // Populate the SourceFeature Taxon whenever any taxon-shaped field is set.
        // The EMBL OS/OC writers read scientificName/lineage from sourceFeature.getTaxon();
        // building the Taxon only when lineage is set would silently drop OS for entries
        // that carry only scientificName or taxId.
        if (m.getLineage() != null
                || m.getScientificName() != null
                || m.getCommonName() != null
                || m.getTaxon() != null) {
            Taxon taxon = sourceFt.getTaxon();
            if (taxon == null) {
                taxon = new TaxonFactory().createTaxon();
                sourceFt.setTaxon(taxon);
            }
            if (m.getLineage() != null) {
                taxon.setLineage(m.getLineage());
            }
            if (m.getScientificName() != null && taxon.getScientificName() == null) {
                taxon.setScientificName(m.getScientificName());
            }
            if (m.getCommonName() != null && taxon.getCommonName() == null) {
                taxon.setCommonName(m.getCommonName());
            }
            if (m.getTaxon() != null && taxon.getTaxId() == null) {
                try {
                    taxon.setTaxId(Long.parseLong(m.getTaxon()));
                } catch (NumberFormatException ignored) {
                    // taxon field is not numeric; skip
                }
            }
        }

        // Project: PR line
        if (m.getProject() != null) {
            entry.addProjectAccession(new Text(m.getProject()));
        }

        // References: RF lines. For WGS contigs the EMBL flatfile carries a per-contig
        // RP line (`RP   1-<length>`) on each submission reference; pass the contig
        // length through so mapReference can attach it.
        Long contigRpLength = (isWgsContig && contigLength > 0) ? contigLength : null;
        if (m.getReferences() != null) {
            for (ReferenceData refData : m.getReferences()) {
                mapReference(refData, entry, contigRpLength);
            }
        }

        // DR lines: database cross-references (ordered to match EMBL convention)
        if (m.getMd5() != null) {
            entry.addXRef(new XRef("MD5", m.getMd5()));
        }
        // For WGS contigs the reference flatfile points back to two SET accessions
        // before the run accession: the master entry (m.accession) and the WGS root
        // set (6-char letter prefix from m.wgsSet + 9 zeros, e.g. "CAXMYH01" → "CAXMYH000000000").
        if (isWgsContig) {
            if (m.getAccession() != null) {
                entry.addXRef(new XRef("ENA", m.getAccession(), "SET"));
            }
            String rootSet = wgsRootSetAccession(m.getWgsSet());
            if (rootSet != null) {
                entry.addXRef(new XRef("ENA", rootSet, "SET"));
            }
        }
        if (m.getRunAccessions() != null) {
            for (String run : m.getRunAccessions()) {
                entry.addXRef(new XRef("ENA", run));
            }
        }
        // Publications (e.g., BioSample). BioProject is skipped — handled via project → PR line.
        if (m.getPublications() != null) {
            for (CrossReference pub : m.getPublications()) {
                if (pub.getSource() != null && pub.getId() != null && !"BioProject".equalsIgnoreCase(pub.getSource())) {
                    entry.addXRef(new XRef(pub.getSource(), pub.getId()));
                }
            }
        }

        // DT lines: first public and last updated (dates + release numbers)
        if (m.getFirstPublic() != null) {
            Date date = parseDate(m.getFirstPublic());
            if (date != null) {
                entry.setFirstPublic(date);
            }
        }
        if (m.getFirstPublicRelease() != null) {
            entry.setFirstPublicRelease(m.getFirstPublicRelease());
        }
        if (m.getLastUpdated() != null) {
            Date date = parseDate(m.getLastUpdated());
            if (date != null) {
                entry.setLastUpdated(date);
            }
        }
        if (m.getLastUpdatedRelease() != null) {
            entry.setLastUpdatedRelease(m.getLastUpdatedRelease());
        }

        // Sequence length: ID line BP/SQ count.
        // For WGS contigs the master.json sequenceLength is the SET-level contig count
        // (e.g. 11435), not the per-contig length. The actual per-contig length comes
        // from the GFF3 ##sequence-region directive. The EMBL Sequence model only
        // surfaces a non-zero length when a byte buffer / contigs / AGP rows are
        // populated, none of which apply to a GFF3-only entry, so we route the per-
        // contig length through idLineSequenceLength + annotationOnlyCON the same way
        // we already do for non-SET non-WGS entries.
        // For non-WGS entries, the IDWriter only uses idLineSequenceLength for
        // SET/master/annotationOnlyCON entries, so for non-SET entries we set
        // annotationOnlyCON so the IDWriter picks up the length.
        if (isWgsContig) {
            if (contigLength > 0) {
                entry.setIdLineSequenceLength(contigLength);
                entry.setAnnotationOnlyCON(true);
            }
        } else if (m.getSequenceLength() != null && m.getSequenceLength() > 0) {
            entry.setIdLineSequenceLength(m.getSequenceLength());
            if (!"SET".equals(entry.getDataClass())) {
                entry.setAnnotationOnlyCON(true);
            }
        }

        // Sample: DR BioSample line. BioSample is a tripartite EBI/NCBI/DDBJ
        // collaboration, so SAMEA, SAMN, and SAMD are all valid BioSample accessions.
        // ERS accessions are ENA sample aliases, not BioSample identifiers, and are skipped.
        if (m.getSample() != null
                && (m.getSample().startsWith("SAMEA")
                        || m.getSample().startsWith("SAMN")
                        || m.getSample().startsWith("SAMD"))) {
            entry.addXRef(new XRef("BioSample", m.getSample()));
        }

        // Search fields: source feature qualifiers (geo_loc_name, collection_date, isolate, etc.)
        if (m.getSearchFields() != null) {
            mapSearchFields(m.getSearchFields(), sourceFt, isWgsContig);
        }
    }

    /**
     * Maps a ReferenceData to an EMBL Reference and adds it to the entry.
     * Creates a {@link Submission} when nested submitter details contain a valid
     * submitted date; otherwise falls back to {@link Unpublished}. When
     * {@code contigRpLength} is non-null the resulting Reference is given an RP
     * range of 1..contigRpLength.
     */
    private void mapReference(ReferenceData refData, Entry entry, Long contigRpLength) {
        Publication publication = createSubmissionPublication(refData);
        if (publication == null) {
            publication = referenceFactory.createUnpublished();
        }

        if (refData.getTitle() != null) {
            publication.setTitle(refData.getTitle());
        }

        if (refData.getConsortium() != null) {
            publication.setConsortium(refData.getConsortium());
        }

        SubmitterDetails submitterDetails = refData.getSubmitterDetails();
        List<AuthorData> authors = submitterDetails == null ? null : submitterDetails.getAuthors();
        if (authors != null) {
            for (AuthorData author : authors) {
                if (author == null) continue;
                String surname = author.getSurname() == null
                        ? ""
                        : author.getSurname().trim().replaceAll("\\s+", " ");
                String initials = toInitials(author.getFirstName()) + toInitials(author.getMiddleName());
                if (surname.isEmpty() && initials.isEmpty()) continue;
                publication.addAuthor(referenceFactory.createPerson(
                        surname.isEmpty() ? null : surname, initials.isEmpty() ? null : initials));
            }
        }

        Reference reference = referenceFactory.createReference(publication, refData.getReferenceNumber());

        if (refData.getReferenceComment() != null) {
            reference.setComment(refData.getReferenceComment());
        }

        if (contigRpLength != null && contigRpLength > 0) {
            Order<LocalRange> rpLocations = new Order<>();
            rpLocations.addLocation(locationFactory.createLocalRange(1L, contigRpLength));
            reference.setLocations(rpLocations);
        }

        entry.addReference(reference);
    }

    private Submission createSubmissionPublication(ReferenceData refData) {
        SubmitterDetails submitterDetails = refData.getSubmitterDetails();
        // Submission RL lines require a valid submitted date. When submitter details are
        // absent or the date is missing/unparseable, fall back to an unpublished reference
        // instead of emitting a malformed EMBL submission location.
        if (submitterDetails == null || submitterDetails.getSubmittedDate() == null) {
            return null;
        }
        Date parsedDate = parseDate(submitterDetails.getSubmittedDate());
        if (parsedDate == null) {
            LOGGER.warn(
                    "Unable to map submission reference location due to invalid submittedDate '{}'",
                    submitterDetails.getSubmittedDate());
            return null;
        }
        Submission submission = referenceFactory.createSubmission();
        submission.setDay(parsedDate);
        String address = buildSubmitterAddress(submitterDetails.getSubmissionAccount());
        if (address != null && !address.isBlank()) {
            submission.setSubmitterAddress(address);
        }
        return submission;
    }

    private static String buildSubmitterAddress(SubmissionAccount account) {
        if (account == null) {
            return null;
        }
        String institutionLine = Stream.of(account.getCenterName(), account.getLaboratoryName())
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .reduce((left, right) -> left + ", " + right)
                .orElse(null);

        String addressLine = Stream.of(account.getAddress(), account.getCountry())
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .reduce((left, right) -> left + ", " + right)
                .orElse(null);

        if (institutionLine == null) {
            return addressLine;
        }
        if (addressLine == null) {
            return institutionLine;
        }
        // Join with a comma so the EMBL RL writer wraps on commas across the
        // whole address (rather than treating institution and address as two
        // separate blocks). The writer collapses any embedded newline to a
        // single space anyway, so a `\n` separator would yield the institution
        // and address running together with no separator.
        return institutionLine + ", " + addressLine;
    }

    /**
     * Derives the WGS "root set" accession from the master entry's {@code wgsSet} field.
     *
     * <p>WGS set accessions come in two forms:
     * <ul>
     *   <li>4-letter prefix (legacy): {@code XXXX01} → {@code XXXX00000000} (12 chars)</li>
     *   <li>6-letter prefix (modern): {@code XXXXXX01} → {@code XXXXXX000000000} (15 chars)</li>
     * </ul>
     *
     * <p>Returns null when {@code wgsSet} is null or has fewer than 4 leading letters.
     */
    static String wgsRootSetAccession(String wgsSet) {
        if (wgsSet == null) {
            return null;
        }
        StringBuilder prefix = new StringBuilder();
        for (int i = 0; i < wgsSet.length() && prefix.length() < 6; i++) {
            char c = wgsSet.charAt(i);
            if (Character.isLetter(c)) {
                prefix.append(Character.toUpperCase(c));
            } else {
                break;
            }
        }
        if (prefix.length() < 4) {
            return null;
        }
        // Pad to 12 chars for 4-letter prefixes, 15 chars for 6-letter prefixes.
        int targetLength = prefix.length() <= 4 ? 12 : 15;
        while (prefix.length() < targetLength) {
            prefix.append('0');
        }
        return prefix.toString();
    }

    /**
     * Reduce a name component (firstName or middleName) to compact EMBL initials,
     * each letter followed by a period, no separators between letters. Accepts:
     * full name ("Eleanor" → "E."), single initial with or without period
     * ("E." / "E" → "E."), and multiple initials separated by spaces and/or
     * periods ("E. P." / "E P" / "E.P." → "E.P."). Returns "" for null/blank.
     */
    static String toInitials(String component) {
        if (component == null) return "";
        StringBuilder sb = new StringBuilder();
        for (String token : component.trim().split("\\s+")) {
            for (String sub : token.split("\\.")) {
                if (sub.isEmpty()) continue;
                sb.append(Character.toUpperCase(sub.charAt(0))).append('.');
            }
        }
        return sb.toString();
    }

    /** Target content width for `CC` lines (EMBL convention is 80 total = 5-col prefix + 75). */
    private static final int CC_LINE_WIDTH = 75;

    /**
     * Word-wrap a comment string to {@code maxWidth}-column lines, preserving any
     * existing line breaks. Words longer than {@code maxWidth} are kept on their own
     * line and not broken (CCWriter will force-break them if needed).
     */
    static String wrapCommentText(String text, int maxWidth) {
        StringBuilder out = new StringBuilder();
        boolean firstParagraph = true;
        for (String paragraph : text.split("\n", -1)) {
            if (!firstParagraph) {
                out.append('\n');
            }
            firstParagraph = false;
            if (paragraph.isEmpty()) {
                continue;
            }
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.split(" +")) {
                if (word.isEmpty()) {
                    continue;
                }
                if (line.length() == 0) {
                    line.append(word);
                } else if (line.length() + 1 + word.length() <= maxWidth) {
                    line.append(' ').append(word);
                } else {
                    out.append(line).append('\n');
                    line.setLength(0);
                    line.append(word);
                }
            }
            if (line.length() > 0) {
                out.append(line);
            }
        }
        return out.toString();
    }

    /**
     * Keys from searchFields that are already handled by other MasterMetadata fields
     * or are not valid source feature qualifiers. Skipping `chromosome`/`plasmid`/`segment`/
     * `linkage_group`/`organelle` avoids emitting these qualifiers twice when the same value
     * is present both at the top level (chromosomeName / chromosomeLocation) and inside
     * searchFields. `mol_type` and `organism` are likewise emitted from the dedicated
     * moleculeType/scientificName paths.
     */
    private static final Set<String> SEARCH_FIELDS_SKIP = Set.of(
            "organism",
            "mol_type",
            "topology",
            "tax_division",
            "md5_checksum",
            "country",
            "chromosome",
            "plasmid",
            "segment",
            "linkage_group",
            "organelle");

    /**
     * SearchFields keys that are SET-level metadata: they describe the assembly project
     * but not the individual WGS contig. Reference WGS flatfiles omit these from the
     * per-contig source feature, so we suppress them when emitting a WGS contig entry.
     */
    private static final Set<String> WGS_CONTIG_SEARCH_FIELDS_SKIP = Set.of("geo_loc_name", "collection_date");

    /**
     * Maps searchFields entries to source feature qualifiers.
     * Keys that are already handled elsewhere (organism, topology, etc.) are skipped.
     * For WGS contig entries, additional SET-level keys are suppressed so the source
     * feature matches the reference WGS contig flatfile.
     */
    private void mapSearchFields(Map<String, String> searchFields, SourceFeature sourceFt, boolean isWgsContig) {
        for (Map.Entry<String, String> field : searchFields.entrySet()) {
            String key = field.getKey();
            String value = field.getValue();
            if (value == null || value.isBlank() || SEARCH_FIELDS_SKIP.contains(key)) {
                continue;
            }
            if (isWgsContig && WGS_CONTIG_SEARCH_FIELDS_SKIP.contains(key)) {
                continue;
            }
            // Taxon db_xref is emitted from the dedicated taxon path; skip duplicates here
            // while preserving legitimate non-taxon db_xref values.
            if ("db_xref".equals(key) && value.startsWith("taxon:")) {
                continue;
            }
            sourceFt.addQualifier(key, value);
        }
        // /environmental_sample is a valueless qualifier implied by metagenome_source
        String metagenomeSource = searchFields.get("metagenome_source");
        if (metagenomeSource != null && !metagenomeSource.isBlank()) {
            sourceFt.addQualifier(qualifierFactory.createQualifier("environmental_sample"));
        }
    }

    private static final DateTimeFormatter EMBL_DATE_FORMATTER = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("dd-MMM-yyyy")
            .toFormatter(Locale.ENGLISH);

    /**
     * Parses a date string into a {@link Date} whose calendar day (in the JVM
     * default zone) matches the UTC calendar day of the input. The downstream
     * EMBL writer reformats the {@link Date} back through {@link ZoneId#systemDefault()},
     * so anchoring start-of-day in the same zone keeps the rendered DT/Submitted
     * line stable across runners.
     *
     * <p>Supports: ISO-8601 offset/zoned datetimes, ISO-8601 instants ("...Z"),
     * ISO-8601 local datetimes / dates (assumed UTC), and EMBL "dd-MMM-yyyy".
     */
    private Date parseDate(String dateStr) {
        LocalDate utcDate = parseToUtcLocalDate(dateStr);
        if (utcDate == null) {
            LOGGER.warn("Unable to parse date '{}'; skipping date mapping", dateStr);
            return null;
        }
        return Date.from(utcDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private LocalDate parseToUtcLocalDate(String dateStr) {
        // ISO-8601 with offset (e.g. "2025-04-03T01:00:00+12:00", "...Z")
        try {
            return OffsetDateTime.parse(dateStr)
                    .toInstant()
                    .atZone(ZoneOffset.UTC)
                    .toLocalDate();
        } catch (DateTimeParseException ignored) {
            // not an offset datetime; try other formats
        }

        // ISO-8601 local datetime (no offset) — interpret as UTC
        try {
            return DateTimeFormatter.ISO_LOCAL_DATE_TIME.parse(dateStr, LocalDate::from);
        } catch (DateTimeParseException ignored) {
            // not a local datetime; try other formats
        }

        // ISO-8601 local date
        try {
            return LocalDate.parse(dateStr);
        } catch (DateTimeParseException ignored) {
            // not an ISO local date
        }

        // EMBL "dd-MMM-yyyy"
        try {
            return LocalDate.parse(dateStr, EMBL_DATE_FORMATTER);
        } catch (DateTimeParseException ignored) {
            // not EMBL date format
        }
        return null;
    }

    /**
     * Maps topology string to {@link Sequence.Topology} enum.
     * Case-insensitive; logs a warning and skips on unrecognised values (FR-8).
     */
    private void mapTopology(String topology, Sequence sequence) {
        if ("linear".equalsIgnoreCase(topology)) {
            sequence.setTopology(Sequence.Topology.LINEAR);
        } else if ("circular".equalsIgnoreCase(topology)) {
            sequence.setTopology(Sequence.Topology.CIRCULAR);
        } else {
            LOGGER.warn("Unrecognised topology value '{}'; skipping topology mapping", topology);
        }
    }

    /**
     * Maps chromosome_type to the appropriate EMBL source feature qualifier.
     * Uses chromosome_name as the qualifier value when available.
     *
     * @param chromosomeType  the chromosome type string from the FASTA header
     * @param chromosomeName  the chromosome name (nullable) used as qualifier value
     * @param sourceFt        the source feature to add the qualifier to
     */
    private void mapChromosomeType(String chromosomeType, String chromosomeName, SourceFeature sourceFt) {
        String qualifierName;
        switch (chromosomeType.toLowerCase()) {
            case "chromosome":
                qualifierName = "chromosome";
                break;
            case "plasmid":
                qualifierName = "plasmid";
                break;
            case "segment":
                qualifierName = "segment";
                break;
            case "linkage group":
                qualifierName = "linkage_group";
                break;
            case "monopartite":
                // Monopartite is a valid type but produces no source qualifier
                return;
            default:
                LOGGER.warn("Unrecognised chromosome_type value '{}'; skipping qualifier mapping", chromosomeType);
                return;
        }

        if (chromosomeName != null) {
            sourceFt.addQualifier(qualifierName, chromosomeName);
        } else {
            sourceFt.addQualifier(qualifierName);
        }
    }

    /**
     * Maps chromosome_location to the EMBL /organelle qualifier on the source feature. "Nucleus"
     * (eukaryotic default) and "Cytoplasm" (prokaryotic/plasmid default) are allowed values that map
     * to no /organelle qualifier (handled case-insensitively). All other values are matched
     * case-insensitively against the INSDC {@code /organelle} controlled vocabulary
     * ({@link ControlledVocabularyUtils.ChromosomeLocation}); this tool now owns validation of that
     * vocabulary, so unrecognised values are rejected here rather than passed through to EMBL.
     *
     * @param chromosomeLocation  the chromosome location string from the FASTA header
     * @param sourceFt            the source feature to add the qualifier to
     */
    private void mapChromosomeLocation(String chromosomeLocation, SourceFeature sourceFt) {
        if (ControlledVocabularyUtils.ChromosomeLocation.NUCLEUS.getValue().equalsIgnoreCase(chromosomeLocation)
                || ControlledVocabularyUtils.ChromosomeLocation.CYTOPLASM
                        .getValue()
                        .equalsIgnoreCase(chromosomeLocation)) {
            // Nucleus/Cytoplasm denote the default location -- no organelle qualifier needed
            return;
        }

        Optional<String> canonical = ControlledVocabularyUtils.canonicalise(
                ControlledVocabularyUtils.ChromosomeLocation.class, chromosomeLocation);
        if (canonical.isEmpty()) {
            LOGGER.warn(
                    "Unrecognised chromosome_location value '{}'; skipping organelle qualifier mapping",
                    chromosomeLocation);
            return;
        }

        sourceFt.addQualifier("organelle", canonical.get().toLowerCase());
    }
}
