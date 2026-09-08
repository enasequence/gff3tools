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
package uk.ac.ebi.embl.gff3tools.validation;

/**
 * Thrown by {@link ParameterProvider}'s raw-map constructor when the supplied {@code --params}
 * map fails one of the fail-fast checks (unknown key, key for an OFF rule/fix, missing
 * mandatory, or bad type). Checked, so caller-side code (CLI/library) must handle it before the
 * validation engine is built.
 */
public class ParameterResolutionException extends Exception {
    public ParameterResolutionException(String message) {
        super(message);
    }
}
