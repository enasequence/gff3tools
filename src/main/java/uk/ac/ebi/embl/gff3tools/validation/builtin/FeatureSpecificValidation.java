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
import java.util.stream.Collectors;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Anthology;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.validation.AnnotationValidation;

public class FeatureSpecificValidation implements AnnotationValidation {

    public static final String VALIDATION_RULE = "GFF3_FEATURE_SPECIFIC_VALIDATION";

    private static final String SINGLE_OPERON_MESSAGE =
            "Feature \"%s\" refers to operon \"%s\". Please provide an operon feature which spans the entire operon region. Refer to (http://www.ebi.ac.uk/ena/WebFeat/operon_s.html) for details";
    private static final String MULTIPLE_OPERON_MESSAGE =
            "\"%s\" number of features refer to operon \"%s\". Please provide an operon feature which spans the entire operon region. Refer to (http://www.ebi.ac.uk/ena/WebFeat/operon_s.html) for details";

    @Override
    public String getValidationRule() {
        return VALIDATION_RULE;
    }

    @Override
    public void validateAnnotation(GFF3Annotation annotation, int line) throws ValidationException {
        validatePeptideFeature(annotation, line);
        validateOperonFeatures(annotation, line);
    }

    private void validateOperonFeatures(GFF3Annotation annotation, int line) throws ValidationException {

        boolean hasOperonFeature = false;
        List<GFF3Feature> featuresWithOperon = new ArrayList<>();

        for (GFF3Feature feature : annotation.getFeatures()) {
            if (feature.getName().equalsIgnoreCase(GFF3Anthology.OPERON_FEATURE_NAME)) {
                hasOperonFeature = true;
                break;
            }
            if (feature.isAttributeExists(GFF3Attributes.OPERON)) {
                featuresWithOperon.add(feature);
            }
        }

        if (hasOperonFeature || featuresWithOperon.isEmpty()) {
            return;
        }

        Map<String, List<GFF3Feature>> groupedByOperon = featuresWithOperon.stream()
                .filter(f -> f.getAttributeByName(GFF3Attributes.OPERON) != null)
                .collect(Collectors.groupingBy(f -> f.getAttributeByName(GFF3Attributes.OPERON)));

        for (Map.Entry<String, List<GFF3Feature>> entry : groupedByOperon.entrySet()) {
            String operonValue = entry.getKey();
            List<GFF3Feature> group = entry.getValue();

            if (group.size() == 1) {
                GFF3Feature feature = group.get(0);
                throw new ValidationException(
                        VALIDATION_RULE, line, SINGLE_OPERON_MESSAGE.formatted(feature.getName(), operonValue));
            } else {
                throw new ValidationException(
                        VALIDATION_RULE, line, MULTIPLE_OPERON_MESSAGE.formatted(group.size(), operonValue));
            }
        }
    }

    private void validatePeptideFeature(GFF3Annotation annotation, int line) throws ValidationException {
        List<GFF3Feature> cdsFeatures = new ArrayList<>();
        List<GFF3Feature> peptideFeatures = new ArrayList<>();

        for (GFF3Feature feature : annotation.getFeatures()) {
            String name = feature.getName();

            if (GFF3Anthology.CDS_EQUIVALENTS.contains(name)) {
                cdsFeatures.add(feature);
            } else if (GFF3Anthology.SIG_PEPTIDE_FEATURE_NAME.equalsIgnoreCase(name)) {
                peptideFeatures.add(feature);
            }
        }

        for (GFF3Feature cdsFeature : cdsFeatures) {
            List<GFF3Feature> relevantPeptideFeatures = new ArrayList<>();

            for (GFF3Feature peptideFeature : peptideFeatures) {

                boolean sameLocusTag = areSame(cdsFeature, peptideFeature, GFF3Attributes.LOCUS_TAG)
                        && areLocationsOnSameStrand(cdsFeature, peptideFeature);

                boolean sameGene = areSame(cdsFeature, peptideFeature, GFF3Attributes.GENE)
                        && doLocationsOverlap(cdsFeature, peptideFeature);

                if (sameLocusTag || sameGene) {
                    relevantPeptideFeatures.add(peptideFeature);
                }
            }

            if (!relevantPeptideFeatures.isEmpty()) {
                checkPseudoQualifier(cdsFeature, relevantPeptideFeatures, line);
            }
        }
    }

    private boolean doLocationsOverlap(GFF3Feature f1, GFF3Feature f2) {
        return f1.getStart() <= f2.getEnd() && f2.getStart() <= f1.getEnd();
    }

    private boolean areLocationsOnSameStrand(GFF3Feature f1, GFF3Feature f2) {
        String strand1 = f1.getStrand();
        String strand2 = f2.getStrand();

        if (".".equals(strand1) || ".".equals(strand2)) {
            return false;
        }
        return strand1.equals(strand2);
    }

    private boolean areSame(GFF3Feature f1, GFF3Feature f2, String attributeName) {
        String attr1 = f1.getAttributeByName(attributeName);
        String attr2 = f2.getAttributeByName(attributeName);

        return attr1 != null && attr1.equalsIgnoreCase(attr2);
    }

    private void checkPseudoQualifier(GFF3Feature cdsFeature, List<GFF3Feature> peptideFeatures, int line)
            throws ValidationException {
        boolean hasPseudo = cdsFeature.getAttributes().containsKey(GFF3Attributes.PSEUDO);

        if (hasPseudo) {
            for (GFF3Feature peptide : peptideFeatures) {
                if (!peptide.getAttributes().containsKey(GFF3Attributes.PSEUDO)) {
                    throw new ValidationException(
                            "Pseudo qualifier missing in peptide feature",
                            line,
                            "Peptide feature " + peptide.getName() + " must have /pseudo because CDS "
                                    + cdsFeature.getName() + " has it");
                }
            }
        }
    }
}
