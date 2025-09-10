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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.converter.cli.CLIExitCode;

public class ValidationExceptionTest {

    @Test
    void testConstructor_ruleAndMessageOnly() {
        String rule = "GFF3_INVALID_RECORD";
        String message = "Invalid format detected.";
        ValidationException exception = new ValidationException(rule, message);

        assertEquals("Violation of rule GFF3_INVALID_RECORD: %s".formatted(message), exception.getMessage());
        assertEquals(rule, exception.getValidationRule());
        assertEquals(0, exception.getLine()); // Default line is 0
        assertEquals(CLIExitCode.VALIDATION_ERROR, exception.exitCode());
        assertNull(exception.getCause());
    }

    @Test
    void testConstructor_ruleLineAndMessage() {
        String rule = "FLATFILE_NO_SOURCE";
        int line = 10;
        String message = "No source feature found.";
        ValidationException exception = new ValidationException(rule, line, message);

        assertEquals("Violation of rule FLATFILE_NO_SOURCE on line 10: %s".formatted(message), exception.getMessage());
        assertEquals(rule, exception.getValidationRule());
        assertEquals(line, exception.getLine());
        assertEquals(CLIExitCode.VALIDATION_ERROR, exception.exitCode());
        assertNull(exception.getCause());
    }

    @Test
    void testExitCode() {
        ValidationException exception = new ValidationException("GFF3_INVALID_RECORD", "Any message");
        assertEquals(CLIExitCode.VALIDATION_ERROR, exception.exitCode());
    }
}
