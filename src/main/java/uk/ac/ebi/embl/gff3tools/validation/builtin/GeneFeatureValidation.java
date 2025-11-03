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
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.utils.ConversionUtils;
import uk.ac.ebi.embl.gff3tools.utils.OntologyClient;
import uk.ac.ebi.embl.gff3tools.utils.OntologyTerm;
import uk.ac.ebi.embl.gff3tools.validation.Validation;
import uk.ac.ebi.embl.gff3tools.validation.meta.Gff3Validation;
import uk.ac.ebi.embl.gff3tools.validation.meta.RuleSeverity;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationMethod;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationType;

@Gff3Validation
public class GeneFeatureValidation extends Validation {

    private static final String GENE_ASSOCIATION_VALIDATION =
            "Features sharing gene \"%s\" are associated with \"%s\" attributes with different values (\"%s\" and \"%s\")";

    private static final String GENE_FEATURE_LOCUS_VALIDATION = "locus_tag=\"%s\" already used by \"%s\" and \"%s\"";

    private static final String DIFFERENT_GENE_VALUES_MESSAGE =
            "Features sharing locus_tag \"%s\" are associated with \"gene\" qualifiers with different values (\"%s\" and \"%s\").";

    private static final String DIFFERENT_GENE_SYNONYM_VALUES_MESSAGE =
            "Features sharing locus_tag \"%s\" are associated with \"gene_synonym\" qualifiers with different sets of values. They should all share the same values.";

    private final OntologyClient ontologyClient = ConversionUtils.getOntologyClient();

    private final Map<String, Map<String, String>> annotationGeneToLocusTag = new HashMap<>();
    private final Map<String, Map<String, String>> annotationGeneToPseudoGene = new HashMap<>();
    private final Map<String, Map<String, GFF3Feature>> annotationLocusTagToGeneFeature = new HashMap<>();
    private final Map<String, Map<String, String>> annotationLocusTagToGene = new HashMap<>();
    private final Map<String, Map<String, List<String>>> annotationLocusTagToSynonyms = new HashMap<>();

    @ValidationMethod(rule = "GENE_ASSOCIATION", type = ValidationType.FEATURE, severity = RuleSeverity.WARN)
    public void validateGeneAssociation(GFF3Feature feature, int line) throws ValidationException {
        if (feature == null || !feature.hasAttribute(GFF3Attributes.GENE)) return;

        Map<String, String> geneToLocusTag =
                annotationGeneToLocusTag.computeIfAbsent(feature.accession(), k -> new HashMap<>());
        Map<String, String> geneToPseudoGene =
                annotationGeneToPseudoGene.computeIfAbsent(feature.accession(), k -> new HashMap<>());

        String geneName = feature.getAttributeByName(GFF3Attributes.GENE);
        String locusTag = feature.getAttributeByName(GFF3Attributes.LOCUS_TAG);
        String existingLocus = geneToLocusTag.get(geneName);

        Optional<String> soIdOpt = ontologyClient.findTermByNameOrSynonym(feature.getName());
        if (existingLocus != null && !Objects.equals(existingLocus, locusTag)) {
            boolean isRrna = soIdOpt.map(soId -> soId.equals(OntologyTerm.RRNA.ID)
                            || soId.equals(OntologyTerm.PSEUDOGENIC_RRNA.ID)
                            || ontologyClient.isChildOf(soId, OntologyTerm.PSEUDOGENIC_RRNA.ID))
                    .orElse(false);

            if (!isRrna) {
                throw new ValidationException(
                        line,
                        GENE_ASSOCIATION_VALIDATION.formatted(
                                geneName, GFF3Attributes.LOCUS_TAG, existingLocus, locusTag));
            }
        }
        geneToLocusTag.put(geneName, locusTag);

        String pseudoGeneName = feature.getAttributeByName(GFF3Attributes.PSEUDOGENE);
        String existingPseudo = geneToPseudoGene.get(geneName);
        if (existingPseudo != null && !Objects.equals(existingPseudo, pseudoGeneName)) {
            throw new ValidationException(
                    line,
                    GENE_ASSOCIATION_VALIDATION.formatted(
                            geneName, GFF3Attributes.PSEUDOGENE, existingPseudo, pseudoGeneName));
        }
        geneToPseudoGene.put(geneName, pseudoGeneName);
    }

    @ValidationMethod(rule = "GENE_LOCUS_TAG_ASSOCIATION", type = ValidationType.FEATURE)
    public void validateGeneLocusTagAssociation(GFF3Feature feature, int line) throws ValidationException {
        String featureName = feature.getName();
        Optional<String> soIdOpt = ontologyClient.findTermByNameOrSynonym(featureName);

        if (soIdOpt.isEmpty()) {
            return;
        }

        String soId = soIdOpt.get();

        // Check if feature is a gene or pseudogene type
        boolean isGene = soId.equals(OntologyTerm.GENE.ID)
                || soId.equals(OntologyTerm.PSEUDOGENE.ID)
                || soId.equals(OntologyTerm.UNITARY_PSEUDOGENE.ID)
                || ontologyClient.isChildOf(soId, OntologyTerm.PSEUDOGENE.ID)
                || ontologyClient.isChildOf(soId, OntologyTerm.UNITARY_PSEUDOGENE.ID);

        if (!isGene) {
            return;
        }
        Map<String, GFF3Feature> locusTagToGeneFeature =
                annotationLocusTagToGeneFeature.computeIfAbsent(feature.accession(), k -> new HashMap<>());

        String locusTag = feature.getAttributeByName(GFF3Attributes.LOCUS_TAG);
        if (locusTag == null || locusTag.isBlank()) {
            return;
        }

        GFF3Feature existing = locusTagToGeneFeature.putIfAbsent(locusTag, feature);

        if (existing != null) {
            throw new ValidationException(
                    line, GENE_FEATURE_LOCUS_VALIDATION.formatted(locusTag, existing.getName(), feature.getName()));
        }
    }

    @ValidationMethod(rule = "LOCUS_TAG_ASSOCIATION", type = ValidationType.FEATURE)
    public void validateLocusTagAssociation(GFF3Feature feature, int line) throws ValidationException {
        if (feature == null || !feature.hasAttribute(GFF3Attributes.LOCUS_TAG)) {
            return;
        }

        String locusTag = feature.getAttributeByName(GFF3Attributes.LOCUS_TAG);
        if (locusTag == null || locusTag.isBlank()) {
            return;
        }

        Map<String, String> locusTagToGene =
                annotationLocusTagToGene.computeIfAbsent(feature.accession(), k -> new HashMap<>());
        Map<String, List<String>> locusTagToSynonyms =
                annotationLocusTagToSynonyms.computeIfAbsent(feature.accession(), k -> new HashMap<>());

        if (isGeneOrCds(feature)) {
            // Extract the first gene and gene_synonym values to check duplicate
            extractLocusMappings(feature, locusTagToGene, locusTagToSynonyms);
        }

        String currentGene = feature.getAttributeByName(GFF3Attributes.GENE);
        List<String> currentSynonyms = parseSynonyms(feature.getAttributeByName(GFF3Attributes.GENE_SYNONYM));

        if (currentGene != null) {
            String masterGene = locusTagToGene.get(locusTag);
            if (masterGene != null && !masterGene.equals(currentGene)) {
                throw new ValidationException(
                        line, DIFFERENT_GENE_VALUES_MESSAGE.formatted(locusTag, masterGene, currentGene));
            }
            locusTagToGene.putIfAbsent(locusTag, currentGene);
        }

        List<String> masterSynonyms = locusTagToSynonyms.get(locusTag);
        if (masterSynonyms != null) {
            if (!currentSynonyms.isEmpty()
                    && !masterSynonyms.isEmpty()
                    && !areSynonymListsEqual(masterSynonyms, currentSynonyms)) {
                throw new ValidationException(line, DIFFERENT_GENE_SYNONYM_VALUES_MESSAGE.formatted(locusTag));
            }
        } else {
            locusTagToSynonyms.put(locusTag, currentSynonyms);
        }
    }

    private void extractLocusMappings(
            GFF3Feature feature, Map<String, String> locusTagToGene, Map<String, List<String>> locusTagToSynonyms) {
        String locusTag = feature.getAttributeByName(GFF3Attributes.LOCUS_TAG);
        if (locusTag == null) return;

        String gene = feature.getAttributeByName(GFF3Attributes.GENE);
        if (gene != null && !gene.isEmpty() && locusTagToGene.isEmpty()) {
            locusTagToGene.put(locusTag, gene);
        }

        String synonymsRaw = feature.getAttributeByName(GFF3Attributes.GENE_SYNONYM);
        if (synonymsRaw != null && !synonymsRaw.isEmpty() && locusTagToSynonyms.isEmpty()) {
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

    private boolean isGeneOrCds(GFF3Feature feature) {
        String featureName = feature.getName();
        Optional<String> soIdOpt = ontologyClient.findTermByNameOrSynonym(featureName);
        if (soIdOpt.isEmpty()) {
            return false;
        }

        String soId = soIdOpt.get();

        return soId.equals(OntologyTerm.GENE.ID)
                || soId.equals(OntologyTerm.CDS.ID)
                || soId.equals(OntologyTerm.PSEUDOGENIC_CDS.ID)
                || ontologyClient.isChildOf(soId, OntologyTerm.GENE.ID)
                || ontologyClient.isChildOf(soId, OntologyTerm.CDS.ID)
                || ontologyClient.isChildOf(soId, OntologyTerm.PSEUDOGENE.ID)
                || ontologyClient.isChildOf(soId, OntologyTerm.PSEUDOGENIC_CDS.ID);
    }
}
