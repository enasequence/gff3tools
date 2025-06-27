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
package uk.ac.ebi.embl.converter.exception;

import uk.ac.ebi.embl.converter.cli.CLIExitCode;
import uk.ac.ebi.embl.converter.validation.ValidationRule;

public class ValidationException extends ExitException {

    private int line;

    public ValidationException(ValidationRule rule, String message) {
        super("Violation of rule %s: %s (%s)".formatted(rule.toString(), rule.getDescription(), message));
    }

    public ValidationException(ValidationRule rule, int line, String message) {
        super("Violation of rule %s on line %d: %s (%s)"
                .formatted(rule.toString(), line, rule.getDescription(), message));
        this.line = line;
    }

    public int getLine() {
        return line;
    }

    @Override
    public CLIExitCode exitCode() {
        return CLIExitCode.VALIDATION_ERROR;
    }
}
