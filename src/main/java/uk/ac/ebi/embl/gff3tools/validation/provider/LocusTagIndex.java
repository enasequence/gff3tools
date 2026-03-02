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
package uk.ac.ebi.embl.gff3tools.validation.provider;

import java.util.*;
import lombok.Getter;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.utils.OntologyClient;
import uk.ac.ebi.embl.gff3tools.utils.OntologyTerm;

/**
 * Pre-computed index of locus-tag relationships for an annotation's features.
 *
 * <p>Built in a single pass over the feature list, this index centralises the
 * map-building logic previously duplicated across {@code GeneFeatureValidation},
 * {@code FeatureSpecificValidation}, and {@code LocusTagAssociationFix}.
 *
 * <p>Instances are immutable once constructed via {@link #build(List, OntologyClient)}.
 */
@Getter
public class LocusTagIndex {

    /** Maps gene name to its associated locus_tag (first-seen wins). */
    private final Map<String, String> geneToLocusTag;

    /** Maps locus_tag to the gene/pseudogene GFF3Feature that owns it (first-seen wins). */
    private final Map<String, GFF3Feature> locusTagToGeneFeature;

    /** Maps locus_tag to the gene name, populated from gene/CDS type features (first-seen wins). */
    private final Map<String, String> locusTagToGene;

    /** Maps locus_tag to gene_synonym list, populated from gene/CDS type features (first-seen wins). */
    private final Map<String, List<String>> locusTagToSynonyms;

    /** Maps locus_tag to peptide features (polypeptide_region descendants). */
    private final Map<String, List<GFF3Feature>> locusTagToPeptides;

    private LocusTagIndex(
            Map<String, String> geneToLocusTag,
            Map<String, GFF3Feature> locusTagToGeneFeature,
            Map<String, String> locusTagToGene,
            Map<String, List<String>> locusTagToSynonyms,
            Map<String, List<GFF3Feature>> locusTagToPeptides) {
        this.geneToLocusTag = Collections.unmodifiableMap(geneToLocusTag);
        this.locusTagToGeneFeature = Collections.unmodifiableMap(locusTagToGeneFeature);
        this.locusTagToGene = Collections.unmodifiableMap(locusTagToGene);
        this.locusTagToSynonyms = Collections.unmodifiableMap(locusTagToSynonyms);
        this.locusTagToPeptides = Collections.unmodifiableMap(locusTagToPeptides);
    }

    /**
     * Builds a {@code LocusTagIndex} from the given features in a single pass.
     *
     * @param features the features of an annotation
     * @param ontology the ontology client used to classify feature types
     * @return a fully populated, immutable index
     */
    public static LocusTagIndex build(List<GFF3Feature> features, OntologyClient ontology) {
        Map<String, String> geneToLocusTag = new HashMap<>();
        Map<String, GFF3Feature> locusTagToGeneFeature = new HashMap<>();
        Map<String, String> locusTagToGene = new HashMap<>();
        Map<String, List<String>> locusTagToSynonyms = new HashMap<>();
        Map<String, List<GFF3Feature>> locusTagToPeptides = new HashMap<>();

        for (GFF3Feature feature : features) {
            if (feature == null) {
                continue;
            }

            Optional<String> soIdOpt = ontology.findTermByNameOrSynonym(feature.getName());
            String soId = soIdOpt.orElse(null);

            String locusTag = feature.getAttribute(GFF3Attributes.LOCUS_TAG).orElse(null);
            String geneName = feature.getAttribute(GFF3Attributes.GENE).orElse(null);

            // --- geneToLocusTag: any feature with a gene attribute ---
            if (geneName != null && locusTag != null) {
                geneToLocusTag.putIfAbsent(geneName, locusTag);
            }

            // --- locusTagToGeneFeature: gene/pseudogene features with a locus_tag ---
            if (soId != null && locusTag != null && !locusTag.isBlank()) {
                boolean isGeneType = isGeneOrPseudogene(soId, ontology);
                if (isGeneType) {
                    locusTagToGeneFeature.putIfAbsent(locusTag, feature);
                }
            }

            // --- locusTagToGene and locusTagToSynonyms: gene/CDS type features ---
            if (soId != null && locusTag != null && !locusTag.isBlank()) {
                boolean isGeneOrCds = isGeneOrCds(soId, ontology);
                if (isGeneOrCds) {
                    if (geneName != null && !geneName.isEmpty()) {
                        locusTagToGene.putIfAbsent(locusTag, geneName);
                    }
                    String synonymsRaw =
                            feature.getAttribute(GFF3Attributes.GENE_SYNONYM).orElse(null);
                    if (synonymsRaw != null && !synonymsRaw.isEmpty()) {
                        locusTagToSynonyms.computeIfAbsent(locusTag, k -> parseSynonyms(synonymsRaw));
                    }
                }
            }

            // --- locusTagToPeptides: polypeptide_region descendants ---
            if (soId != null && locusTag != null) {
                boolean isPeptide = ontology.isSelfOrDescendantOf(soId, OntologyTerm.POLYPEPTIDE_REGION.ID);
                if (isPeptide) {
                    locusTagToPeptides
                            .computeIfAbsent(locusTag, k -> new ArrayList<>())
                            .add(feature);
                }
            }
        }

        return new LocusTagIndex(
                geneToLocusTag, locusTagToGeneFeature, locusTagToGene, locusTagToSynonyms, locusTagToPeptides);
    }

    /**
     * Checks whether the SO ID represents a gene or pseudogene type.
     * Mirrors the logic in {@code GeneFeatureValidation.validateGeneLocusTagAssociation}.
     */
    private static boolean isGeneOrPseudogene(String soId, OntologyClient ontology) {
        return soId.equals(OntologyTerm.GENE.ID)
                || soId.equals(OntologyTerm.PSEUDOGENE.ID)
                || soId.equals(OntologyTerm.UNITARY_PSEUDOGENE.ID)
                || ontology.isSelfOrDescendantOf(soId, OntologyTerm.PSEUDOGENE.ID)
                || ontology.isSelfOrDescendantOf(soId, OntologyTerm.UNITARY_PSEUDOGENE.ID);
    }

    /**
     * Checks whether the SO ID represents a gene or CDS type.
     * Mirrors the logic in {@code GeneFeatureValidation.isGeneOrCds}.
     */
    private static boolean isGeneOrCds(String soId, OntologyClient ontology) {
        return soId.equals(OntologyTerm.GENE.ID)
                || soId.equals(OntologyTerm.CDS.ID)
                || soId.equals(OntologyTerm.PSEUDOGENIC_CDS.ID)
                || ontology.isSelfOrDescendantOf(soId, OntologyTerm.GENE.ID)
                || ontology.isSelfOrDescendantOf(soId, OntologyTerm.CDS.ID)
                || ontology.isSelfOrDescendantOf(soId, OntologyTerm.PSEUDOGENE.ID)
                || ontology.isSelfOrDescendantOf(soId, OntologyTerm.PSEUDOGENIC_CDS.ID);
    }

    /**
     * Parses a comma-separated synonym string into a list.
     * Mirrors the logic in {@code GeneFeatureValidation.parseSynonyms}.
     */
    private static List<String> parseSynonyms(String synonymValue) {
        if (synonymValue == null || synonymValue.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(synonymValue.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
