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
package uk.ac.ebi.embl.gff3tools.fftogff3;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.embl.api.entry.Entry;
import uk.ac.ebi.embl.api.entry.feature.Feature;
import uk.ac.ebi.embl.api.entry.location.CompoundLocation;
import uk.ac.ebi.embl.api.entry.location.Location;
import uk.ac.ebi.embl.api.entry.qualifier.Qualifier;
import uk.ac.ebi.embl.api.entry.sequence.Sequence;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.*;
import uk.ac.ebi.embl.gff3tools.gff3.directives.GFF3SequenceRegion;
import uk.ac.ebi.embl.gff3tools.gff3.writer.TranslationWriter;
import uk.ac.ebi.embl.gff3tools.utils.ConversionUtils;
import uk.ac.ebi.embl.gff3tools.utils.Gff3Utils;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngine;

public class GFF3AnnotationFactory {

    Logger LOG = LoggerFactory.getLogger(GFF3AnnotationFactory.class);

    // Map of features with parent-child relation
    static final Map<String, Set<String>> featureRelationMap = ConversionUtils.getFeatureRelationMap();
    // Base Id ends with _ and digit (e.g. ppk_2)
    static final Pattern incrementIdpattern = Pattern.compile(".*_\\d+$");

    /// Keeps track of all the features belonging to a gene.
    Map<String, List<GFF3Feature>> geneMap;
    /// List of features that do not belong to a gene.
    List<GFF3Feature> nonGeneFeatures;

    Path fastaPath = null;

    // Map of Id with count, used for incrementing when same id is found.
    Map<String, Integer> idMap = new HashMap<>();

    GFF3DirectivesFactory directivesFactory;
    ValidationEngine validationEngine;

    public GFF3AnnotationFactory(
            ValidationEngine validationEngine, GFF3DirectivesFactory directivesFactory, Path fastaPath) {
        this.validationEngine = validationEngine;
        this.directivesFactory = directivesFactory;
        this.fastaPath = fastaPath;
    }

    public GFF3Annotation from(Entry entry) throws ValidationException {

        geneMap = new LinkedHashMap<>();
        nonGeneFeatures = new ArrayList<>();

        String accession = entry.getSequence().getAccession();
        LOG.info("Converting FF entry: {}", accession);
        GFF3SequenceRegion sequenceRegion = directivesFactory.createSequenceRegion(entry);
        try (BufferedWriter fastaWriter = Files.newBufferedWriter(
                fastaPath,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, // create file if not exists
                StandardOpenOption.APPEND)) {

            for (Feature feature : entry.getFeatures().stream().sorted().toList()) {

                if (feature.getName().equalsIgnoreCase("source")) {
                    continue; // early exit
                }
                buildGeneFeatureMap(sequenceRegion, feature, fastaWriter);
            }

            // For circular topologies; We have not found a circular feature so we must
            // include a region
            // encompasing all source.
            if (isCircularTopology(entry) && lacksCircularAttribute()) {
                nonGeneFeatures.add(createLandmarkFeature(sequenceRegion, entry));
            }
            sortFeaturesAndAssignId();

            List<GFF3Feature> features =
                    geneMap.values().stream().flatMap(List::stream).collect(Collectors.toList());
            features.addAll(nonGeneFeatures);

            // Create annotation and set values
            GFF3Annotation annotation = new GFF3Annotation();
            annotation.setSequenceRegion(sequenceRegion);
            annotation.setFeatures(features);

            validationEngine.validate(annotation, -1);

            return annotation;
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    private boolean lacksCircularAttribute() {
        return !geneMap.values().stream().flatMap(List::stream).anyMatch(feature -> feature.getAttributes()
                .containsKey("Is_circular"));
    }

    private boolean isCircularTopology(Entry entry) {
        return entry.getSequence().getTopology() == Sequence.Topology.CIRCULAR;
    }

    private GFF3Feature createLandmarkFeature(GFF3SequenceRegion sequenceRegion, Entry entry) {
        CompoundLocation<Location> locations = entry.getPrimarySourceFeature().getLocations();
        return new GFF3Feature(
                Optional.of(sequenceRegion.accession()),
                Optional.empty(),
                sequenceRegion.accessionId(),
                sequenceRegion.accessionVersion(),
                ".",
                "region",
                locations.getMinPosition(),
                locations.getMaxPosition(),
                ".",
                "+",
                ".",
                Map.of("ID", List.of(sequenceRegion.accession()), "Is_circular", List.of("true")));
    }

    private List<GFF3Feature> transformFeature(
            GFF3SequenceRegion sequenceRegion, Feature ffFeature, Optional<String> geneName, Writer fastaWriter)
            throws ValidationException {
        List<GFF3Feature> gff3Features = new ArrayList<>();

        String source = ".";
        String score = ".";

        String featureName = FeatureMapping.getGFF3FeatureName(ffFeature);

        Optional<String> id = Optional.of(getIncrementalId(featureName, geneName));
        Optional<String> parentId = getParentFeature(featureName, geneName);

        Map<String, List<String>> baseAttributes = getAttributeMap(ffFeature);

        geneName.ifPresent(v -> baseAttributes.put("gene", List.of(v)));
        id.ifPresent(v -> baseAttributes.put("ID", List.of(v)));
        parentId.ifPresent(v -> baseAttributes.put("Parent", List.of(v)));

        // Write translation to fasta and remove from attribute map.
        handleTranslation(fastaWriter, baseAttributes, id, sequenceRegion);

        CompoundLocation<Location> compoundLocation = ffFeature.getLocations();
        for (Location location : compoundLocation.getLocations()) {
            Map<String, List<String>> attributes = new LinkedHashMap<>(baseAttributes);

            List<String> partiality = getPartiality(location);
            if (!partiality.isEmpty()) {
                attributes.put("partial", partiality);
            }

            GFF3Feature gff3Feature = new GFF3Feature(
                    id,
                    parentId,
                    sequenceRegion.accessionId(),
                    sequenceRegion.accessionVersion(),
                    source,
                    featureName,
                    location.getBeginPosition(),
                    location.getEndPosition(),
                    score,
                    getStrand(location, compoundLocation),
                    getPhase(ffFeature),
                    attributes);
            validationEngine.validate(gff3Feature, -1);
            gff3Features.add(gff3Feature);
        }

        return gff3Features;
    }

    /**
     * Write translation to fasta and remove from attribute map.
     */
    private void handleTranslation(
            Writer fastaWriter,
            Map<String, List<String>> baseAttributes,
            Optional<String> featureId,
            GFF3SequenceRegion sequenceRegion) {
        if (baseAttributes.containsKey("translation") && featureId.isPresent()) {
            String translationKey = TranslationWriter.getTranslationKey(sequenceRegion.accession(), featureId.get());
            List<String> translation = baseAttributes.get("translation");
            TranslationWriter.writeTranslation(fastaWriter, translationKey, translation.get(0));
            baseAttributes.remove("translation");
        }
    }

    public Map<String, List<String>> getAttributeMap(Feature ffFeature) {
        Map<String, String> qualifierMap = ConversionUtils.getFF2GFF3QualifierMap();
        Map<String, List<String>> attributes = new LinkedHashMap<>();

        ffFeature.getQualifiers().stream()
                .filter(q -> !"gene".equals(q.getName()))
                .forEach(q -> {
                    String key = qualifierMap.getOrDefault(q.getName(), q.getName());
                    String value = q.isValue() ? q.getValue() : "true";
                    Gff3Utils.addAttribute(attributes, key, value);
                });
        return attributes;
    }

    private void buildGeneFeatureMap(GFF3SequenceRegion sequenceRegion, Feature ffFeature, Writer fastaWriter)
            throws ValidationException {

        List<Qualifier> genes = ffFeature.getQualifiers(Qualifier.GENE_QUALIFIER_NAME);

        if (genes.isEmpty()) {
            nonGeneFeatures.addAll(transformFeature(sequenceRegion, ffFeature, Optional.empty(), fastaWriter));
        } else {

            for (Qualifier gene : genes) {
                String geneName = gene.getValue();

                List<GFF3Feature> gfFeatures = geneMap.getOrDefault(geneName, new ArrayList<>());

                gfFeatures.addAll(transformFeature(sequenceRegion, ffFeature, Optional.of(geneName), fastaWriter));
                geneMap.put(geneName, gfFeatures);
            }
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

        String locusTag = root.getAttributeByName("locus_tag").orElse(null);
        gffFeatures.add(root);

        // Recursively process children
        for (GFF3Feature child : root.getChildren()) {
            if (child.hasChildren()) {
                orderRootAndChildren(gffFeatures, child);
            } else {
                // Leaf node processing
                if (locusTag != null && child.getAttributes().get("locus_tag") == null) {
                    // Add parent's locus_tag only when it is not present in children
                    child.getAttributes().put("locus_tag", List.of(locusTag));
                }
                child.getAttributes().remove("gene");
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

    public String getIncrementalId(String featureName, Optional<String> geneName) {

        String baseId = geneName.filter(gene -> !gene.isEmpty())
                .map(gene -> "%s_%s".formatted(featureName, gene))
                .orElse(featureName);

        // Add ".S" to baseId when the id ends with _ and a digit (e.g ppk_2 -> ppk_2.S)
        if (incrementIdpattern.matcher(baseId).matches()) {
            baseId = baseId + ".S";
        }
        int count = idMap.getOrDefault(baseId, 0);
        idMap.put(baseId, count + 1);

        return count > 0 ? "%s_%d".formatted(baseId, count) : baseId;
    }

    private Optional<String> getParentFeature(String emblFeatureName, Optional<String> geneName) {

        if (!geneName.isPresent()) {
            return Optional.empty();
        }

        List<GFF3Feature> gffFeatures = geneMap.getOrDefault(geneName.orElse(""), Collections.emptyList());
        Set<String> definedParents = featureRelationMap.getOrDefault(emblFeatureName, Collections.emptySet());

        // As the features are ordered by location, iterating the features from last to first to get the immediate
        // parent of a child feature.
        for (int i = gffFeatures.size() - 1; i >= 0; i--) {
            GFF3Feature feature = gffFeatures.get(i);
            if (definedParents.contains(feature.getName())) {
                return feature.getId();
            }
        }
        return Optional.empty();
    }
}
