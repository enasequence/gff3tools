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
package uk.ac.ebi.embl.converter.ff;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.embl.api.entry.Entry;
import uk.ac.ebi.embl.api.entry.feature.Feature;
import uk.ac.ebi.embl.api.entry.feature.FeatureFactory;
import uk.ac.ebi.embl.api.entry.location.Join;
import uk.ac.ebi.embl.api.entry.location.Location;
import uk.ac.ebi.embl.api.entry.location.LocationFactory;
import uk.ac.ebi.embl.api.entry.qualifier.Qualifier;
import uk.ac.ebi.embl.api.entry.qualifier.QualifierFactory;
import uk.ac.ebi.embl.converter.gff3.GFF3Feature;
import uk.ac.ebi.embl.converter.gff3.GFF3Reader;
import uk.ac.ebi.embl.converter.utils.ConversionEntry;
import uk.ac.ebi.embl.converter.utils.ConversionUtils;

public class FFEntryMapper {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(FFEntryMapper.class);

    private static final FeatureFactory featureFactory = new FeatureFactory();
    private static final QualifierFactory qualifierFactory = new QualifierFactory();
    private static final LocationFactory locationFactory = new LocationFactory();

    public static Entry mapGFF3RecordToEntry(GFF3Feature gff3Feature) {

        Map<String, ConversionEntry> featureMap = ConversionUtils.getGFF32FFFeatureMap();
        Map<String, String> qualifierMap = ConversionUtils.getGFF32FFQualifierMap();

        String sequenceId = gff3Feature.getAccession();
        String source = gff3Feature.getSource();
        String featureName = gff3Feature.getName();
        long start = gff3Feature.getStart();
        long end = gff3Feature.getEnd();
        String score = gff3Feature.getScore();
        String strand = gff3Feature.getStrand();
        String phase = gff3Feature.getPhase();
        Map<String, String> attributes = gff3Feature.getAttributes();

        Entry entry = new Entry();

        String primaryAcc = sequenceId.split("\\.")[0];
        entry.setPrimaryAccession(primaryAcc);

        // Qualifier Mapping
        List<Qualifier> qualifierList = getQualifiers(attributes, qualifierMap);

        if (qualifierList.size() == 0) {
            throw new RuntimeException("No qualifier found for sequence id " + sequenceId);
        }

        // location
        boolean compliment = strand.equals("-");
        Join location = getLocation(start, end, compliment);

        // Feature Mapping
        if (featureMap.containsKey(featureName)) {
            Feature feature =
                    featureFactory.createFeature(featureMap.get(featureName).getFeature());
            feature.setLocations(location);
            feature.addQualifiers(qualifierList);

            entry.addFeature(feature);
        } else {
            LOG.warn("Invalid feature: " + featureName);
        }

        return entry;
    }

    public static List<Entry> mapGFF3RecordToEntry(GFF3Reader gff3Reader) throws IOException {

        ArrayList<Entry> entries = new ArrayList<>();
        GFF3Feature feature = null;
        while ((feature = gff3Reader.read()) != null) {
            entries.add(mapGFF3RecordToEntry(feature));
        }
        return entries;
    }

    private static Join getLocation(long start, long end, boolean compliment) {
        Location location = locationFactory.createLocalRange((long) start, (long) end, compliment);
        Join<Location> compoundLocation = new Join<Location>();
        compoundLocation.addLocation(location);

        return compoundLocation;
    }

    private static List<Qualifier> getQualifiers(Map<String, String> attributes, Map<String, String> qualifierMap) {
        List<Qualifier> qualifierList = new ArrayList<>();
        for (Map.Entry<String, String> attributePair : attributes.entrySet()) {
            String attributeKey = attributePair.getKey();
            String attributeValue = attributePair.getValue();

            String qualifierName = qualifierMap.getOrDefault(attributeKey, attributeKey);
            Qualifier qualifier = qualifierFactory.createQualifier(qualifierName, attributeValue);

            qualifierList.add(qualifier);
        }
        return qualifierList;
    }
}
