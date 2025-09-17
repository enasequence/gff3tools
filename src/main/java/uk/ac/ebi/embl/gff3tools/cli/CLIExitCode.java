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
package uk.ac.ebi.embl.gff3tools.cli;

public enum CLIExitCode {
    GENERAL(1),
    // User input errors
    USAGE(2),
    UNSUPPORTED_FORMAT_CONVERSION(3),
    // IO errors
    READ_ERROR(10),
    WRITE_ERROR(11),
    NON_EXISTENT_FILE(12),
    // Validation errors
    VALIDATION_ERROR(20),
    // Runtime errors
    OUT_OF_MEMORY(30);

    private final int exitCode;

    CLIExitCode(final int code) {
        this.exitCode = code;
    }

    public int asInt() {
        return exitCode;
    }
}
