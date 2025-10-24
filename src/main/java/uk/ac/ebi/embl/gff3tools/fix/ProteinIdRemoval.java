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
package uk.ac.ebi.embl.gff3tools.fix;

import static uk.ac.ebi.embl.gff3tools.validation.meta.ValidationType.ANNOTATION;

import java.util.*;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.validation.meta.FixMethod;
import uk.ac.ebi.embl.gff3tools.validation.meta.Gff3Fix;

@Gff3Fix
public class ProteinIdRemoval {

    @FixMethod(type = ANNOTATION)
    public void fixAnnotation(GFF3Annotation gff3Annotation) {
        List<GFF3Feature> updatedFeatures = new ArrayList<>();
        for (GFF3Feature feature : gff3Annotation.getFeatures()) {
            String proteinId = feature.getAttributeByName(GFF3Attributes.PROTEIN_ID);
            if (proteinId == null) {
                updatedFeatures.add(feature);
                continue;
            }
            feature.removeAttribute(GFF3Attributes.PROTEIN_ID);
            updatedFeatures.add(feature);
        }
        gff3Annotation.setFeatures(updatedFeatures);
    }
}
