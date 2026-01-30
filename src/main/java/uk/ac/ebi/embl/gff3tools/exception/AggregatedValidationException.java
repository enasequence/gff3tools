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

import java.util.List;
import uk.ac.ebi.embl.gff3tools.cli.CLIExitCode;

/**
 * Exception that aggregates multiple validation errors encountered during
 * processing when fail-fast mode is disabled (the default).
 */
public class AggregatedValidationException extends ValidationException {

    private final List<ValidationException> errors;

    public AggregatedValidationException(List<ValidationException> errors) {
        super(formatMessage(errors));
        this.errors = List.copyOf(errors);
    }

    private static String formatMessage(List<ValidationException> errors) {
        if (errors.isEmpty()) {
            return "No errors";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Processing completed with %d error(s):".formatted(errors.size()));
        int maxToShow = Math.min(3, errors.size());
        for (int i = 0; i < maxToShow; i++) {
            sb.append("\n  - ").append(errors.get(i).getMessage());
        }
        if (errors.size() > maxToShow) {
            sb.append("\n  ... and %d more".formatted(errors.size() - maxToShow));
        }
        return sb.toString();
    }

    public List<ValidationException> getErrors() {
        return errors;
    }

    public int getErrorCount() {
        return errors.size();
    }

    @Override
    public CLIExitCode exitCode() {
        return CLIExitCode.VALIDATION_ERROR;
    }
}
