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
package uk.ac.ebi.embl.gff3tools.validation.fix;

import static uk.ac.ebi.embl.gff3tools.validation.meta.ValidationType.ANNOTATION;

import java.util.*;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.utils.OntologyClient;
import uk.ac.ebi.embl.gff3tools.utils.OntologyTerm;
import uk.ac.ebi.embl.gff3tools.utils.PseudoLookup;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;
import uk.ac.ebi.embl.gff3tools.validation.meta.Fix;
import uk.ac.ebi.embl.gff3tools.validation.meta.FixMethod;
import uk.ac.ebi.embl.gff3tools.validation.meta.Gff3Fix;
import uk.ac.ebi.embl.gff3tools.validation.meta.InjectContext;

@Gff3Fix(
        name = "GENE_ASSOCIATED_FEATURE_REMOVAL",
        description =
                "Removes gene features entry if locations are identical with gene associated features (CDS, RRNA, TRNA)")
public class GeneAssociatedFeatureRemoval implements Fix {

    @InjectContext
    private ValidationContext context;

    @FixMethod(
            rule = "GENE_ASSOCIATED_FEATURE_REMOVAL",
            description =
                    "Removes gene features entry if locations are identical with gene associated features (CDS, RRNA, TRNA)",
            type = ANNOTATION)
    public void fixAnnotation(GFF3Annotation gff3Annotation, int line) {
        // Build the shared CDS lookup before any gene goes, so its reasons name the genes the
        // submitter wrote. Removing a gene never changes whether a CDS is pseudo: a pseudo gene's
        // marker is copied to its children below.
        PseudoLookup.of(gff3Annotation, context);

        OntologyClient ontologyClient = context.get(OntologyClient.class);
        List<GFF3Feature> geneAssociatedFeatures = new ArrayList<>();
        Map<String, GFF3Feature> geneFeatureMap = new HashMap<>();

        for (GFF3Feature feature : gff3Annotation.getFeatures()) {
            Optional<String> soIdOpt = ontologyClient.findTermByNameOrSynonym(feature.getName());
            if (soIdOpt.isEmpty()) continue;

            String soId = soIdOpt.get();
            if (OntologyTerm.CDS.ID.equals(soId)
                    || OntologyTerm.CDS_REGION.ID.equals(soId)
                    || OntologyTerm.RRNA.ID.equals(soId)
                    || OntologyTerm.TRNA.ID.equals(soId)) {
                geneAssociatedFeatures.add(feature);
                continue;
            }

            boolean isGene = soId.equals(OntologyTerm.GENE.ID)
                    || soId.equals(OntologyTerm.PSEUDOGENE.ID)
                    || soId.equals(OntologyTerm.UNITARY_PSEUDOGENE.ID)
                    || ontologyClient.isSelfOrDescendantOf(soId, OntologyTerm.PSEUDOGENE.ID)
                    || ontologyClient.isSelfOrDescendantOf(soId, OntologyTerm.UNITARY_PSEUDOGENE.ID);

            if (isGene) {
                String locationKey = feature.getStart() + ":" + feature.getEnd();
                geneFeatureMap.put(locationKey, feature);
            }
        }

        Map<String, GFF3Feature> removedGenes = new HashMap<>();
        for (GFF3Feature geneAssociatedFeature : geneAssociatedFeatures) {
            String locationKey = geneAssociatedFeature.getStart() + ":" + geneAssociatedFeature.getEnd();
            GFF3Feature toRemove = geneFeatureMap.get(locationKey);
            if (toRemove != null) {
                toRemove.getId().ifPresent(id -> removedGenes.put(id, toRemove));
                gff3Annotation.removeFeature(toRemove);
            }
        }

        // Clear Parent reference on any feature whose parent was just removed,
        // preventing DANGLING_PARENT errors from the validator. A child of a pseudo
        // gene takes the gene's pseudo, which it would otherwise lose with the link.
        if (!removedGenes.isEmpty()) {
            PseudoLookup.Walker walker = PseudoLookup.walker(gff3Annotation, context);
            for (GFF3Feature feature : gff3Annotation.getFeatures()) {
                feature.getParentId().map(removedGenes::get).ifPresent(gene -> {
                    if (walker.isPseudo(gene)) {
                        copyPseudo(gene, feature);
                    }
                    feature.setParentId(Optional.empty());
                    feature.removeAttributeList(GFF3Attributes.ATTRIBUTE_PARENT);
                });
            }
        }
    }

    // pseudo and pseudogene are mutually exclusive, so the child gets the gene's pseudogene
    // when it has one, otherwise pseudo=true. A child that already has either keeps its own.
    private static void copyPseudo(GFF3Feature gene, GFF3Feature child) {
        if (child.hasAttribute(GFF3Attributes.PSEUDO) || child.hasAttribute(GFF3Attributes.PSEUDOGENE)) {
            return;
        }
        Optional<List<String>> pseudogene = gene.getAttributeList(GFF3Attributes.PSEUDOGENE);
        if (pseudogene.isPresent()) {
            child.setAttributeList(GFF3Attributes.PSEUDOGENE, new ArrayList<>(pseudogene.get()));
        } else {
            child.addAttribute(GFF3Attributes.PSEUDO, "true");
        }
    }
}
