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

import java.util.HashSet;
import java.util.Set;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.validation.Validation;
import uk.ac.ebi.embl.gff3tools.validation.meta.Gff3Validation;
import uk.ac.ebi.embl.gff3tools.validation.meta.RuleSeverity;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationMethod;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationType;

/**
 * Validates that all Parent references in GFF3 features resolve to features
 * present in the same annotation block.
 *
 * <p>In GFF3 format, the "###" directive signals that all features before it
 * have been fully resolved, allowing streaming parsers to flush their state.
 * If a feature references a Parent that was defined before a "###" directive,
 * the parent-child relationship cannot be properly resolved during conversion.
 *
 * <p>This commonly occurs when:
 * <ul>
 *   <li>The GFF3 file has interleaved features separated by "###" directives,
 *       where child features appear in a different block than their parent.</li>
 *   <li>The parent feature is missing from the file entirely.</li>
 * </ul>
 *
 * <p>When this validation triggers, features with dangling parent references
 * will not inherit the /gene qualifier from their parent hierarchy.
 */
@Gff3Validation(name = "DANGLING_PARENT")
public class DanglingParentValidation extends Validation {

    public static final String VALIDATION_RULE = "DANGLING_PARENT";

    @ValidationMethod(rule = VALIDATION_RULE, type = ValidationType.ANNOTATION, severity = RuleSeverity.ERROR)
    public void validateAnnotation(GFF3Annotation annotation, int line) throws ValidationException {
        // Build set of all IDs in this annotation block
        Set<String> availableIds = new HashSet<>();
        for (GFF3Feature feature : annotation.getFeatures()) {
            feature.getId().ifPresent(availableIds::add);
        }

        // Check each feature's Parent reference
        for (GFF3Feature feature : annotation.getFeatures()) {
            if (feature.getParentId().isPresent()) {
                String parentId = feature.getParentId().get();
                if (!availableIds.contains(parentId)) {
                    String featureId = feature.getId().orElse("<no ID>");
                    throw new ValidationException(VALIDATION_RULE, buildErrorMessage(feature, featureId, parentId));
                }
            }
        }
    }

    private String buildErrorMessage(GFF3Feature feature, String featureId, String parentId) {
        return String.format(
                "Feature \"%s\" (type: %s) at %s:%d-%d references Parent \"%s\" which is not present in the current annotation block separated by \\\"###\\\" directives",
                featureId, feature.getName(), feature.accession(), feature.getStart(), feature.getEnd(), parentId);
    }
}
