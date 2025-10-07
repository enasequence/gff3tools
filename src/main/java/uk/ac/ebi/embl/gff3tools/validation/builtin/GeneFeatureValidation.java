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
package uk.ac.ebi.embl.gff3tools.validation.builtin;

import java.util.*;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Anthology;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.validation.AnnotationValidation;

public class GeneFeatureValidation implements AnnotationValidation {

    public static final String VALIDATION_RULE = "GFF3_GENE_FEATURE_VALIDATION";

    private static final String GENE_ASSOCIATION_VALIDATION =
            "Features sharing gene \"%s\" are associated with \"%s\" attributes with different values (\"%s\" and \"%s\")";

    private static final String GENE_FEATURE_LOCUS_VALIDATION = "locus_tag=\"%s\" already used by \"%s\" and \"%s\"";

    private static final String DIFFERENT_GENE_VALUES_MESSAGE =
            "Features sharing locus_tag \"%s\" are associated with \"gene\" qualifiers with different values (\"%s\" and \"%s\").";

    private static final String DIFFERENT_GENE_SYNONYM_VALUES_MESSAGE =
            "Features sharing locus_tag \"%s\" are associated with \"gene_synonym\" qualifiers with different sets of values. They should all share the same values.";

    @Override
    public String getValidationRule() {
        return VALIDATION_RULE;
    }

    @Override
    public void validateAnnotation(GFF3Annotation annotation, int line) throws ValidationException {
        validateGeneLocusTagAssociation(annotation, line);
        validateGeneAssociation(annotation, line);
        validateLocusTagAssociation(annotation, line);
    }

    // TODO: The below validation is considered as Warning in the sequenceTool, check we need Error or Warning
    private void validateGeneAssociation(GFF3Annotation annotation, int line) throws ValidationException {
        List<GFF3Feature> geneFeatures = annotation.getFeatures().stream()
                .filter(gff3Feature -> gff3Feature.isAttributeExists(GFF3Attributes.GENE))
                .toList();

        Map<String, String> geneToLocusTag = new HashMap<>();
        Map<String, String> geneToPseudoGene = new HashMap<>();
        for (GFF3Feature geneFeature : geneFeatures) {
            String geneName = geneFeature.getAttributeByName(GFF3Attributes.GENE);
            String locusTag = geneFeature.getAttributeByName(GFF3Attributes.LOCUS_TAG);

            String existingLocus = geneToLocusTag.get(geneName);
            if (existingLocus != null
                    && !Objects.equals(existingLocus, locusTag)
                    && !geneFeature.getName().equalsIgnoreCase(GFF3Anthology.R_RNA_FEATURE_NAME)) {
                throw new ValidationException(
                        VALIDATION_RULE,
                        line,
                        GENE_ASSOCIATION_VALIDATION.formatted(
                                geneName, GFF3Attributes.LOCUS_TAG, existingLocus, locusTag));
            }
            geneToLocusTag.put(geneName, locusTag);

            String pseudoGeneName = geneFeature.getAttributeByName(GFF3Attributes.PSEUDOGENE);
            String existingPseudo = geneToPseudoGene.get(geneName);
            if (existingPseudo != null && !Objects.equals(existingPseudo, pseudoGeneName)) {
                throw new ValidationException(
                        VALIDATION_RULE,
                        line,
                        GENE_ASSOCIATION_VALIDATION.formatted(
                                geneName, GFF3Attributes.PSEUDOGENE, existingPseudo, pseudoGeneName));
            }
            geneToPseudoGene.put(geneName, pseudoGeneName);
        }
    }

    private void validateGeneLocusTagAssociation(GFF3Annotation annotation, int line) throws ValidationException {
        HashMap<String, GFF3Feature> locusTagToGene = new HashMap<>();
        for (GFF3Feature gff3Feature : annotation.getFeatures()) {
            if (GFF3Anthology.GENE_EQUIVALENTS.contains(gff3Feature.getName())) {
                String locusTag = gff3Feature.getAttributeByName(GFF3Attributes.LOCUS_TAG);
                if (locusTag == null) continue;
                GFF3Feature existing = locusTagToGene.putIfAbsent(locusTag, gff3Feature);
                if (existing != null) {
                    throw new ValidationException(
                            VALIDATION_RULE,
                            line,
                            GENE_FEATURE_LOCUS_VALIDATION.formatted(
                                    locusTag, existing.getName(), gff3Feature.getName()));
                }
            }
        }
    }

    private void validateLocusTagAssociation(GFF3Annotation annotation, int line) throws ValidationException {
        List<GFF3Feature> locusFeatures = annotation.getFeatures().stream()
                .filter(f -> f.isAttributeExists(GFF3Attributes.LOCUS_TAG))
                .toList();

        if (locusFeatures.isEmpty()) return;

        Map<String, List<String>> locusTagToSynonyms = new HashMap<>();
        Map<String, String> locusTagToGene = new HashMap<>();

        locusFeatures.stream()
                .filter(f -> GFF3Anthology.GENE_EQUIVALENTS.contains(f.getName())
                        || GFF3Anthology.CDS_EQUIVALENTS.contains(f.getName()))
                .findFirst()
                .ifPresent(master -> extractLocusMappings(master, locusTagToGene, locusTagToSynonyms));

        for (GFF3Feature feature : locusFeatures) {
            String locusTag = feature.getAttributeByName(GFF3Attributes.LOCUS_TAG);
            if (locusTag == null) continue;

            String currentGene = feature.getAttributeByName(GFF3Attributes.GENE);
            List<String> currentSynonyms = parseSynonyms(feature.getAttributeByName(GFF3Attributes.GENE_SYNONYM));

            if (currentGene != null) {
                if (locusTagToGene.containsKey(locusTag)) {
                    String masterGene = locusTagToGene.get(locusTag);
                    if (!masterGene.equals(currentGene)) {
                        throw new ValidationException(
                                VALIDATION_RULE,
                                line,
                                DIFFERENT_GENE_VALUES_MESSAGE.formatted(locusTag, masterGene, currentGene));
                    }
                } else {
                    locusTagToGene.put(locusTag, currentGene);
                }
            }

            if (locusTagToSynonyms.containsKey(locusTag)) {
                List<String> masterSynonyms = locusTagToSynonyms.get(locusTag);

                if (!currentSynonyms.isEmpty()
                        && !masterSynonyms.isEmpty()
                        && !areSynonymListsEqual(masterSynonyms, currentSynonyms)) {

                    throw new ValidationException(
                            VALIDATION_RULE, line, DIFFERENT_GENE_SYNONYM_VALUES_MESSAGE.formatted(locusTag));
                }
            } else {
                locusTagToSynonyms.put(locusTag, currentSynonyms);
            }
        }
    }

    private void extractLocusMappings(
            GFF3Feature feature, Map<String, String> locusTagToGene, Map<String, List<String>> locusTagToSynonyms) {
        String locusTag = feature.getAttributeByName(GFF3Attributes.LOCUS_TAG);
        if (locusTag == null) return;

        String gene = feature.getAttributeByName(GFF3Attributes.GENE);
        if (gene != null && !gene.isEmpty()) {
            locusTagToGene.put(locusTag, gene);
        }

        String synonymsRaw = feature.getAttributeByName(GFF3Attributes.GENE_SYNONYM);
        if (synonymsRaw != null && !synonymsRaw.isEmpty()) {
            List<String> synonyms = Arrays.stream(synonymsRaw.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
            locusTagToSynonyms.put(locusTag, synonyms);
        }
    }

    private List<String> parseSynonyms(String synonymValue) {
        if (synonymValue == null || synonymValue.isEmpty()) return List.of();
        return Arrays.stream(synonymValue.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private boolean areSynonymListsEqual(List<String> list1, List<String> list2) {
        if (list1.size() != list2.size()) return false;
        return new HashSet<>(list1).equals(new HashSet<>(list2));
    }
}
