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
import uk.ac.ebi.embl.gff3tools.validation.meta.Fix;
import uk.ac.ebi.embl.gff3tools.validation.meta.FixMethod;
import uk.ac.ebi.embl.gff3tools.validation.meta.Gff3Fix;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationType;

/**
 * Top-level, real-scan test fixture exercising {@link Parameter} declarations on a
 * {@code @FixMethod}, for fix-enable effective-state checks. Must be top-level (not nested) to be
 * picked up by {@code ValidationRegistry.ScanHolder}'s classpath scan.
 */
@Gff3Fix(name = "PARAM_FIXTURE_FIX")
public class ParamFixtureFix implements Fix {

    @Parameter(name = "THRESHOLD", type = ParameterType.LONG, description = "unused", defaultValue = "1")
    @FixMethod(rule = "PARAM_FIXTURE_FIX_RULE", type = ValidationType.ANNOTATION, enabled = true)
    public void fix(GFF3Annotation annotation, int line) {}
}
