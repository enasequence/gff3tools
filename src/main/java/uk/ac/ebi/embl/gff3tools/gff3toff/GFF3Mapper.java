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

import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.embl.api.entry.Entry;
import uk.ac.ebi.embl.api.entry.EntryFactory;
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
import uk.ac.ebi.embl.gff3tools.gff3.directives.*;
import uk.ac.ebi.embl.gff3tools.gff3.reader.GFF3FileReader;
import uk.ac.ebi.embl.gff3tools.gff3.reader.OffsetRange;
import uk.ac.ebi.embl.gff3tools.gff3.writer.TranslationWriter;
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

    public GFF3Mapper(GFF3FileReader gff3FileReader) {
        parentFeatures = new HashMap<>();
        joinableFeatureMap = new HashMap<>();
        entry = null;
        this.gff3FileReader = gff3FileReader;
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

        String existingID =
                gff3Feature.getAttributeByName("ID").map(List::getFirst).get();
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
            if (gff3Feature.getAttributeByName("Is_circular").isPresent()
                    && OntologyTerm.REGION.ID.equalsIgnoreCase(gff3Id)) {
                // Do not convert "region" features. These are added when doing EMBL->GFF3 mapping to
                // represent circular topologies. The topology in the EMBL mapping will be provided
                // by the fasta headers.
                return;
            }
            LOGGER.debug("Found GFF3ID: \"%s\" for feature \"%s\"".formatted(gff3Id, gff3FeatureName));
            ConversionEntry conversionEntry = ConversionUtils.getINSDCFeatureForSOTerm(gff3Id);
            if (conversionEntry != null) {
                ffFeature = featureFactory.createFeature(conversionEntry.getFeature());
                CompoundLocation<Location> locations = new Join();
                if (location.isComplement()) {
                    locations.setComplement(true);
                    location.setComplement(false);
                }
                locations.addLocation(location);
                ffFeature.setLocations(locations);

                ffFeature.addQualifiers(mapGFF3Attributes(gff3Feature.getAttributes()));

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
        String translationKey = TranslationWriter.getTranslationKey(gff3Feature.accession(), featureId);
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
        if (gff3Feature.getAttributes().containsKey("gene")) {
            return gff3Feature.getAttributeByName("gene").map(List::getFirst).get();
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
        List<String> partials = gff3Feature.getAttributes().getOrDefault("partial", new ArrayList<>());

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

    private Collection<Qualifier> mapGFF3Attributes(Map<String, List<String>> attributes) {
        Collection<Qualifier> qualifierList = new ArrayList();

        for (Object o : attributes.entrySet()) {
            Map.Entry<String, String> attributePairs = (Map.Entry) o;
            String attributeKey = attributePairs.getKey();
            if (qmap.containsKey(attributeKey)) {
                attributeKey = qmap.get(attributeKey);
            }
            if (!attributeKey.isBlank()) {
                Object value = attributePairs.getValue();
                if (value instanceof List) {
                    List<String> values = (List<String>) value;
                    for (String val : values) {
                        qualifierList.add(createQualifier(attributeKey, val));
                    }
                } else {
                    qualifierList.add(createQualifier(attributeKey, value.toString()));
                }
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
}
