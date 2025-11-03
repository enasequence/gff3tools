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
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.validation.Validation;
import uk.ac.ebi.embl.gff3tools.validation.meta.Gff3Validation;
import uk.ac.ebi.embl.gff3tools.validation.meta.RuleSeverity;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationMethod;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationType;

@Gff3Validation(name = "ATTRIBUTES_RELATION")
public class AttributesRelationValidation extends Validation {

    private static final String ERROR_MUTUALLY_EXCLUSIVE_BY_VALUE =
            "Attribute \"%s\" must not exist when qualifier \"%s\" has value \"%s\"";

    private static final String REQUIRED_ATTRIBUTES_MISSING_IN_ANNOTATION =
            "\"%s\" attribute is required when \"%s\" present in any of the feature";
    private static final String REQUIRED_ATTRIBUTES_MISSING_IN_FEATURE =
            "\"%s\" attribute is required when \"%s\" present in the feature";

    private static final String EXCLUSIVE_ATTRIBUTES_SAME_VALUE =
            "Attributes \"%s\" and \"%s\" cannot have the same value";

    private static final String ERROR_MUTUALLY_EXCLUSIVE =
            "Attributes \"%s\" and \"%s\" cannot both exist in the same feature";

    private static final String ERROR_PSEUDO_PRODUCT_CONFLICT =
            "Feature annotated with \"%s\" should not contain \"%s\". "
                    + "Please move the \"%s\" attribute value into \"%s\" or add a comment for a curator.";

    private static final Map<String, Set<String>> EXCLUSIVE_ATTRIBUTES =
            Map.of("clone", Set.of("sub_clone", "clone_lib"));

    public static final String MITOCHONDRION = "mitochondrion";
    public static final String PROVIRAL_VALUE_PATTERN = ".*endogenous retrovirus$";

    private static final Map<String, Set<String>> MUTUALLY_EXCLUSIVE = Map.of(
            GFF3Attributes.PROVIRAL, Set.of(GFF3Attributes.VIRION),
            GFF3Attributes.REARRANGED, Set.of(GFF3Attributes.GERM_LINE),
            GFF3Attributes.ENVIRONMENTAL_SAMPLE,
                    Set.of(
                            GFF3Attributes.STRAIN,
                            GFF3Attributes.CULTIVAR,
                            GFF3Attributes.VARIETY,
                            GFF3Attributes.CULTURE_COLLECTION,
                            GFF3Attributes.SPECIMEN_VOUCHER),
            GFF3Attributes.PSEUDO, Set.of(GFF3Attributes.PSEUDOGENE, GFF3Attributes.PRODUCT),
            GFF3Attributes.PSEUDOGENE, Set.of(GFF3Attributes.PRODUCT),
            GFF3Attributes.CULTIVAR, Set.of(GFF3Attributes.VARIETY));

    private static final Map<String, Set<String>> MUTUALLY_REQUIRED = Map.of(
            GFF3Attributes.MAP, Set.of(GFF3Attributes.CHROMOSOME, GFF3Attributes.SEGMENT, GFF3Attributes.ORGANELLE),
            GFF3Attributes.SUB_CLONE, Set.of(GFF3Attributes.CLONE),
            GFF3Attributes.SUB_STRAIN, Set.of(GFF3Attributes.STRAIN),
            GFF3Attributes.ENVIRONMENTAL_SAMPLE, Set.of(GFF3Attributes.ISOLATION_SOURCE),
            GFF3Attributes.OLD_LOCUS_TAG, Set.of(GFF3Attributes.LOCUS_TAG),
            GFF3Attributes.ORGANISM, Set.of(GFF3Attributes.ENVIRONMENTAL_SAMPLE));

    // Map<DisallowedAttribute, Map<ConditionAttribute, Set<AttributeValues>>>
    private static final Map<String, Map<String, Set<String>>> MUTUALLY_EXCLUSIVE_BY_VALUE = Map.of(
            GFF3Attributes.ORGANELLE, Map.of(GFF3Attributes.GENE, Set.of("5.8S rRNA", "18S rRNA", "28S rRNA")),
            GFF3Attributes.GERM_LINE, Map.of(GFF3Attributes.MOL_TYPE, Set.of("mRNA")),
            GFF3Attributes.MACRO_NUCLEAR, Map.of(GFF3Attributes.ORGANELLE, Set.of(MITOCHONDRION)));

    @ValidationMethod(rule = "EXCLUSIVE_ATTRIBUTES", type = ValidationType.FEATURE)
    public void validateExclusiveAttributes(GFF3Feature feature, int line) throws ValidationException {
        for (Map.Entry<String, Set<String>> entry : EXCLUSIVE_ATTRIBUTES.entrySet()) {
            String key = entry.getKey();
            String keyValue = feature.getAttributeByName(key);

            if (keyValue == null) continue;

            for (String other : entry.getValue()) {
                String otherValue = feature.getAttributeByName(other);

                if (keyValue.equals(otherValue)) {
                    throw new ValidationException(line, EXCLUSIVE_ATTRIBUTES_SAME_VALUE.formatted(key, other));
                }
            }
        }
    }

    @ValidationMethod(rule = "REQUIRED_ATTRIBUTES", type = ValidationType.ANNOTATION)
    public void validateRequiredAttributes(GFF3Annotation annotation, int line) throws ValidationException {
        Set<String> presentQualifiers = annotation.getFeatures().stream()
                .flatMap(f -> f.getAttributes().keySet().stream())
                .collect(Collectors.toSet());

        if (presentQualifiers.contains(GFF3Attributes.SATELLITE)) {
            if (!presentQualifiers.contains(GFF3Attributes.MAP)) {
                throw new ValidationException(
                        line,
                        REQUIRED_ATTRIBUTES_MISSING_IN_ANNOTATION.formatted(
                                GFF3Attributes.MAP, GFF3Attributes.SATELLITE));
            }
            if (!presentQualifiers.contains(GFF3Attributes.PCR_PRIMERS)) {
                throw new ValidationException(
                        line,
                        REQUIRED_ATTRIBUTES_MISSING_IN_ANNOTATION.formatted(
                                GFF3Attributes.PCR_PRIMERS, GFF3Attributes.SATELLITE));
            }
        }
    }

    @ValidationMethod(rule = "MUTUALLY_REQUIRED_ATTRIBUTES", type = ValidationType.FEATURE)
    public void validateMutuallyRequiredAttributes(GFF3Feature feature, int line) throws ValidationException {

        for (Map.Entry<String, Set<String>> entry : MUTUALLY_REQUIRED.entrySet()) {
            String presentQualifier = entry.getKey();
            Set<String> requiredQualifiers = entry.getValue();

            if (feature.hasAttribute(presentQualifier)) {
                for (String required : requiredQualifiers) {
                    if (!feature.hasAttribute(required)) {
                        throw new ValidationException(
                                line, REQUIRED_ATTRIBUTES_MISSING_IN_FEATURE.formatted(required, presentQualifier));
                    }
                }
            }
        }
    }

    @ValidationMethod(
            rule = "MUTUALLY_EXCLUSIVE_ATTRIBUTES",
            type = ValidationType.FEATURE,
            severity = RuleSeverity.WARN)
    public void validateMutuallyExclusiveAttributes(GFF3Feature feature, int line) throws ValidationException {
        Set<String> featureAttributeKeys = new HashSet<>(feature.getAttributes().keySet());
        featureAttributeKeys.retainAll(MUTUALLY_EXCLUSIVE.keySet());

        if (featureAttributeKeys.isEmpty()) {
            return;
        }

        for (String key : featureAttributeKeys) {

            for (String value : MUTUALLY_EXCLUSIVE.get(key)) {
                if (feature.hasAttribute(value)) {
                    if (GFF3Attributes.PSEUDO.equals(key) || GFF3Attributes.PSEUDOGENE.equals(key)) {
                        throw new ValidationException(
                                line, ERROR_PSEUDO_PRODUCT_CONFLICT.formatted(key, value, value, GFF3Attributes.NOTE));
                    } else {
                        throw new ValidationException(line, ERROR_MUTUALLY_EXCLUSIVE.formatted(key, value));
                    }
                }
            }
        }
    }

    @ValidationMethod(rule = "MUTUALLY_EXCLUSIVE_ATTRIBUTES_VALUE", type = ValidationType.FEATURE)
    public void validateMutuallyExclusiveAttributesByValue(GFF3Feature feature, int line) throws ValidationException {
        for (Map.Entry<String, Map<String, Set<String>>> entry : MUTUALLY_EXCLUSIVE_BY_VALUE.entrySet()) {
            String disallowedQualifier = entry.getKey();
            Map<String, Set<String>> conditions = entry.getValue();

            if (feature.hasAttribute(disallowedQualifier)) {
                for (Map.Entry<String, Set<String>> condition : conditions.entrySet()) {
                    String conditionQualifier = condition.getKey();
                    Set<String> disallowedValues = condition.getValue();

                    String actualValue = feature.getAttributeByName(conditionQualifier);
                    if (actualValue != null && disallowedValues.contains(actualValue)) {
                        throw new ValidationException(
                                line,
                                ERROR_MUTUALLY_EXCLUSIVE_BY_VALUE.formatted(
                                        disallowedQualifier, conditionQualifier, actualValue));
                    }
                }
            }
        }
    }
}
