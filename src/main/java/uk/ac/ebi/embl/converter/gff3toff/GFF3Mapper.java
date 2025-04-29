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
import java.util.*;
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
import uk.ac.ebi.embl.converter.gff3.GFF3File;
import uk.ac.ebi.embl.converter.gff3.reader.GFF3FileReader;
import uk.ac.ebi.embl.converter.gff3.reader.GFF3ValidationError;
import uk.ac.ebi.embl.converter.utils.ConversionUtils;

public class GFF3Mapper {
    private final Map<String, String> qmap = ConversionUtils.getGFF32FFQualifierMap();
    private final EntryFactory entryFactory = new EntryFactory();
    private final FeatureFactory featureFactory = new FeatureFactory();
    private final QualifierFactory qualifierFactory = new QualifierFactory();
    private final LocationFactory locationFactory = new LocationFactory();
    private final SequenceFactory sequenceFactory = new SequenceFactory();
    final String resourceBundle = "uk.ac.ebi.embl.gff3.mapping.gffMapper";

    public GFF3Mapper() {}

    private static List<GFF3Feature> getAnnotationFeatures(GFF3Annotation a) {
        List<List<GFF3Feature>> features = new ArrayList<>(a.geneMap().values());
        features.add(a.nonGeneFeatures());
        return features.stream().flatMap(List::stream).toList();
    }

    public Entry mapGFF3ToEntry(GFF3Annotation annotation) {

        Entry entry = entryFactory.createEntry();
        entry.setSequence(sequenceFactory.createSequence());
        if (annotation.directives().directives().size() > 0) {
            SourceFeature sourceFeature = this.featureFactory.createSourceFeature();
            for (GFF3Directives.GFF3Directive directive :
                    annotation.directives().directives()) {
                if (directive.getClass() == GFF3Directives.GFF3SequenceRegion.class) {
                    GFF3Directives.GFF3SequenceRegion reg = (GFF3Directives.GFF3SequenceRegion) directive;
                    String accession = reg.accession();
                    String accessionId = accession.substring(0, accession.lastIndexOf('.'));
                    entry.setPrimaryAccession(accessionId);
                    Location location = this.locationFactory.createLocalRange(reg.start(), reg.end());
                    Order<Location> compoundJoin = new Order();
                    compoundJoin.addLocation(location);
                    sourceFeature.setLocations(compoundJoin);
                }
            }
            entry.addFeature(sourceFeature);
        }

        for (GFF3Feature feature : getAnnotationFeatures(annotation)) {
            entry.addFeature(mapGFF3Feature(feature));
            for (GFF3Feature childFeature : feature.getChildren()) {
                Feature ffChildFeature = mapGFF3Feature(childFeature);
                if (feature.getAttributes().containsKey("gene")) {
                    ffChildFeature.addQualifier("gene", feature.getAttributes().get("gene"));
                }
                entry.addFeature(ffChildFeature);
            }
        }

        return entry;
    }

    private Feature mapGFF3Feature(GFF3Feature gff3Feature) {

        Map<String, String> attributes = gff3Feature.getAttributes();
        Collection<Qualifier> qualifiers = mapGFF3Attributes(attributes);

        CompoundLocation<Location> locations = mapGFF3Location(gff3Feature);

        String featureType = gff3Feature.getName();
        Feature ffFeature = this.featureFactory.createFeature(featureType);
        ffFeature.setLocations(locations);
        ffFeature.addQualifiers(qualifiers);

        return ffFeature;
    }

    private CompoundLocation<Location> mapGFF3Location(GFF3Feature gff3Feature) {

        long start = gff3Feature.getStart();
        long end = gff3Feature.getEnd();
        List<String> partials = Arrays.stream(
                        gff3Feature.getAttributes().getOrDefault("partial", "").split(","))
                .toList();

        Location location = this.locationFactory.createLocalRange(
                start, end, gff3Feature.getStrand().equals("-"));
        if (partials.contains("start")) {
            location.setFivePrimePartial(true);
        }
        if (partials.contains("end")) {
            location.setThreePrimePartial(true);
        }
        Join<Location> compoundJoin = new Join();
        compoundJoin.addLocation(location);
        return compoundJoin;
    }

    private Collection<Qualifier> mapGFF3Attributes(Map<String, String> attributes) {
        Collection<Qualifier> qualifierList = new ArrayList();

        for (Object o : attributes.entrySet()) {
            Map.Entry<String, String> attributePairs = (Map.Entry) o;
            String attributeKey = attributePairs.getKey();
            if (qmap.containsKey(attributeKey)) {
                attributeKey = qmap.get(attributeKey);
            }
            if (!attributeKey.isBlank()) {
                String attributeValue = attributePairs.getValue();
                qualifierList.add(qualifierFactory.createQualifier(attributeKey, attributeValue));
            }
        }

        return qualifierList;
    }

    public List<Entry> mapGFF3ToEntry(GFF3FileReader gff3Reader) throws IOException, GFF3ValidationError {
        GFF3File gff3File = gff3Reader.read();
        ArrayList<Entry> entries = new ArrayList<>(
                gff3File.annotations().stream().map(this::mapGFF3ToEntry).toList());
        return entries;
    }
}
