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

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.gff3tools.cli.CLIExitCode;

class AggregatedValidationExceptionTest {

    @Test
    void exitCode_returnsValidationError() {
        List<ValidationException> errors = List.of(new ValidationException("RULE", 1, "test"));
        AggregatedValidationException exception = new AggregatedValidationException(errors);

        assertEquals(CLIExitCode.VALIDATION_ERROR, exception.exitCode());
    }

    @Test
    void getErrors_returnsImmutableCopy() {
        List<ValidationException> errors = new ArrayList<>();
        errors.add(new ValidationException("RULE", 1, "test"));
        AggregatedValidationException exception = new AggregatedValidationException(errors);

        assertThrows(
                UnsupportedOperationException.class,
                () -> exception.getErrors().add(new ValidationException("RULE", 2, "another")));
    }

    @Test
    void getErrorCount_returnsCorrectCount() {
        List<ValidationException> errors = List.of(
                new ValidationException("RULE1", 1, "error 1"),
                new ValidationException("RULE2", 2, "error 2"),
                new ValidationException("RULE3", 3, "error 3"));
        AggregatedValidationException exception = new AggregatedValidationException(errors);

        assertEquals(3, exception.getErrorCount());
    }

    @Test
    void getMessage_containsErrorCountAndDetails() {
        List<ValidationException> errors =
                List.of(new ValidationException("RULE1", 1, "error 1"), new ValidationException("RULE2", 2, "error 2"));
        AggregatedValidationException exception = new AggregatedValidationException(errors);

        String message = exception.getMessage();
        assertTrue(message.contains("2 error(s)"));
        assertTrue(message.contains("error 1"));
        assertTrue(message.contains("error 2"));
    }

    @Test
    void getMessage_truncatesWhenManyErrors() {
        List<ValidationException> errors = List.of(
                new ValidationException("RULE1", 1, "error 1"),
                new ValidationException("RULE2", 2, "error 2"),
                new ValidationException("RULE3", 3, "error 3"),
                new ValidationException("RULE4", 4, "error 4"),
                new ValidationException("RULE5", 5, "error 5"));
        AggregatedValidationException exception = new AggregatedValidationException(errors);

        String message = exception.getMessage();
        assertTrue(message.contains("5 error(s)"));
        assertTrue(message.contains("error 1"));
        assertTrue(message.contains("error 2"));
        assertTrue(message.contains("error 3"));
        assertFalse(message.contains("error 4"));
        assertFalse(message.contains("error 5"));
        assertTrue(message.contains("and 2 more"));
    }

    @Test
    void getErrors_preservesOriginalErrors() {
        ValidationException error1 = new ValidationException("RULE1", 1, "error 1");
        ValidationException error2 = new ValidationException("RULE2", 2, "error 2");
        List<ValidationException> errors = List.of(error1, error2);
        AggregatedValidationException exception = new AggregatedValidationException(errors);

        List<ValidationException> retrieved = exception.getErrors();
        assertEquals(2, retrieved.size());
        assertSame(error1, retrieved.get(0));
        assertSame(error2, retrieved.get(1));
    }
}
