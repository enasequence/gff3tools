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
import java.util.stream.Collectors;
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
  /// This property is used to keep track of the presence of circular landmarks on the annotation.
  /// If the topology is circular and no circular landmark was found, then a new "region"
  /// is added using the "createLandmarkFeature" method.
  boolean missingCircularLandmark;

  ///  Keeps track of all the features belonging to a gene.
  Map<String, List<GFF3Feature>> geneMap;
  ///  List of features that do not belong to a gene.
  List<GFF3Feature> nonGeneFeatures;

  @Override
  public GFF3Annotation from(Entry entry) {

    geneMap = new LinkedHashMap<>();
    nonGeneFeatures = new ArrayList<>();
    missingCircularLandmark = entry.getSequence().getTopology() == Sequence.Topology.CIRCULAR;

    String accession = entry.getSequence().getAccession();

    // TODO: We need to handle accession versions
    entry.setPrimaryAccession(accession + ".1");
    entry.getSequence().setAccession(accession + ".1");

    GFF3Directives directives = new GFF3DirectivesFactory().from(entry);
    try {
      Map<String, List<ConversionEntry>> featureMap = ConversionUtils.getFF2GFF3FeatureMap();

      for (Feature feature : entry.getFeatures().stream().sorted().toList()) {

        if (feature.getName().equalsIgnoreCase("source")) {
          continue; // early exit
        }

        // TODO: insert a gene feature if/where appropriate
        Optional<ConversionEntry> first =
            featureMap.get(feature.getName()).stream()
                .filter(conversionEntry -> hasAllQualifiers(feature, conversionEntry))
                .findFirst();

        // Rule: Throw an error if we find an unmapped feature
        if (first.isEmpty()) throw new Exception("Mapping not found for " + feature.getName());

        updateCircularLandmarkPresence(feature);

        buildGeneFeatureMap(entry.getPrimaryAccession(), feature);
      }

      // For circular topologies; We have not found a circular feature so we must include a region
      // encompasing all source.
      if (missingCircularLandmark) {
        nonGeneFeatures.add(createLandmarkFeature(accession, entry));
      }
      sortFeaturesAndAssignId();

      return new GFF3Annotation(directives, geneMap, nonGeneFeatures);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private void updateCircularLandmarkPresence(Feature feature) {
    if (missingCircularLandmark) {
      // A feature that has "circular_RNA" can be used as a landmark for circular topologies.
      if (!feature.getQualifiers("circular_RNA").isEmpty()) {
        missingCircularLandmark = false;
      }
    }
  }

  private GFF3Feature createLandmarkFeature(String name, Entry entry) {
    CompoundLocation<Location> locations = entry.getPrimarySourceFeature().getLocations();
    return new GFF3Feature(
        entry.getPrimaryAccession(),
        ".",
        "region",
        locations.getMinPosition(),
        locations.getMaxPosition(),
        ".",
        "+",
        ".",
        Map.of("ID", name, "Is_circular", "true"));
  }

  private GFF3Feature transformFeature(String accession, Feature ffFeature, Optional<String> gene) {
    Map<String, String> qualifierMap = ConversionUtils.getFF2GFF3QualifierMap();

    String source = ".";
    String score = ".";

    Map<String, String> attributes =
        ffFeature.getQualifiers().stream()
            .filter(
                q -> !"gene".equals(q.getName())) // gene is filtered for handling overlapping gene
            .collect(
                Collectors.toMap(
                    q ->
                        qualifierMap.getOrDefault(
                            q.getName(), q.getName()), // Rename if mapping exists
                    q -> q.isValue() ? q.getValue() : "true" // Ensure non-empty values
                    ));

    gene.ifPresent(v -> attributes.put("gene", v));

    if (!getPartiality(ffFeature).isBlank()) {
      attributes.put("partial", getPartiality(ffFeature));
    }

    return new GFF3Feature(
        accession,
        source,
        ffFeature.getName(),
        ffFeature.getLocations().getMinPosition(),
        ffFeature.getLocations().getMaxPosition(),
        score,
        getStrand(ffFeature),
        getPhase(ffFeature),
        attributes);
  }

  private void buildGeneFeatureMap(String accession, Feature ffFeature) {

    List<Qualifier> genes = ffFeature.getQualifiers(Qualifier.GENE_QUALIFIER_NAME);

    try {

      if (genes.isEmpty()) {
        nonGeneFeatures.add(transformFeature(accession, ffFeature, Optional.empty()));
      } else {

        for (Qualifier gene : genes) {
          String geneName = gene.getValue();

          List<GFF3Feature> gfFeatures = geneMap.getOrDefault(geneName, new ArrayList<>());

          gfFeatures.add(transformFeature(accession, ffFeature, Optional.of(geneName)));

          geneMap.put(geneName, gfFeatures);
        }
      }
    } catch (Exception e) {
      throw new ConversionError();
    }
  }

  private void sortFeaturesAndAssignId() {
    for (String geneName : geneMap.keySet()) {
      List<GFF3Feature> gfFeatures = geneMap.get(geneName);

      // Sort feature by start and end location
      gfFeatures.sort(
          Comparator.comparingLong(GFF3Feature::start)
              .thenComparing(GFF3Feature::end, Comparator.reverseOrder()));

      Optional<GFF3Feature> firstFeature = gfFeatures.stream().findFirst();

      // Set ID and Parent
      if (firstFeature.isPresent()) {

        // Set ID for root
        String idValue = "%s_%s".formatted(firstFeature.get().name(), geneName);
        firstFeature.get().attributes().put("ID", idValue);
        String locus_tag = firstFeature.get().attributes().get("locus_tag");
        // Set Parent only for children
        gfFeatures.stream()
            .skip(1)
            .forEach(
                feature -> {
                  feature.attributes().put("Parent", idValue);
                  if (locus_tag != null) {
                    feature.attributes().put("locus_tag", locus_tag);
                  }
                  feature.attributes().remove("gene");
                });
      }
    }
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

  private String getPartiality(Feature feature) {

    StringJoiner partiality = new StringJoiner(",");

    if (feature.getLocations().isFivePrimePartial()) {
      partiality.add("start");
    }
    if (feature.getLocations().isThreePrimePartial()) {
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
}
