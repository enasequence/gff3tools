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

public class ExitExceptionTest {

    // Anonymous inner class to test the abstract ExitException
    private static class TestExitException extends ExitException {
        public TestExitException(String message) {
            super(message);
        }

        public TestExitException(String message, Exception cause) {
            super(message, cause);
        }

        @Override
        public CLIExitCode exitCode() {
            return CLIExitCode.GENERAL; // A generic exit code for testing
        }
    }

    @Test
    void testConstructor_messageOnly() {
        String message = "Test message";
        TestExitException exception = new TestExitException(message);
        assertEquals(message, exception.getMessage());
        assertNull(exception.getCause());
        assertEquals(CLIExitCode.GENERAL, exception.exitCode());
    }

    @Test
    void testConstructor_messageAndCause() {
        String message = "Test message with cause";
        Exception cause = new RuntimeException("Original cause");
        TestExitException exception = new TestExitException(message, cause);
        assertEquals(message, exception.getMessage());
        assertEquals(cause, exception.getCause());
        assertEquals(CLIExitCode.GENERAL, exception.exitCode());
    }

    @Test
    void testExitCode() {
        TestExitException exception = new TestExitException("Any message");
        assertEquals(CLIExitCode.GENERAL, exception.exitCode());
    }
}
