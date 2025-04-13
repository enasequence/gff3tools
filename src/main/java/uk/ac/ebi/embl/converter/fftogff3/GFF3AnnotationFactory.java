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
import uk.ac.ebi.embl.converter.IConversionRule;
import uk.ac.ebi.embl.converter.gff3.*;
import uk.ac.ebi.embl.converter.utils.ConversionEntry;
import uk.ac.ebi.embl.converter.utils.ConversionUtils;

public class GFF3AnnotationFactory implements IConversionRule<Entry, GFF3Annotation> {

  Logger LOG = LoggerFactory.getLogger(GFF3AnnotationFactory.class);

  static final Map<String, String> featureRelationMap = ConversionUtils.getFeatureRelationMap();

  ///  Keeps track of all the features belonging to a gene.
  Map<String, List<GFF3Feature>> geneMap;
  ///  List of features that do not belong to a gene.
  List<GFF3Feature> nonGeneFeatures;

  boolean ignoreSpecies;

  public GFF3AnnotationFactory(boolean ignoreSpecies) {
    this.ignoreSpecies = ignoreSpecies;
  }

  @Override
  public GFF3Annotation from(Entry entry) {

    geneMap = new LinkedHashMap<>();
    nonGeneFeatures = new ArrayList<>();

    String accession = entry.getSequence().getAccession();
    LOG.info("Converting FF entry: {}", accession);
    // TODO: We need to handle accession versions
    entry.setPrimaryAccession(accession + ".1");
    entry.getSequence().setAccession(accession + ".1");

    GFF3Directives directives = new GFF3DirectivesFactory(this.ignoreSpecies).from(entry);
    try {
      Map<String, List<ConversionEntry>> featureMap = ConversionUtils.getFF2GFF3FeatureMap();

      for (Feature feature : entry.getFeatures().stream().sorted().toList()) {

        if (feature.getName().equalsIgnoreCase("source")) {
          continue; // early exit
        }

        // TODO: insert a gene feature if/where appropriate
        Optional<ConversionEntry> first =
            Optional.ofNullable(featureMap.get(feature.getName())).stream()
                .flatMap(List::stream)
                .filter(conversionEntry -> hasAllQualifiers(feature, conversionEntry))
                .findFirst();

        // Rule: Throw an error if we find an unmapped feature
        if (first.isEmpty()) throw new Exception("Mapping not found for " + feature.getName());

        buildGeneFeatureMap(entry.getPrimaryAccession(), feature);
      }

      // For circular topologies; We have not found a circular feature so we must include a region
      // encompasing all source.
      if (isCircularTopology(entry) && lacksCircularAttribute()) {
        nonGeneFeatures.add(createLandmarkFeature(accession, entry));
      }
      sortFeaturesAndAssignId();

      return new GFF3Annotation(directives, geneMap, nonGeneFeatures);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private boolean lacksCircularAttribute() {
    return !geneMap.values().stream()
        .flatMap(List::stream)
        .anyMatch(feature -> feature.getAttributes().containsKey("Is_circular"));
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

  private List<GFF3Feature> transformFeature(
      String accession, Feature ffFeature, Optional<String> geneName) {
    Map<String, String> qualifierMap = ConversionUtils.getFF2GFF3QualifierMap();
    List<GFF3Feature> gff3Features = new ArrayList<>();

    String source = ".";
    String score = ".";

      Optional<String> id = Optional.empty();
      Optional<String> parentId = Optional.empty();

      if (geneName.isPresent()) {
          id = Optional.of(getId(ffFeature.getName(), geneName.get()));
          String parentFeatureName = getParentFeature(ffFeature.getName());
          parentId = Optional.ofNullable(parentFeatureName).map(name -> getId(name, geneName.get()));
      }

      Map<String, String> baseAttributes =
        ffFeature.getQualifiers().stream()
            .filter(
                q -> !"gene".equals(q.getName())) // gene is filtered for handling overlapping gene
            .collect(
                Collectors.toMap(
                    q ->
                        qualifierMap.getOrDefault(
                            q.getName(), q.getName()), // Rename if mapping exists
                    q -> q.isValue() ? q.getValue() : "true", // Ensure non-empty values
                    (existing, replacement) -> existing));

    geneName.ifPresent(v -> baseAttributes.put("gene", v));
    id.ifPresent(v -> baseAttributes.put("ID", v));
    parentId.ifPresent(v -> baseAttributes.put("Parent", v));

    for (Location location : ffFeature.getLocations().getLocations()) {
      Map<String, String> attributes = new LinkedHashMap<>(baseAttributes);

      String partiality = getPartiality(location);
      if (!partiality.isBlank()) {
        attributes.put("partial", partiality);
      }

      gff3Features.add(
          new GFF3Feature(
                  id,
                  parentId,
              accession,
              source,
              ffFeature.getName(),
              location.getBeginPosition(),
              location.getEndPosition(),
              score,
              getStrand(ffFeature),
              getPhase(ffFeature),
              attributes));
    }

    return gff3Features;
  }

  private void buildGeneFeatureMap(String accession, Feature ffFeature) {

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
      throw new ConversionError();
    }
  }

    private void sortFeaturesAndAssignId() {
        for (String geneName : geneMap.keySet()) {
            List<GFF3Feature> gffFeatures = geneMap.get(geneName);

            // Sort gffFeatures by start asc, end desc
            gffFeatures.sort(
                    Comparator.comparingLong(GFF3Feature::getStart)
                            .thenComparing(GFF3Feature::getEnd, Comparator.reverseOrder()));

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

        Map<String, GFF3Feature> idMap =
                gffFeatures.stream()
                        .filter(f -> f.getId().isPresent())
                        .collect(
                                Collectors.toMap(
                                        f -> f.getId().get(),
                                        Function.identity(),
                                        (existing, replacement) -> replacement));

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

        String locusTag = root.getAttributes().get("locus_tag");
        gffFeatures.add(root);

        // Recursively process children
        for (GFF3Feature child : root.getChildren()) {
            if (child.hasChildren()) {
                orderRootAndChildren(gffFeatures, child);
            } else {
                // Leaf node processing
                if (locusTag != null) {
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

    private boolean hasParent(GFF3Feature feature, List<GFF3Feature> gffFeatures) {
        Optional<String> parentId = feature.getParentId();
        // Check if gffFeatures has the parent
        return parentId.isPresent()
                && gffFeatures.stream()
                .anyMatch(f -> f.getId().map(id -> id.equalsIgnoreCase(parentId.get())).orElse(false));
    }

  private String getStrand(Feature feature) {
    return feature.getLocations().isComplement() ? "-" : "+";
  }

  private String getPhase(Feature feature) {

    // Rule: Use the phase value if present in a qualified.
    // Rule: If phase qualifier is not present, calculate it only for CDS (default
    // 0) or use "." otherwise

    Qualifier phase =
        feature.getQualifiers().stream()
            .filter(qualifier -> qualifier.getName().equalsIgnoreCase("phase"))
            .findFirst()
            .orElse(null);
    Qualifier codonStart =
        feature.getQualifiers().stream()
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

  private String getPartiality(Location location) {

    StringJoiner partiality = new StringJoiner(",");

    if (location.isFivePrimePartial()) {
      partiality.add("start");
    }
    if (location.isThreePrimePartial()) {
      partiality.add("end");
    }
    // Returns empty string if non partial location
    return partiality.length() > 1 ? partiality.toString() : "";
  }

  private boolean hasAllQualifiers(Feature feature, ConversionEntry conversionEntry) {
    boolean firstQualifierMatches = conversionEntry.getQualifier1() == null;
    boolean secondQualifierMatches = conversionEntry.getQualifier2() == null;

    for (Qualifier qualifier : feature.getQualifiers()) {
      String formatted = "/%s=%s".formatted(qualifier.getName(), qualifier.getValue());
      firstQualifierMatches |= formatted.equalsIgnoreCase(conversionEntry.getQualifier1());
      secondQualifierMatches |= formatted.equalsIgnoreCase(conversionEntry.getQualifier2());
    }
    return firstQualifierMatches && secondQualifierMatches;
  }

  private String getId(String name, String geneName) {
    return "%s_%s".formatted(name, geneName);
  }

  private String getParentFeature(String featureName) {
    return featureRelationMap.get(featureName);
  }
}
