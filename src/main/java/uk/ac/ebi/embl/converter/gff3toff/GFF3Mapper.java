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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import uk.ac.ebi.embl.api.entry.Entry;
import uk.ac.ebi.embl.api.entry.EntryFactory;
import uk.ac.ebi.embl.api.entry.feature.Feature;
import uk.ac.ebi.embl.api.entry.feature.FeatureFactory;
import uk.ac.ebi.embl.api.entry.location.Location;
import uk.ac.ebi.embl.api.entry.location.LocationFactory;
import uk.ac.ebi.embl.api.entry.location.Order;
import uk.ac.ebi.embl.api.entry.qualifier.Qualifier;
import uk.ac.ebi.embl.api.entry.qualifier.QualifierFactory;
import uk.ac.ebi.embl.converter.gff3.GFF3Feature;
import uk.ac.ebi.embl.converter.gff3.GFF3Reader;

public class GFF3Mapper {
    private final EntryFactory entryFactory = new EntryFactory();
    private final FeatureFactory featureFactory = new FeatureFactory();
    private final QualifierFactory qualifierFactory = new QualifierFactory();
    private final LocationFactory locationFactory = new LocationFactory();
    final String resourceBundle = "uk.ac.ebi.embl.gff3.mapping.gffMapper";

    public GFF3Mapper() {}

    public Entry mapGFF3ToEntry(GFF3Feature feature) {

        ResourceBundle featureQualifiers = ResourceBundle.getBundle(this.resourceBundle);

        Entry entry = entryFactory.createEntry();
        entry.setPrimaryAccession(feature.getAccession());
        String featureType = feature.getName();
        long start = feature.getStart();
        long end = feature.getEnd();
        Map attributes = feature.getAttributes();
        Collection<Qualifier> qualifierList = new ArrayList();

        for (Object o : attributes.entrySet()) {
            Map.Entry attributePairs = (Map.Entry) o;
            String attributeKey = attributePairs.getKey().toString();
            String attributeValue = attributePairs.getValue().toString();
            if (featureQualifiers.containsKey(attributeKey)) {
                Qualifier qualifier = this.qualifierFactory.createQualifier(
                        featureQualifiers.getString(attributeKey), attributeValue);
                qualifierList.add(qualifier);
            }
        }

        Location location = this.locationFactory.createLocalRange(start, end);
        Order<Location> compoundJoin = new Order();
        compoundJoin.addLocation(location);
        if (featureQualifiers.containsKey(featureType)) {
            Feature ffFeature = this.featureFactory.createFeature(featureQualifiers.getString(featureType));
            ffFeature.setLocations(compoundJoin);
            ffFeature.addQualifiers(qualifierList);
            entry.addFeature(ffFeature);
        }

        return entry;
    }

    public List<Entry> mapGFF3ToEntry(GFF3Reader gff3Reader) throws IOException {
        ArrayList<Entry> entries = new ArrayList<>();
        GFF3Feature feature = null;
        while ((feature = gff3Reader.read()) != null) {
            Entry entry = mapGFF3ToEntry(feature);
            if (entry != null) {
                entries.add(entry);
            }
        }

        return entries;
    }
}
