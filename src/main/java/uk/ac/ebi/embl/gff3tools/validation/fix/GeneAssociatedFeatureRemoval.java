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
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.utils.ConversionUtils;
import uk.ac.ebi.embl.gff3tools.utils.OntologyClient;
import uk.ac.ebi.embl.gff3tools.utils.OntologyTerm;
import uk.ac.ebi.embl.gff3tools.validation.meta.FixMethod;
import uk.ac.ebi.embl.gff3tools.validation.meta.Gff3Fix;

@Gff3Fix(
        name = "GENE_ASSOCIATED_FEATURE_REMOVAL",
        description =
                "Removes gene features entry if locations are identical with gene associated features (CDS, RRNA, TRNA)")
public class GeneAssociatedFeatureRemoval {

    private final OntologyClient ontologyClient = ConversionUtils.getOntologyClient();

    @FixMethod(
            rule = "GENE_ASSOCIATED_FEATURE_REMOVAL",
            description =
                    "Removes gene features entry if locations are identical with gene associated features (CDS, RRNA, TRNA)",
            type = ANNOTATION)
    public void fixAnnotation(GFF3Annotation gff3Annotation, int line) {
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

        for (GFF3Feature geneAssociatedFeature : geneAssociatedFeatures) {
            String locationKey = geneAssociatedFeature.getStart() + ":" + geneAssociatedFeature.getEnd();
            GFF3Feature toRemove = geneFeatureMap.get(locationKey);
            if (toRemove != null) {
                gff3Annotation.removeFeature(toRemove);
            }
        }
    }
}
