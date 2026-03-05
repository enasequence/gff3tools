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

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ValidationContextTest {

    private ValidationContext context;

    @BeforeEach
    void setUp() {
        context = new ValidationContext();
    }

    // ---------------------------------------------------------------
    // Helper providers used by the tests
    // ---------------------------------------------------------------

    static class CachingProvider implements ContextProvider<String> {
        private String cached;

        @Override
        public String get(ValidationContext context) {
            if (cached == null) {
                // Produce a new instance each time (not interned) to enable identity checks
                cached = new String("value-" + System.nanoTime());
            }
            return cached;
        }

        @Override
        public Class<String> type() {
            return String.class;
        }
    }

    static class ObjectProvider implements ContextProvider<Object> {
        private Object cached;

        @Override
        public Object get(ValidationContext context) {
            if (cached == null) {
                cached = new Object();
            }
            return cached;
        }

        @Override
        public Class<Object> type() {
            return Object.class;
        }
    }

    /** Provider that depends on another provider without forming a cycle. */
    static class DependentProvider implements ContextProvider<String> {
        private String cached;

        @Override
        public String get(ValidationContext context) {
            if (cached == null) {
                Object globalValue = context.get(Object.class);
                cached = "dependent-on-" + globalValue;
            }
            return cached;
        }

        @Override
        public Class<String> type() {
            return String.class;
        }
    }

    /** Provider whose get() throws a RuntimeException, used to verify context remains usable. */
    static class FailingProvider implements ContextProvider<String> {
        @Override
        public String get(ValidationContext context) {
            throw new RuntimeException("provider computation failed");
        }

        @Override
        public Class<String> type() {
            return String.class;
        }
    }

    // ---------------------------------------------------------------
    // SC2: get() returns same cached instance on repeated calls
    // ---------------------------------------------------------------

    @Test
    @DisplayName("SC2: get() returns the same cached instance on repeated calls (assertSame)")
    void get_returnsCachedInstance() {
        context.register(String.class, new CachingProvider());

        String first = context.get(String.class);
        String second = context.get(String.class);

        assertSame(first, second, "Repeated get() calls must return the same cached instance");
    }

    @Test
    @DisplayName("SC2: ObjectProvider returns same cached instance on repeated calls")
    void get_objectProvider_returnsCachedInstance() {
        context.register(Object.class, new ObjectProvider());

        Object first = context.get(Object.class);
        Object second = context.get(Object.class);

        assertSame(first, second, "Provider must return the same cached instance");
    }

    // ---------------------------------------------------------------
    // Additional edge-case tests
    // ---------------------------------------------------------------

    @Test
    @DisplayName("get() for unregistered provider throws IllegalArgumentException")
    void get_unregisteredProvider_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> context.get(String.class));
    }

    @Test
    @DisplayName("register() replaces an existing provider")
    void register_replacesExistingProvider() {
        CachingProvider first = new CachingProvider();
        CachingProvider second = new CachingProvider();

        context.register(String.class, first);
        String valueFromFirst = context.get(String.class);

        context.register(String.class, second);
        String valueFromSecond = context.get(String.class);

        assertNotSame(valueFromFirst, valueFromSecond, "After replacing a provider, get() should use the new provider");
    }

    @Test
    @DisplayName("Provider can depend on another provider")
    void get_nonCircularDependency_resolves() {
        context.register(Object.class, new ObjectProvider());
        context.register(String.class, new DependentProvider());

        String result = context.get(String.class);

        assertNotNull(result);
        assertTrue(result.startsWith("dependent-on-"), "Dependent provider should resolve its dependency");
    }

    @Test
    @DisplayName("Context remains usable after a provider throws")
    void get_providerThrows_contextRemainsUsable() {
        context.register(String.class, new FailingProvider());
        context.register(Object.class, new ObjectProvider());

        assertThrows(RuntimeException.class, () -> context.get(String.class));

        assertDoesNotThrow(() -> context.get(Object.class), "Context must be usable after a provider throws");
    }
}
