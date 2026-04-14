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
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.embl.api.entry.Entry;
import uk.ac.ebi.embl.api.entry.EntryFactory;
import uk.ac.ebi.embl.api.entry.Text;
import uk.ac.ebi.embl.api.entry.feature.Feature;
import uk.ac.ebi.embl.api.entry.feature.FeatureFactory;
import uk.ac.ebi.embl.api.entry.feature.SourceFeature;
import uk.ac.ebi.embl.api.entry.location.*;
import uk.ac.ebi.embl.api.entry.qualifier.Qualifier;
import uk.ac.ebi.embl.api.entry.qualifier.QualifierFactory;
import uk.ac.ebi.embl.api.entry.sequence.Sequence;
import uk.ac.ebi.embl.api.entry.sequence.SequenceFactory;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.gff3.TranslationKey;
import uk.ac.ebi.embl.gff3tools.gff3.directives.*;
import uk.ac.ebi.embl.gff3tools.gff3.reader.GFF3FileReader;
import uk.ac.ebi.embl.gff3tools.gff3.reader.OffsetRange;
import uk.ac.ebi.embl.gff3tools.sequence.SequenceLookup;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.FastaHeaderProvider;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.utils.FastaHeader;
import uk.ac.ebi.embl.gff3tools.utils.ConversionEntry;
import uk.ac.ebi.embl.gff3tools.utils.ConversionUtils;
import uk.ac.ebi.embl.gff3tools.utils.OntologyTerm;

public class GFF3Mapper {

    private final Map<String, String> qmap = ConversionUtils.getGFF32FFQualifierMap();
    private final EntryFactory entryFactory = new EntryFactory();
    private final FeatureFactory featureFactory = new FeatureFactory();
    private final QualifierFactory qualifierFactory = new QualifierFactory();
    private final LocationFactory locationFactory = new LocationFactory();
    private final SequenceFactory sequenceFactory = new SequenceFactory();
    private static final Logger LOGGER = LoggerFactory.getLogger(GFF3Mapper.class);

    Map<String, GFF3Feature> parentFeatures;
    // Used to keep track of features that will be merged using a location join
    Map<String, Feature> joinableFeatureMap;

    Entry entry;
    GFF3FileReader gff3FileReader;
    private final FastaHeaderProvider headerProvider;
    private final SequenceLookup sequenceLookup;

    public GFF3Mapper(GFF3FileReader gff3FileReader) {
        this(gff3FileReader, null, null);
    }

    public GFF3Mapper(GFF3FileReader gff3FileReader, FastaHeaderProvider headerProvider) {
        this(gff3FileReader, headerProvider, null);
    }

    public GFF3Mapper(
            GFF3FileReader gff3FileReader, FastaHeaderProvider headerProvider, SequenceLookup sequenceLookup) {
        parentFeatures = new HashMap<>();
        joinableFeatureMap = new HashMap<>();
        entry = null;
        this.gff3FileReader = gff3FileReader;
        this.headerProvider = headerProvider;
        this.sequenceLookup = sequenceLookup;
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

        applyFastaHeader(sequenceRegion, entry, sequence, sourceFeature);

        for (GFF3Feature gff3Feature : gff3Annotation.getFeatures()) {
            if (gff3Feature.getId().isPresent()) {
                parentFeatures.put(gff3Feature.getId().get(), gff3Feature);
            }

            mapGFF3Feature(gff3Feature, gff3FileReader.getTranslationOffsetMap());
        }

        // Load sequence data as late as possible to minimise time it spends in memory.
        // The Entry is written and becomes GC-eligible immediately after this method returns.
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
    private Map<String, List<String>> getAttributesMap(GFF3Feature gff3Feature) {
        Map<String, List<String>> attributesMap = new HashMap<>();
        for (String key : gff3Feature.getAttributeKeys()) {
            gff3Feature.getAttributeList(key).ifPresent(values -> attributesMap.put(key, values));
        }
        return attributesMap;
    }

    /**
     * Populates the nucleotide sequence data on the Sequence object from the SequenceLookup.
     * Gracefully skips if no lookup is available or no sequence region is defined.
     */
    private void applySequenceData(GFF3SequenceRegion sequenceRegion, Sequence sequence) {
        if (sequenceLookup == null || sequenceRegion == null) {
            return;
        }
        try {
            String nucleotides = sequenceLookup.getSequenceSlice(
                    sequenceRegion.accessionId(), sequenceRegion.start(), sequenceRegion.end());
            // Convert to lowercase bytes in a single pass to avoid an intermediate
            // String allocation from toLowerCase() (saves ~2N bytes of peak memory).
            byte[] seqBytes = new byte[nucleotides.length()];
            for (int i = 0; i < nucleotides.length(); i++) {
                seqBytes[i] = (byte) Character.toLowerCase(nucleotides.charAt(i));
            }
            sequence.setSequence(ByteBuffer.wrap(seqBytes));
            sequence.setLength((long) seqBytes.length);
        } catch (Exception e) {
            LOGGER.warn("Failed to retrieve sequence for '{}': {}", sequenceRegion.accessionId(), e.getMessage());
        }
    }

    /**
     * Applies FASTA header metadata to the EMBL entry, sequence, and source feature.
     * Gracefully skips if no header provider is available or no header is found for the seqId.
     */
    private void applyFastaHeader(
            GFF3SequenceRegion sequenceRegion, Entry entry, Sequence sequence, SourceFeature sourceFt) {
        if (headerProvider == null) {
            return;
        }
        if (sequenceRegion == null) {
            return;
        }

        String seqId = sequenceRegion.accessionId();
        Optional<FastaHeader> opt = headerProvider.getHeader(seqId);
        if (opt.isEmpty()) {
            LOGGER.debug("No FASTA header found for seqId '{}'; skipping header mapping", seqId);
            return;
        }

        FastaHeader h = opt.get();

        // FR-1: Description mapping (DE line)
        if (h.getDescription() != null) {
            entry.setDescription(new Text(h.getDescription()));
        }

        // FR-2: Molecule type mapping (ID line field 4 + source qualifier)
        if (h.getMoleculeType() != null) {
            sequence.setMoleculeType(h.getMoleculeType());
            sourceFt.addQualifier("mol_type", h.getMoleculeType());
        }

        // FR-3: Topology mapping (ID line field 3)
        if (h.getTopology() != null) {
            mapTopology(h.getTopology(), sequence);
        }

        // FR-4 + FR-5: Chromosome type and name mapping
        if (h.getChromosomeType() != null) {
            mapChromosomeType(h.getChromosomeType(), h.getChromosomeName(), sourceFt);
        } else if (h.getChromosomeName() != null) {
            // FR-4: Standalone chromosome name (no type specified)
            sourceFt.addQualifier("chromosome", h.getChromosomeName());
        }

        // FR-5: Chromosome location mapping
        if (h.getChromosomeLocation() != null) {
            mapChromosomeLocation(h.getChromosomeLocation(), sourceFt);
        }
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
            // Nuclear is the default — no organelle qualifier needed
            return;
        }

        sourceFt.addQualifier("organelle", normalised);
    }
}
