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

import java.util.*;

import uk.ac.ebi.embl.gff3tools.fftogff3.FeatureMapping;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.validation.meta.FixMethod;
import uk.ac.ebi.embl.gff3tools.validation.meta.Gff3Fix;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationType;

@Gff3Fix(
        name = "GENE_SYNONYM",
        description =
                "Normalizes gene_synonym values across features sharing the same locus_tag/gene; CDS sets the canonical list",
        enabled = true)
public class GeneSynonymFix {

    private static final String FF_GENE = "gene";
    private static final String GENE_SYNONYM = GFF3Attributes.GENE_SYNONYM;
    private HashSet<String> GENE_FEATURES = new HashSet<>();

    public GeneSynonymFix() {
        FeatureMapping.getGFF3FeatureCandidateIdsAndNames(FF_GENE).forEach(GENE_FEATURES::add);
    }

    @FixMethod(
            rule = "PUSHING_GENE_SYNONYM_ATTRIBUTE_TO_PARENT_FEATURES_ONLY",
            type = ValidationType.ANNOTATION,
            description =
                    "Pushes gene_synonym attribute to only persist at a parent level",
            enabled = true)
    public void fix(GFF3Annotation annotation, int line) {

        List<GFF3Feature> features = annotation.getFeatures();

        Map<String, GFF3Feature> featuresById = new LinkedHashMap<>();
        for (GFF3Feature f : features) {
            featuresById.put(getFeatureKey(f),f);
        }

        for (GFF3Feature f : featuresById.values()) {
            var currentId = f.getId().isPresent() ? f.getId().get() : "";
            if(f.hasAttribute(GENE_SYNONYM)
                    && !GENE_FEATURES.contains(currentId)
                    && !GENE_FEATURES.contains(f.getName()))
            {
                var parent = findGeneAncestor(f);
                if (parent==null) parent = findOldestAncestorWithSameLocation(f);

                if(!getFeatureKey(f).equals(getFeatureKey(parent))) {
                    if(!parent.hasAttribute(GENE_SYNONYM)) {
                            parent.setAttributeValueList(GENE_SYNONYM, f.getAttributeValueList(GENE_SYNONYM));
                            featuresById.put(getFeatureKey(parent), parent);
                    }

                    f.removeAttribute(GENE_SYNONYM);
                    featuresById.put(getFeatureKey(f), f);
                }
            }
        }
    }

    public String getFeatureKey(GFF3Feature feature) {
        var id = feature.getId();
        var name = feature.getName();
        return id.map(s -> s + "_" + name).orElse(name);
    }

    public GFF3Feature findGeneAncestor(GFF3Feature feature) {
        GFF3Feature current = feature;
        while (current.getParent() != null) {
            current = current.getParent();

            var currentId = current.getId().isPresent() ? current.getId().get() : "";
            if(GENE_FEATURES.contains(current.getName())
                    || GENE_FEATURES.contains(currentId)) break;
        }

        if(GENE_FEATURES.contains(current.getName())) return current;
        return null;
    }

    public GFF3Feature findOldestAncestorWithSameLocation(GFF3Feature feature) {
        GFF3Feature current = feature;
        while (current.getParent() != null
                && current.getParent().getStart() == current.getStart()
                && current.getParent().getEnd() == current.getEnd()) {
            current = current.getParent();
        }
        return current;
    }
}
