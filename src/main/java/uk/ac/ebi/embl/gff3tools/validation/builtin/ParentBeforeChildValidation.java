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

import static uk.ac.ebi.embl.gff3tools.validation.meta.ValidationType.ANNOTATION;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.utils.ValidationUtils;
import uk.ac.ebi.embl.gff3tools.validation.meta.Gff3Validation;
import uk.ac.ebi.embl.gff3tools.validation.meta.Validation;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationMethod;

@Gff3Validation(name = "PARENT_BEFORE_CHILD", description = "A parent feature must be listed before its children")
public class ParentBeforeChildValidation implements Validation {

    public static final String VALIDATION_RULE = "PARENT_BEFORE_CHILD";
    private static final String OUT_OF_ORDER_MESSAGE = "Parent features must be listed before their children:%s";
    private static final String VIOLATION_MESSAGE =
            "\nFeature %s \"%s\" at %s:%s%s is listed before its Parent \"%s\"%s";

    @ValidationMethod(
            rule = VALIDATION_RULE,
            description = "A parent feature must be listed before its children",
            type = ANNOTATION)
    public void validateParentBeforeChild(GFF3Annotation annotation, int line) throws ValidationException {
        Map<String, GFF3Feature> firstLineById = getIdFeatureMap(annotation);
        Set<String> idsListedSoFar = new HashSet<>();
        Set<String> reportedChildren = new HashSet<>();
        List<String> violations = new ArrayList<>();

        for (GFF3Feature feature : annotation.getFeatures()) {
            // Empty when the Parent is already listed, or when the annotation lacks it altogether
            Optional<GFF3Feature> parentListedLater = feature.getParentId()
                    .filter(parentId -> !idsListedSoFar.contains(parentId))
                    .map(firstLineById::get);

            // A child split over several lines is reported once, at its first line out of order
            if (parentListedLater.isPresent() && reportedChildren.add(ValidationUtils.featureGroupKey(feature))) {
                violations.add(violation(feature, parentListedLater.get()));
            }
            feature.getId().ifPresent(idsListedSoFar::add);
        }

        if (!violations.isEmpty()) {
            throw new ValidationException(line, OUT_OF_ORDER_MESSAGE.formatted(String.join("", violations)));
        }
    }

    /** Each ID's first line, which is where a parent counts as listed. */
    private static Map<String, GFF3Feature> getIdFeatureMap(GFF3Annotation annotation) {
        Map<String, GFF3Feature> firstLineById = new HashMap<>();
        for (GFF3Feature feature : annotation.getFeatures()) {
            feature.getId().ifPresent(id -> firstLineById.putIfAbsent(id, feature));
        }
        return firstLineById;
    }

    private static String violation(GFF3Feature child, GFF3Feature parent) {
        return VIOLATION_MESSAGE.formatted(
                child.getName(),
                child.getId().orElse("<no ID>"),
                child.accession(),
                ValidationUtils.getLocationString(child),
                lineSuffix(child),
                parent.getId().orElseThrow(),
                lineSuffix(parent));
    }

    /** The source line, which features converted from a flat file do not have. */
    private static String lineSuffix(GFF3Feature feature) {
        return feature.getLine() >= 0 ? " (line %d)".formatted(feature.getLine()) : "";
    }
}
