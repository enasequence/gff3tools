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
package uk.ac.ebi.embl.converter.fftogff3;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.embl.api.entry.Entry;
import uk.ac.ebi.embl.api.entry.feature.Feature;
import uk.ac.ebi.embl.api.entry.location.CompoundLocation;
import uk.ac.ebi.embl.api.entry.location.Location;
import uk.ac.ebi.embl.api.entry.qualifier.Qualifier;
import uk.ac.ebi.embl.api.entry.sequence.Sequence;
import uk.ac.ebi.embl.converter.ConversionError;
import uk.ac.ebi.embl.converter.gff3.*;
import uk.ac.ebi.embl.converter.utils.ConversionEntry;
import uk.ac.ebi.embl.converter.utils.ConversionUtils;
import uk.ac.ebi.embl.converter.utils.Gff3Utils;

public class GFF3AnnotationFactory {

    Logger LOG = LoggerFactory.getLogger(GFF3AnnotationFactory.class);

    // Map of features with parent-child relation
    static final Map<String, Set<String>> featureRelationMap = ConversionUtils.getFeatureRelationMap();

    ///  Keeps track of all the features belonging to a gene.
    Map<String, List<GFF3Feature>> geneMap;
    ///  List of features that do not belong to a gene.
    List<GFF3Feature> nonGeneFeatures;

    // Map of Id with count, used for incrementing when same id is found.
    Map<String, Integer> idMap = new HashMap<>();

    boolean ignoreSpecies;

    public GFF3AnnotationFactory(boolean ignoreSpecies) {
        this.ignoreSpecies = ignoreSpecies;
    }

    public GFF3Annotation from(Entry entry) throws ConversionError {

        geneMap = new LinkedHashMap<>();
        nonGeneFeatures = new ArrayList<>();

        String accession = entry.getSequence().getAccession();
        LOG.info("Converting FF entry: {}", accession);
        // TODO: We need to handle accession versions
        entry.setPrimaryAccession(accession + ".1");
        entry.getSequence().setAccession(accession + ".1");

        GFF3Directives directives = new GFF3DirectivesFactory(this.ignoreSpecies).from(entry);
        try {

            for (Feature feature : entry.getFeatures().stream().sorted().toList()) {

                if (feature.getName().equalsIgnoreCase("source")) {
                    continue; // early exit
                }

                buildGeneFeatureMap(entry.getPrimaryAccession(), feature);
            }

            // For circular topologies; We have not found a circular feature so we must include a region
            // encompasing all source.
            if (isCircularTopology(entry) && lacksCircularAttribute()) {
                nonGeneFeatures.add(createLandmarkFeature(accession, entry));
            }
            sortFeaturesAndAssignId();

            List<GFF3Feature> features =
                    geneMap.values().stream().flatMap(List::stream).collect(Collectors.toList());
            features.addAll(nonGeneFeatures);

            // Create annotation and set values
            GFF3Annotation annotation = new GFF3Annotation();
            annotation.setFeatures(features);
            annotation.setDirectives(directives);

            return annotation;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private boolean lacksCircularAttribute() {
        return !geneMap.values().stream().flatMap(List::stream).anyMatch(feature -> feature.getAttributes()
                .containsKey("Is_circular"));
    }

    private boolean isCircularTopology(Entry entry) {
        return entry.getSequence().getTopology() == Sequence.Topology.CIRCULAR;
    }

    private GFF3Feature createLandmarkFeature(String accession, Entry entry) {
        CompoundLocation<Location> locations = entry.getPrimarySourceFeature().getLocations();
        return new GFF3Feature(
                Optional.of(accession),
                Optional.empty(),
                entry.getPrimaryAccession(),
                ".",
                "region",
                locations.getMinPosition(),
                locations.getMaxPosition(),
                ".",
                "+",
                ".",
                Map.of("ID", accession, "Is_circular", "true"));
    }

    private List<GFF3Feature> transformFeature(String accession, Feature ffFeature, Optional<String> geneName) {
        List<GFF3Feature> gff3Features = new ArrayList<>();

        String source = ".";
        String score = ".";

        Optional<String> id = Optional.empty();
        Optional<String> parentId = Optional.empty();

        String featureName = getGFF3FeatureName(ffFeature);

        if (geneName.isPresent()) {
            id = Optional.of(getIncrementalId(featureName, geneName.get()));
            parentId = Optional.of(getParentFeature(featureName, geneName));
        }

        Map<String, Object> baseAttributes = getAttributeMap(ffFeature);

        geneName.ifPresent(v -> baseAttributes.put("gene", v));
        id.ifPresent(v -> baseAttributes.put("ID", v));
        parentId.ifPresent(v -> baseAttributes.put("Parent", v));

        CompoundLocation<Location> compoundLocation = ffFeature.getLocations();
        for (Location location : compoundLocation.getLocations()) {
            Map<String, Object> attributes = new LinkedHashMap<>(baseAttributes);

            List<String> partiality = getPartiality(location);
            if (!partiality.isEmpty()) {
                attributes.put("partial", partiality);
            }

            gff3Features.add(new GFF3Feature(
                    id,
                    parentId,
                    accession,
                    source,
                    featureName,
                    location.getBeginPosition(),
                    location.getEndPosition(),
                    score,
                    getStrand(location, compoundLocation),
                    getPhase(ffFeature),
                    attributes));
        }

        return gff3Features;
    }

    public Map<String, Object> getAttributeMap(Feature ffFeature) {
        Map<String, String> qualifierMap = ConversionUtils.getFF2GFF3QualifierMap();
        Map<String, Object> attributes = new LinkedHashMap<>();

        ffFeature.getQualifiers().stream()
                .filter(q -> !"gene".equals(q.getName()))
                .forEach(q -> {
                    String key = qualifierMap.getOrDefault(q.getName(), q.getName());
                    String value = q.isValue() ? q.getValue() : "true";

                    Gff3Utils.addAttribute(attributes, key, value);
                });
        return attributes;
    }

    private void buildGeneFeatureMap(String accession, Feature ffFeature) throws ConversionError {

        List<Qualifier> genes = ffFeature.getQualifiers(Qualifier.GENE_QUALIFIER_NAME);

        try {

            if (genes.isEmpty()) {
                nonGeneFeatures.addAll(transformFeature(accession, ffFeature, Optional.empty()));
            } else {

                for (Qualifier gene : genes) {
                    String geneName = gene.getValue();

                    List<GFF3Feature> gfFeatures = geneMap.getOrDefault(geneName, new ArrayList<>());

                    gfFeatures.addAll(transformFeature(accession, ffFeature, Optional.of(geneName)));
                    geneMap.put(geneName, gfFeatures);
                }
            }
        } catch (Exception e) {
            throw new ConversionError(e.getMessage());
        }
    }

    private void sortFeaturesAndAssignId() {
        for (String geneName : geneMap.keySet()) {
            List<GFF3Feature> gffFeatures = geneMap.get(geneName);

            // build a tree of parent node and its children
            List<GFF3Feature> rootNode = buildFeatureTree(gffFeatures);

            // Clear and re-add in correct order
            gffFeatures.clear();
            for (GFF3Feature root : rootNode) {
                orderRootAndChildren(gffFeatures, root);
            }
        }
    }

    public List<GFF3Feature> buildFeatureTree(List<GFF3Feature> gffFeatures) {
        List<GFF3Feature> rootNode = new ArrayList<>();

        Map<String, GFF3Feature> idMap = gffFeatures.stream()
                .filter(f -> f.getId().isPresent())
                .collect(Collectors.toMap(
                        f -> f.getId().get(), Function.identity(), (existing, replacement) -> replacement));

        for (GFF3Feature feature : gffFeatures) {
            String parentId = feature.getParentId().orElse(null);
            if (parentId != null && idMap.containsKey(parentId)) {
                GFF3Feature parent = idMap.get(parentId);
                parent.addChild(feature);
                feature.setParent(parent);
            } else {
                rootNode.add(feature);
            }
        }

        return rootNode;
    }

    public void orderRootAndChildren(List<GFF3Feature> gffFeatures, GFF3Feature root) {

        String locusTag = (String) root.getAttributes().get("locus_tag");
        gffFeatures.add(root);

        // Recursively process children
        for (GFF3Feature child : root.getChildren()) {
            if (child.hasChildren()) {
                orderRootAndChildren(gffFeatures, child);
            } else {
                // Leaf node processing
                if (locusTag != null && child.getAttributes().get("locus_tag")==null) {
                    // Add parent's locus_tag only when it is not present in children
                    child.getAttributes().put("locus_tag", locusTag);
                }
                child.getAttributes().remove("gene");
                child.getAttributes().remove("ID");
                gffFeatures.add(child);
            }
        }

        if (hasParent(root, gffFeatures)) {
            // Parent cleanup
            root.getAttributes().remove("gene");
        } else {
            // Child cleanup
            root.getAttributes().remove("Parent");
        }
    }

    private String getStrand(Location location, CompoundLocation<Location> compoundLocation) {
        boolean effectivelyComplemented = location.isComplement() || compoundLocation.isComplement();
        return effectivelyComplemented ? "-" : "+";
    }

    private String getPhase(Feature feature) {

        // Rule: Use the phase value if present in a qualified.
        // Rule: If phase qualifier is not present, calculate it only for CDS (default
        // 0) or use "." otherwise

        Qualifier phase = feature.getQualifiers().stream()
                .filter(qualifier -> qualifier.getName().equalsIgnoreCase("phase"))
                .findFirst()
                .orElse(null);
        Qualifier codonStart = feature.getQualifiers().stream()
                .filter(qualifier -> qualifier.getName().equalsIgnoreCase("codon_start"))
                .findFirst()
                .orElse(null);
        if (phase != null) {
            return phase.getValue();
        } else if (feature.getName().equalsIgnoreCase("CDS")) {
            return codonStart == null ? "0" : String.valueOf((Long.parseLong(codonStart.getValue()) - 1));
        }

        return ".";
    }

    private List<String> getPartiality(Location location) {

        List<String> partiality = new ArrayList<>();
        if (location.isFivePrimePartial()) {
            partiality.add(location.isComplement() ? "end" : "start");
        }
        if (location.isThreePrimePartial()) {
            partiality.add(location.isComplement() ? "start" : "end");
        }
        return partiality;
    }

    private boolean hasParent(GFF3Feature feature, List<GFF3Feature> gffFeatures) {
        Optional<String> parentId = feature.getParentId();
        // Check if gffFeatures has the parent
        return parentId.isPresent()
                && gffFeatures.stream().anyMatch(f -> f.getId()
                        .map(id -> id.equalsIgnoreCase(parentId.get()))
                        .orElse(false));
    }

    public String getIncrementalId(String name, String geneName) {
        String baseId = "%s_%s".formatted(name, geneName);
        int count = idMap.getOrDefault(baseId, 0);
        idMap.put(baseId, count + 1);

        return count > 0 ? "%s_%d".formatted(baseId, count) : baseId;
    }

    private String getParentFeature(String emblFeatureName, Optional geneName) {

        if (!geneName.isPresent()) {
            return "";
        }

        List<GFF3Feature> gffFeatures = geneMap.getOrDefault(geneName.get(), Collections.emptyList());
        Set<String> definedParents = featureRelationMap.getOrDefault(emblFeatureName, Collections.emptySet());
        for (GFF3Feature feature : gffFeatures) {
            if (definedParents.contains(feature.getName())) {
                return feature.getId().orElse("");
            }
        }
        return "";
    }

    private String getGFF3FeatureName(Feature ffFeature) {

        List<ConversionEntry> mappings = ConversionUtils.getFF2GFF3FeatureMap().get(ffFeature.getName());
        if (mappings == null) {
            return ffFeature.getName();
        }

        // return the soTerm of the max qualifier mapping
        return mappings.stream()
                .filter(entry -> entry.getFeature().equalsIgnoreCase(ffFeature.getName()))
                .filter(entry -> hasAllQualifiers(ffFeature, entry))
                .max(Comparator.comparingInt(entry -> entry.getQualifiers().size()))
                .map(ConversionEntry::getSOTerm)
                .orElse(ffFeature.getName());
    }

    private boolean hasAllQualifiers(Feature feature, ConversionEntry conversionEntry) {
        Map<String, String> requiredQualifiers = conversionEntry.getQualifiers();

        boolean matchesAllQualifiers = true;
        for (String expectedQualifierName : requiredQualifiers.keySet()) {
            boolean qualifierMatches = false;
            for (Qualifier featureQualifier : feature.getQualifiers(expectedQualifierName)) {
                // When qualifier value is not found the value is considered "true"
                String qualifierValue = featureQualifier.getValue() == null ? "true" : featureQualifier.getValue();
                qualifierMatches = qualifierValue.equalsIgnoreCase(requiredQualifiers.get(expectedQualifierName));
                if (qualifierMatches) {
                    break;
                }
            }
            matchesAllQualifiers = qualifierMatches;
            if (!matchesAllQualifiers) {
                break;
            }
        }

        return matchesAllQualifiers;
    }
}
