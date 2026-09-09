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
package uk.ac.ebi.embl.gff3tools.validation.fixtures;

import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.validation.Parameter;
import uk.ac.ebi.embl.gff3tools.validation.ParameterType;
import uk.ac.ebi.embl.gff3tools.validation.meta.Gff3Validation;
import uk.ac.ebi.embl.gff3tools.validation.meta.RuleSeverity;
import uk.ac.ebi.embl.gff3tools.validation.meta.Validation;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationMethod;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationType;

/**
 * Top-level, real-scan test fixture exercising {@link Parameter}/{@link
 * uk.ac.ebi.embl.gff3tools.validation.Parameters} declarations. Must be top-level (not a nested
 * class) to be picked up by {@code ValidationRegistry.ScanHolder}'s classpath scan, which
 * excludes classes with {@code $} in their name.
 */
@Gff3Validation(name = "PARAM_FIXTURE")
public class ParamFixtureValidation implements Validation {

    @Parameter(
            name = "MIN_AMINO_ACIDS",
            type = ParameterType.LONG,
            description = "Minimum amino acids",
            defaultValue = "25")
    @ValidationMethod(rule = "PARAM_FIXTURE_LONG", type = ValidationType.ANNOTATION, severity = RuleSeverity.ERROR)
    public void validateLongParam(GFF3Annotation annotation, int line) {}

    @Parameter(name = "LABEL", type = ParameterType.STRING, description = "A label")
    @ValidationMethod(rule = "PARAM_FIXTURE_STRING", type = ValidationType.ANNOTATION, severity = RuleSeverity.ERROR)
    public void validateStringParam(GFF3Annotation annotation, int line) {}

    // OFF by default so this fixture's mandatory parameter does not require every other CLI
    // command test in the suite to supply it or explicitly disable the rule; tests that need to
    // prove real mandatory enforcement turn it back on explicitly via --rules=...:ERROR.
    @Parameter(
            name = "MANDATORY_LABEL",
            type = ParameterType.STRING,
            description = "A mandatory label",
            mandatory = true)
    @ValidationMethod(rule = "PARAM_FIXTURE_MANDATORY", type = ValidationType.ANNOTATION, severity = RuleSeverity.OFF)
    public void validateMandatoryParam(GFF3Annotation annotation, int line) {}
}
