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
import uk.ac.ebi.embl.api.gff3.GFF3Record;
import uk.ac.ebi.embl.api.gff3.GFF3RecordSet;
import uk.ac.ebi.embl.converter.utils.ConversionEntry;
import uk.ac.ebi.embl.converter.utils.ConversionUtils;

public class FFEntryMapper {

  private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(FFEntryMapper.class);

  private static final FeatureFactory featureFactory = new FeatureFactory();
  private static final QualifierFactory qualifierFactory = new QualifierFactory();
  private static final LocationFactory locationFactory = new LocationFactory();
  private static final List<Entry> entryList = new ArrayList<>();

  public static List<Entry> mapGFF3RecordToEntry(GFF3RecordSet records) {

    Map<String, ConversionEntry> featureMap = ConversionUtils.getGFF32FFFeatureMap();
    Map<String, String> qualifierMap = ConversionUtils.getGFF32FFQualifierMap();
    for (GFF3Record record : records.getRecords()) {

      String sequenceId = record.getSequenceID();
      String source = record.getSource();
      String featureName = record.getType();
      int start = record.getStart();
      int end = record.getEnd();
      double score = record.getScore();
      int strand = record.getStrand();
      int phase = record.getPhase();
      Map<String, String> attributes = record.getAttributes();

      // Qualifier Mapping
      List<Qualifier> qualifierList = getQualifiers(attributes, qualifierMap);

      if (qualifierList.size() == 0) {
        throw new RuntimeException("No qualifier found for sequence id " + sequenceId);
      }

      // location
      boolean compliment = strand < 0;
      Join location = getLocation(start, end, compliment);

      // Feature Mapping
      if (featureMap.containsKey(featureName)) {
        Feature feature = featureFactory.createFeature(featureMap.get(featureName).getFeature());
        feature.setLocations(location);
        feature.addQualifiers(qualifierList);

        String primaryAcc = sequenceId.split("\\.")[0];

        Entry entry =
            entryList.stream()
                .filter(e -> e.getPrimaryAccession().equals(primaryAcc))
                .findFirst()
                .orElseGet(
                    () -> {
                      Entry newEntry = new Entry();
                      newEntry.setPrimaryAccession(primaryAcc);
                      entryList.add(newEntry);
                      return newEntry;
                    });
        entry.addFeature(feature);
      } else {
        LOG.warn("Invalid feature: " + featureName);
      }
    }

    return entryList;
  }

  private static Join getLocation(long start, long end, boolean compliment) {
    Location location = locationFactory.createLocalRange((long) start, (long) end, compliment);
    Join<Location> compoundLocation = new Join<Location>();
    compoundLocation.addLocation(location);

    return compoundLocation;
  }

  private static List<Qualifier> getQualifiers(
      Map<String, String> attributes, Map<String, String> qualifierMap) {
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
