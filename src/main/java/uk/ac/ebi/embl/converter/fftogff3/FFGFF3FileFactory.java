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
import uk.ac.ebi.embl.api.entry.qualifier.Qualifier;
import uk.ac.ebi.embl.converter.IConversionRule;
import uk.ac.ebi.embl.converter.gff3.GFF3Feature;
import uk.ac.ebi.embl.converter.gff3.GFF3File;
import uk.ac.ebi.embl.converter.gff3.GFF3Headers;
import uk.ac.ebi.embl.converter.gff3.GFF3SourceMetadata;
import uk.ac.ebi.embl.converter.utils.ConversionEntry;
import uk.ac.ebi.embl.converter.utils.ConversionUtils;

public class FFGFF3FileFactory implements IConversionRule<Entry, GFF3File> {
  Map<String, List<GFF3Feature>> geneMap = new LinkedHashMap<>();
  List<GFF3Feature> nonGeneFeatures = new ArrayList<>();

  @Override
  public GFF3File from(Entry entry) {
    GFF3Headers headers = new FFGFF3HeadersFactory().from(entry);
    GFF3SourceMetadata metadata = new FFGFF3SourceAttributesFactory().from(entry);
    try {
      entry.setPrimaryAccession(entry.getPrimaryAccession() + ".1");
      entry.getSequence().setAccession(entry.getSequence().getAccession() + ".1");

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

        buildGeneFeatureMap(entry.getPrimaryAccession(), feature);
      }
      sortFeaturesAndAssignId();

      return new GFF3File(headers, metadata, geneMap, nonGeneFeatures);
    } catch (Exception e) {
      throw new ConversionError();
    }
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
      Map<String, String> qualifierMap = ConversionUtils.getFF2GFF3QualifierMap();

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
