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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ContextDiscoveryIntegrationTest {

    // ---------------------------------------------------------------
    // Test providers (not on the classpath as real providers -- inner classes
    // are excluded by the "$" filter in discoverProviders())
    // ---------------------------------------------------------------

    static class TestValueProvider implements ContextProvider<String> {
        private String cached;
        private final String value;

        TestValueProvider() {
            this("auto-discovered");
        }

        TestValueProvider(String value) {
            this.value = value;
        }

        @Override
        public String get(ValidationContext context) {
            if (cached == null) {
                cached = value;
            }
            return cached;
        }

        @Override
        public void invalidate() {
            cached = null;
        }

        @Override
        public ProviderScope scope() {
            return ProviderScope.GLOBAL;
        }
    }

    // ---------------------------------------------------------------
    // SC6: withProvider() replaces auto-discovered provider
    // ---------------------------------------------------------------

    @Test
    @DisplayName("SC6: withProvider() replaces auto-discovered provider of the same class")
    void withProvider_replacesAutoDiscovered() {
        // Given: A builder with an override provider
        TestValueProvider override = new TestValueProvider("overridden");

        ValidationEngine engine =
                new ValidationEngineBuilder().withProvider(override).build();

        // When: We retrieve the provider from the engine's context
        ValidationContext context = engine.getContext();
        String value = context.get(TestValueProvider.class);

        // Then: The override value is used, not the auto-discovered default
        assertEquals("overridden", value);
    }

    // ---------------------------------------------------------------
    // Context injection into validator instances
    // ---------------------------------------------------------------

    @Test
    @DisplayName("ValidationContext is injected into validator instances during build")
    void build_injectsContextIntoValidators() {
        // Given: A builder
        ValidationEngine engine = new ValidationEngineBuilder().build();

        // Then: The engine has a non-null context
        assertNotNull(engine.getContext(), "Engine must have a non-null ValidationContext after build");
    }

    // ---------------------------------------------------------------
    // Multiple builds produce independent contexts
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Each build() produces an independent ValidationContext")
    void multipleBuild_producesIndependentContexts() {
        ValidationEngineBuilder builder = new ValidationEngineBuilder();

        ValidationEngine engine1 = builder.build();
        ValidationEngine engine2 = builder.build();

        assertNotSame(
                engine1.getContext(),
                engine2.getContext(),
                "Each build() must create a new, independent ValidationContext");
    }

    // ---------------------------------------------------------------
    // withProvider() is additive across multiple calls
    // ---------------------------------------------------------------

    @Test
    @DisplayName("withProvider() can be called multiple times for different providers")
    void withProvider_multipleProviders() {
        // A second provider type
        class AnotherProvider implements ContextProvider<Integer> {
            @Override
            public Integer get(ValidationContext context) {
                return 42;
            }

            @Override
            public void invalidate() {}

            @Override
            public ProviderScope scope() {
                return ProviderScope.GLOBAL;
            }
        }

        ValidationEngine engine = new ValidationEngineBuilder()
                .withProvider(new TestValueProvider("custom"))
                .withProvider(new AnotherProvider())
                .build();

        ValidationContext context = engine.getContext();
        assertEquals("custom", context.get(TestValueProvider.class));
        assertEquals(42, context.get(AnotherProvider.class));
    }

    // ---------------------------------------------------------------
    // Builder method chaining
    // ---------------------------------------------------------------

    @Test
    @DisplayName("withProvider() returns the builder for fluent chaining")
    void withProvider_returnsSameBuilder() {
        ValidationEngineBuilder builder = new ValidationEngineBuilder();
        ValidationEngineBuilder returned = builder.withProvider(new TestValueProvider());

        assertSame(builder, returned, "withProvider() must return the same builder instance");
    }
}
