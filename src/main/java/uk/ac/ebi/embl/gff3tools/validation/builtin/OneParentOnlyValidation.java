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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.validation.meta.Gff3Validation;
import uk.ac.ebi.embl.gff3tools.validation.meta.RuleSeverity;
import uk.ac.ebi.embl.gff3tools.validation.meta.Validation;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationMethod;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationType;

/**
 * Validates that a GFF3 feature references at most one parent.
 *
 * <p>INSDC prefers archived and exchanged data to have a single parent per feature, and ENA
 * follows that. The flat file to GFF3 conversion already splits a feature with several parents
 * into one copy per parent. A submitted GFF3, however, is read with only its first
 * {@code Parent} value taken into account, so any further parents would be silently dropped.
 * This validation guards against that, keeping gff3tools self-consistent.
 *
 * <p>Both {@code Parent=a,b} and a repeated {@code Parent=a;Parent=b} are read into the same
 * value list and are reported. A value repeated within that list names the same parent and is
 * not counted twice.
 */
@Gff3Validation(name = "ONE_PARENT_ONLY", description = "A feature may reference at most one Parent")
public class OneParentOnlyValidation implements Validation {

    public static final String VALIDATION_RULE = "ONE_PARENT_ONLY";

    private static final String MULTIPLE_PARENTS_MESSAGE =
            "Feature \"%s\" (type: %s) at %s:%d-%d references %d parents (%s), but only one Parent is allowed per feature";

    @ValidationMethod(
            rule = VALIDATION_RULE,
            description = "A feature may reference at most one Parent",
            type = ValidationType.FEATURE,
            severity = RuleSeverity.ERROR)
    public void validateOneParentOnly(GFF3Feature feature, int line) throws ValidationException {
        List<String> parentValues =
                feature.getAttributeList(GFF3Attributes.ATTRIBUTE_PARENT).orElse(List.of());
        Set<String> parents = new LinkedHashSet<>(parentValues);

        if (parents.size() > 1) {
            throw new ValidationException(
                    line,
                    MULTIPLE_PARENTS_MESSAGE.formatted(
                            feature.getId().orElse("<no ID>"),
                            feature.getName(),
                            feature.accession(),
                            feature.getStart(),
                            feature.getEnd(),
                            parents.size(),
                            String.join(", ", parents)));
        }
    }
}
