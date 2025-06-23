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
package uk.ac.ebi.embl.converter.gff3toff;

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
import uk.ac.ebi.embl.api.entry.sequence.SequenceFactory;
import uk.ac.ebi.embl.converter.gff3.GFF3Annotation;
import uk.ac.ebi.embl.converter.gff3.GFF3Directives;
import uk.ac.ebi.embl.converter.gff3.GFF3Feature;
import uk.ac.ebi.embl.converter.utils.ConversionEntry;
import uk.ac.ebi.embl.converter.utils.ConversionUtils;

public class GFF3Mapper {

    private static final Logger LOG = LoggerFactory.getLogger(GFF3Mapper.class);

    private final Map<String, String> qmap = ConversionUtils.getGFF32FFQualifierMap();
    private final EntryFactory entryFactory = new EntryFactory();
    private final FeatureFactory featureFactory = new FeatureFactory();
    private final QualifierFactory qualifierFactory = new QualifierFactory();
    private final LocationFactory locationFactory = new LocationFactory();
    private final SequenceFactory sequenceFactory = new SequenceFactory();

    Map<String, GFF3Feature> parentFeatures;
    Map<String, Feature> ffFeatures;
    Entry entry;

    public GFF3Mapper() {
        parentFeatures = new HashMap<>();
        ffFeatures = new HashMap<>();
        entry = null;
    }

    public Entry mapGFF3ToEntry(GFF3Annotation gff3Annotation) {

        parentFeatures.clear();
        ffFeatures.clear();
        entry = entryFactory.createEntry();
        entry.setSequence(sequenceFactory.createSequence());

        if (!gff3Annotation.getDirectives().getDirectives().isEmpty()) {
            SourceFeature sourceFeature = this.featureFactory.createSourceFeature();
            for (GFF3Directives.GFF3Directive directive :
                    gff3Annotation.getDirectives().getDirectives()) {
                if (directive.getClass() == GFF3Directives.GFF3SequenceRegion.class) {
                    GFF3Directives.GFF3SequenceRegion reg = (GFF3Directives.GFF3SequenceRegion) directive;
                    String accession = reg.accession();
                    LOG.info("Converting Gff3 entry: {}", accession);
                    String accessionId = accession.substring(0, accession.lastIndexOf('.'));
                    entry.setPrimaryAccession(accessionId);
                    Location location = this.locationFactory.createLocalRange(reg.start(), reg.end());
                    Join<Location> compoundJoin = new Join<>();
                    compoundJoin.addLocation(location);
                    sourceFeature.setLocations(compoundJoin);
                }
            }
            entry.addFeature(sourceFeature);
        }

        for (GFF3Feature gff3Feature : gff3Annotation.getFeatures()) {
            if (gff3Feature.getId().isPresent()) {
                parentFeatures.put(gff3Feature.getId().get(), gff3Feature);
            }

            mapGFF3Feature(gff3Feature);
        }

        return entry;
    }

    private void mapGFF3Feature(GFF3Feature gff3Feature) {

        Map<String, Object> attributes = gff3Feature.getAttributes();
        String featureHashId = (String) attributes.getOrDefault("ID", String.valueOf(gff3Feature.hashCode()));

        Location location = mapGFF3Location(gff3Feature);
        Feature ffFeature = ffFeatures.get(featureHashId);
        if (ffFeature != null) {
            CompoundLocation<Location> parentFeatureLocation = ffFeature.getLocations();
            // If the compoundlocation isComplement but the new location we are adding is not complement
            // we need to restructure the locations that it contains
            // QUESTION: Does this ever happen? AFAIK the syntax would allow this, but it is nonsensical.
            if (parentFeatureLocation.isComplement() && !location.isComplement()) {
                parentFeatureLocation.getLocations().forEach((l) -> location.setComplement(true));
                parentFeatureLocation.setComplement(false);
            } else if (parentFeatureLocation.isComplement() && location.isComplement()) {
                location.setComplement(false);
            }
            parentFeatureLocation.addLocation(location);
        } else {
            String gff3FeatureName = gff3Feature.getName();
            // Get featureName from Anthology map if it exists.
            ConversionEntry conversionEntry =
                    ConversionUtils.getGFF32FFFeatureMap().get(gff3FeatureName);
            String featureName = (conversionEntry != null) ? conversionEntry.getFeature() : gff3FeatureName;

            ffFeature = featureFactory.createFeature(featureName);
            CompoundLocation<Location> locations = new Join();
            if (location.isComplement()) {
                locations.setComplement(true);
                location.setComplement(false);
            }
            locations.addLocation(location);
            ffFeature.setLocations(locations);
            ffFeature.addQualifiers(mapGFF3Attributes(attributes));
            ffFeatures.put(featureHashId, ffFeature);
            entry.addFeature(ffFeature);
        }
        if (ffFeature.getQualifiers("gene").isEmpty()) {
            String gene = getGeneForFeature(gff3Feature);
            if (gene != null) {
                ffFeature.addQualifier("gene", gene);
            }
        }
    }

    private String getGeneForFeature(GFF3Feature gff3Feature) {
        if (gff3Feature.getAttributes().containsKey("gene")) {
            return (String) gff3Feature.getAttributes().get("gene");
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
        Object partialsRaw = gff3Feature.getAttributes().getOrDefault("partial", new ArrayList<>());
        List<String> partials =
                partialsRaw instanceof String ? List.of((String) partialsRaw) : (List<String>) partialsRaw;

        boolean isComplement = gff3Feature.getStrand().equals("-");
        Location location = this.locationFactory.createLocalRange(
                start, end, isComplement);

        // Set location partiality
        if (!partials.isEmpty()) {
            boolean fivePrimePartial = partials.contains("start");
            boolean threePrimePartial = partials.contains("end");

            location.setFivePrimePartial(isComplement ? !fivePrimePartial : fivePrimePartial);
            location.setThreePrimePartial(isComplement ? !threePrimePartial : threePrimePartial);
        }

        return location;
    }

    private Collection<Qualifier> mapGFF3Attributes(Map<String, Object> attributes) {
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
