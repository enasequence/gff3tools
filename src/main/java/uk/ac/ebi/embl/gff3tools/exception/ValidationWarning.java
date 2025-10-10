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
package uk.ac.ebi.embl.gff3tools.exception;

import uk.ac.ebi.embl.gff3tools.cli.CLIExitCode;

public class ValidationWarning extends Exception {

    private int line;
    private String rule;

    public ValidationWarning(String rule, String message) {
        super("Violation of rule %s: %s".formatted(rule.toString(), message));
        this.rule = rule;
    }

    public ValidationWarning(String rule, int line, String message) {
        super("Violation of rule %s on line %d: %s".formatted(rule.toString(), line, message));
        this.line = line;
        this.rule = rule;
    }

    public ValidationWarning(String message) {
        super(message);
    }

    public int getLine() {
        return line;
    }

    public String getValidationRule() {
        return this.rule;
    }


}
