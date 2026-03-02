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

import org.junit.jupiter.api.Test;

class CircularDependencyExceptionTest {

    @Test
    void testConstructor_messageIsPreserved() {
        String message = "Circular dependency detected while resolving ProviderA";
        CircularDependencyException exception = new CircularDependencyException(message);

        assertEquals(message, exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    void testIsRuntimeException() {
        CircularDependencyException exception = new CircularDependencyException("cycle");

        assertInstanceOf(RuntimeException.class, exception);
    }
}
