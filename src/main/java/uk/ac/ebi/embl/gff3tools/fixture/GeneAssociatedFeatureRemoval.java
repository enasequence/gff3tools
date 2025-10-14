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
package uk.ac.ebi.embl.gff3tools.fixture;

import java.util.ArrayList;
import java.util.List;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Anthology;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;

public class GeneAssociatedFeatureRemoval implements AnnotationFix {

    @Override
    public GFF3Annotation fixAnnotation(GFF3Annotation gff3Annotation) {
        List<GFF3Feature> geneAssociatedFeatures = new ArrayList<>();
        List<GFF3Feature> geneFeatures = new ArrayList<>();

        for (GFF3Feature feature : gff3Annotation.getFeatures()) {
            if (GFF3Anthology.CDS_EQUIVALENTS.contains(feature.getName())
                    || GFF3Anthology.T_RNA_EQUIVALENTS.contains(feature.getName())
                    || GFF3Anthology.R_RNA_EQUIVALENTS.contains(feature.getName())) {
                geneAssociatedFeatures.add(feature);
            }
            if (GFF3Anthology.GENE_EQUIVALENTS.contains(feature.getName())) {
                geneFeatures.add(feature);
            }
        }
        for (GFF3Feature geneAssociatedFeature : geneAssociatedFeatures) {
            for (GFF3Feature geneFeature : geneFeatures) {
                if (geneAssociatedFeature.getStart() == geneFeature.getStart()
                        && geneAssociatedFeature.getEnd() == geneFeature.getEnd()) {
                    gff3Annotation.removeFeature(geneFeature);
                }
            }
        }
        return gff3Annotation;
    }
}
