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

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.*;
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
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.gff3.TranslationKey;
import uk.ac.ebi.embl.gff3tools.gff3.directives.*;
import uk.ac.ebi.embl.gff3tools.gff3.reader.GFF3FileReader;
import uk.ac.ebi.embl.gff3tools.gff3.reader.OffsetRange;
import uk.ac.ebi.embl.gff3tools.metadata.AnnotationMetadata;
import uk.ac.ebi.embl.gff3tools.metadata.AnnotationMetadataProvider;
import uk.ac.ebi.embl.gff3tools.metadata.CrossReference;
import uk.ac.ebi.embl.gff3tools.metadata.ReferenceData;
import uk.ac.ebi.embl.gff3tools.utils.ConversionEntry;
import uk.ac.ebi.embl.gff3tools.utils.ConversionUtils;
import uk.ac.ebi.embl.gff3tools.utils.OntologyTerm;
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
    private final AnnotationMetadataProvider metadataProvider;

    public GFF3Mapper(GFF3FileReader gff3FileReader) {
        this(gff3FileReader, (AnnotationMetadataProvider) null);
    }

    public GFF3Mapper(GFF3FileReader gff3FileReader, AnnotationMetadataProvider metadataProvider) {
        parentFeatures = new HashMap<>();
        joinableFeatureMap = new HashMap<>();
        entry = null;
        this.gff3FileReader = gff3FileReader;
        this.metadataProvider = metadataProvider;
    }

    public Entry mapGFF3ToEntry(GFF3Annotation gff3Annotation) throws ValidationException {

        parentFeatures.clear();
        joinableFeatureMap.clear();
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

        applyAnnotationMetadata(sequenceRegion, entry, sequence, sourceFeature);

        for (GFF3Feature gff3Feature : gff3Annotation.getFeatures()) {
            if (gff3Feature.getId().isPresent()) {
                parentFeatures.put(gff3Feature.getId().get(), gff3Feature);
            }

            mapGFF3Feature(gff3Feature, gff3FileReader.getTranslationOffsetMap());
        }

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
    private Map<String, List<String>> getAttributesMap(GFF3Feature gff3Feature) {
        Map<String, List<String>> attributesMap = new HashMap<>();
        for (String key : gff3Feature.getAttributeKeys()) {
            gff3Feature.getAttributeList(key).ifPresent(values -> attributesMap.put(key, values));
        }
        return attributesMap;
    }

    /**
     * Applies AnnotationMetadata to the EMBL entry, sequence, and source feature.
     * Gracefully skips if no metadata provider is available or no metadata is found for the seqId.
     */
    private void applyAnnotationMetadata(
            GFF3SequenceRegion sequenceRegion, Entry entry, Sequence sequence, SourceFeature sourceFt) {
        if (metadataProvider == null) {
            return;
        }
        if (sequenceRegion == null) {
            return;
        }

        String seqId = sequenceRegion.accessionId();
        Optional<AnnotationMetadata> opt = metadataProvider.getMetadata(seqId);
        if (opt.isEmpty()) {
            LOGGER.debug("No annotation metadata found for seqId '{}'; skipping metadata mapping", seqId);
            return;
        }

        AnnotationMetadata m = opt.get();

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

        // Data class: ID line data class field
        if (m.getDataClass() != null) {
            entry.setDataClass(m.getDataClass());
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
            if (sequenceRegion != null && sequenceRegion.accessionVersion().isEmpty()) {
                sequence.setVersion(m.getVersion());
            }
        }

        // Keywords: KW line
        if (m.getKeywords() != null) {
            for (String kw : m.getKeywords()) {
                entry.addKeyword(new Text(kw));
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

        // Common name: source feature /note with prefix
        if (m.getCommonName() != null) {
            sourceFt.addQualifier("note", "common name: " + m.getCommonName());
        }

        // Lineage: OC line (taxonomy classification).
        // The EMBL OCWriter reads lineage from sourceFeature.getTaxon().getLineage(),
        // so we ensure a Taxon object exists on the SourceFeature and populate it.
        if (m.getLineage() != null) {
            Taxon taxon = sourceFt.getTaxon();
            if (taxon == null) {
                taxon = new TaxonFactory().createTaxon();
                sourceFt.setTaxon(taxon);
            }
            taxon.setLineage(m.getLineage());
            // Also populate taxon fields from metadata if they were set above as qualifiers
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

        // References: RF lines
        if (m.getReferences() != null) {
            for (ReferenceData refData : m.getReferences()) {
                mapReference(refData, entry);
            }
        }

        // DR lines: database cross-references (ordered to match EMBL convention)
        if (m.getMd5() != null) {
            entry.addXRef(new XRef("MD5", m.getMd5()));
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
        // The IDWriter only uses idLineSequenceLength for SET/master/annotationOnlyCON
        // entries. For non-SET entries, we set annotationOnlyCON so the IDWriter picks
        // up the length. SET entries already use idLineSequenceLength natively.
        if (m.getSequenceLength() != null && m.getSequenceLength() > 0) {
            entry.setIdLineSequenceLength(m.getSequenceLength());
            if (!"SET".equals(entry.getDataClass())) {
                entry.setAnnotationOnlyCON(true);
            }
        }

        // Sample: DR BioSample line (only for SAMEA-prefixed accessions;
        // ERS accessions are ENA sample aliases, not BioSample identifiers)
        if (m.getSample() != null && m.getSample().startsWith("SAMEA")) {
            entry.addXRef(new XRef("BioSample", m.getSample()));
        }

        // Search fields: source feature qualifiers (geo_loc_name, collection_date, isolate, etc.)
        if (m.getSearchFields() != null) {
            mapSearchFields(m.getSearchFields(), sourceFt);
        }
    }

    private static final java.util.regex.Pattern SUBMISSION_PATTERN = java.util.regex.Pattern.compile(
            "Submitted \\(([^)]+)\\) to the INSDC\\.\\n?(.*)$", java.util.regex.Pattern.DOTALL);

    /**
     * Maps a ReferenceData to an EMBL Reference and adds it to the entry.
     * Detects submission-type references from the location field pattern
     * {@code "Submitted (DD-MMM-YYYY) to the INSDC.\nADDRESS"} and creates a
     * {@link Submission}; otherwise falls back to {@link Unpublished}.
     */
    private void mapReference(ReferenceData refData, Entry entry) {
        Publication publication;

        if (refData.getLocation() != null) {
            var matcher = SUBMISSION_PATTERN.matcher(refData.getLocation());
            if (matcher.matches()) {
                publication =
                        createSubmission(matcher.group(1), matcher.group(2).trim());
            } else {
                publication = referenceFactory.createUnpublished();
            }
        } else {
            publication = referenceFactory.createUnpublished();
        }

        if (refData.getTitle() != null) {
            publication.setTitle(refData.getTitle());
        }

        if (refData.getConsortium() != null) {
            publication.setConsortium(refData.getConsortium());
        }

        if (refData.getAuthors() != null) {
            String[] authorNames = refData.getAuthors().split(",\\s*");
            for (String authorName : authorNames) {
                // Collapse runs of whitespace inside each name — master.json
                // sometimes carries doubled spaces between surname and initials
                // (e.g. "Goudenege  D."), which would otherwise propagate verbatim
                // into the EMBL `RA` line.
                String normalized = authorName.trim().replaceAll("\\s+", " ");
                if (!normalized.isEmpty()) {
                    publication.addAuthor(referenceFactory.createPerson(normalized));
                }
            }
        }

        Reference reference = referenceFactory.createReference(publication, refData.getReferenceNumber());

        if (refData.getReferenceComment() != null) {
            reference.setComment(refData.getReferenceComment());
        }

        entry.addReference(reference);
    }

    /**
     * Creates a Submission publication from the parsed date string and submitter address.
     */
    private Submission createSubmission(String dateStr, String address) {
        Submission sub = referenceFactory.createSubmission();
        Date day = parseDate(dateStr);
        if (day != null) {
            sub.setDay(day);
        }
        if (!address.isBlank()) {
            sub.setSubmitterAddress(address);
        }
        return sub;
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
     * Keys from searchFields that are already handled by other AnnotationMetadata fields
     * or are not valid source feature qualifiers. Skipping `chromosome`/`plasmid`/`segment`/
     * `linkage_group`/`organelle` avoids emitting these qualifiers twice when the same value
     * is present both at the top level (chromosomeName / chromosomeLocation) and inside
     * searchFields.
     */
    private static final Set<String> SEARCH_FIELDS_SKIP = Set.of(
            "organism",
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
     * Maps searchFields entries to source feature qualifiers.
     * Keys that are already handled elsewhere (organism, topology, etc.) are skipped.
     */
    private void mapSearchFields(Map<String, String> searchFields, SourceFeature sourceFt) {
        for (Map.Entry<String, String> field : searchFields.entrySet()) {
            String key = field.getKey();
            String value = field.getValue();
            if (value == null || value.isBlank() || SEARCH_FIELDS_SKIP.contains(key)) {
                continue;
            }
            sourceFt.addQualifier(key, value);
        }
        // /environmental_sample is a valueless qualifier implied by metagenome_source
        if (searchFields.containsKey("metagenome_source")) {
            sourceFt.addQualifier(qualifierFactory.createQualifier("environmental_sample"));
        }
    }

    private static final DateTimeFormatter[] DATE_FORMATTERS = {
        DateTimeFormatter.ISO_DATE_TIME,
        DateTimeFormatter.ISO_DATE,
        new DateTimeFormatterBuilder()
                .parseCaseInsensitive()
                .appendPattern("dd-MMM-yyyy")
                .toFormatter(Locale.ENGLISH),
    };

    /**
     * Parses a date string into a {@link Date}. Supports ISO-8601 datetime (with timezone)
     * and simple date formats. Uses {@code java.time} to correctly handle timezone offsets
     * including the UTC 'Z' suffix.
     */
    private Date parseDate(String dateStr) {
        // Try ISO-8601 instant first (handles "2024-01-15T00:00:00Z" and offset variants)
        try {
            return Date.from(Instant.parse(dateStr));
        } catch (DateTimeParseException ignored) {
            // not a strict instant; try other formats
        }

        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                var accessor = formatter.parse(dateStr);
                try {
                    return Date.from(Instant.from(accessor));
                } catch (DateTimeException ignored) {
                    // no timezone info; treat as a local date at UTC midnight
                }
                LocalDate localDate = LocalDate.from(accessor);
                return Date.from(localDate.atStartOfDay(ZoneOffset.UTC).toInstant());
            } catch (DateTimeParseException ignored) {
                // try next format
            }
        }
        LOGGER.warn("Unable to parse date '{}'; skipping date mapping", dateStr);
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
     * Maps chromosome_location to the EMBL /organelle qualifier on the source feature.
     * "Nuclear" is the default and produces no qualifier. All other values are passed through
     * as-is (lowercased); downstream EMBL validation checks qualifier values.
     *
     * @param chromosomeLocation  the chromosome location string from the FASTA header
     * @param sourceFt            the source feature to add the qualifier to
     */
    private void mapChromosomeLocation(String chromosomeLocation, SourceFeature sourceFt) {
        String normalised = chromosomeLocation.toLowerCase();

        if ("nuclear".equals(normalised)) {
            // Nuclear is the default -- no organelle qualifier needed
            return;
        }

        sourceFt.addQualifier("organelle", normalised);
    }
}
