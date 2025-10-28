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
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.utils.ConversionUtils;
import uk.ac.ebi.embl.gff3tools.utils.OntologyClient;
import uk.ac.ebi.embl.gff3tools.utils.OntologyTerm;
import uk.ac.ebi.embl.gff3tools.validation.Validation;
import uk.ac.ebi.embl.gff3tools.validation.meta.Gff3Validation;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationMethod;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationType;

@Gff3Validation
public class AttributesValueValidation extends Validation {

    private static final String INVALID_FEATURE_PRODUCT_PATTERN =
            "Feature \"%s\" requires \"%s\" attributes with value matching the pattern \"%s\"";

    private static final String INVALID_ATTRIBUTE_VALUE_PATTERN =
            "Attribute \"%s\" value should match the pattern \"%s\"";

    private static final String QUALIFIER_VALUE_REQUIRED_ERROR =
            "Qualifier \"%s\" must have one of values \"%s\" when qualifier \"%s\" has value \"%s\" in any feature.";

    private static final String PROTEIN_ID_VALUE_VALIDATION = "Protein Id cannot be null or empty";

    public static final String MITOCHONDRION = "mitochondrion";
    public static final String PROVIRAL_VALUE_PATTERN = ".*endogenous retrovirus$";

    public static final Map<String, List<String>> FEATURE_PRODUCT_PATTERNS = Map.of(
            OntologyTerm.TRNA.name(),
            List.of("^(transfer RNA-)(?!synthetase).*$"),
            OntologyTerm.RRNA.name(),
            List.of(
                    "^(5.8S ribosomal RNA)$",
                    "^(12S ribosomal RNA)$",
                    "^(16S ribosomal RNA)$",
                    "^(18S ribosomal RNA)$",
                    "^(23S ribosomal RNA)$",
                    "^(28S ribosomal RNA)$"));

    private final OntologyClient ontologyClient = ConversionUtils.getOntologyClient();

    @ValidationMethod(rule = "RNA_PRODUCT_ATTRIBUTE_VALUE", type = ValidationType.FEATURE)
    public void validateAttributeValuePattern(GFF3Feature feature, int line) throws ValidationException {
        String featureName = feature.getName();
        Optional<String> soIdOpt = ontologyClient.findTermByNameOrSynonym(featureName);
        if (soIdOpt.isEmpty()) {
            return;
        }
        String soId = soIdOpt.get();
        if (OntologyTerm.RRNA.ID.equals(soId) || OntologyTerm.TRNA.ID.equals(soId)) {
            String value = feature.getAttributeByName(GFF3Attributes.PRODUCT);
            if (value != null) {
                List<String> patterns = FEATURE_PRODUCT_PATTERNS.get(featureName);

                boolean matches = patterns != null && patterns.stream().anyMatch(value::matches);

                if (!matches) {
                    throw new ValidationException(
                            line,
                            INVALID_FEATURE_PRODUCT_PATTERN.formatted(featureName, GFF3Attributes.PRODUCT, patterns));
                }
            }
        }
    }

    @ValidationMethod(rule = "PROVIRAL_ATTRIBUTE_VALUE", type = ValidationType.FEATURE)
    public void validateProviralAttribute(GFF3Feature feature, int line) throws ValidationException {
        if (feature.hasAttribute(GFF3Attributes.NOTE) && feature.hasAttribute(GFF3Attributes.PROVIRAL)) {
            String proviralValue = feature.getAttributeByName(GFF3Attributes.PROVIRAL);
            if (proviralValue != null && !proviralValue.matches(PROVIRAL_VALUE_PATTERN)) {
                throw new ValidationException(
                        line,
                        INVALID_ATTRIBUTE_VALUE_PATTERN.formatted(GFF3Attributes.PROVIRAL, PROVIRAL_VALUE_PATTERN));
            }
        }
    }

    @ValidationMethod(rule = "PROTEIN_VALUE", type = ValidationType.FEATURE)
    public void validateProteinValue(GFF3Feature feature, int line) throws ValidationException {
        if (feature.hasAttribute(GFF3Attributes.PROTEIN_ID)
                && feature.getAttributeByName(GFF3Attributes.PROTEIN_ID) == null) {
            throw new ValidationException(line, PROTEIN_ID_VALUE_VALIDATION);
        }
    }

    @ValidationMethod(rule = "12S_RRNA_MITOCHONDRION_DEPENDENCY", type = ValidationType.ANNOTATION)
    public void validateAttributeValueDependency(GFF3Annotation annotation, int line) throws ValidationException {
        boolean has12SrRNA = false;
        boolean hasMitochondrion = false;

        for (GFF3Feature feature : annotation.getFeatures()) {
            String geneValue = feature.getAttributeByName(GFF3Attributes.GENE);
            if ("12S rRNA".equalsIgnoreCase(geneValue)) {
                has12SrRNA = true;
            }

            String organelleValue = feature.getAttributeByName(GFF3Attributes.ORGANELLE);
            if (MITOCHONDRION.equalsIgnoreCase(organelleValue)) {
                hasMitochondrion = true;
            }

            if (has12SrRNA && hasMitochondrion) {
                break;
            }
        }

        if (has12SrRNA && !hasMitochondrion) {
            throw new ValidationException(
                    line,
                    QUALIFIER_VALUE_REQUIRED_ERROR.formatted(
                            GFF3Attributes.ORGANELLE, MITOCHONDRION, GFF3Attributes.GENE, "12S rRNA"));
        }
    }
}
