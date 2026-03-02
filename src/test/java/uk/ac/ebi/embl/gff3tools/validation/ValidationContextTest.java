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
import uk.ac.ebi.embl.gff3tools.exception.CircularDependencyException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;

class ValidationContextTest {

    private ValidationContext context;

    @BeforeEach
    void setUp() {
        context = new ValidationContext();
    }

    // ---------------------------------------------------------------
    // Helper providers used by the tests
    // ---------------------------------------------------------------

    /** A simple ANNOTATION-scoped provider that produces a new String on each computation. */
    static class AnnotationScopedProvider implements ContextProvider<String> {
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
        public void invalidate() {
            cached = null;
        }

        @Override
        public ProviderScope scope() {
            return ProviderScope.ANNOTATION;
        }
    }

    /** A simple GLOBAL-scoped provider that produces a new Object on each computation. */
    static class GlobalScopedProvider implements ContextProvider<Object> {
        private Object cached;

        @Override
        public Object get(ValidationContext context) {
            if (cached == null) {
                cached = new Object();
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

    /** Provider A depends on Provider B, forming a potential cycle. */
    static class ProviderA implements ContextProvider<String> {
        @Override
        public String get(ValidationContext context) {
            // Attempt to resolve ProviderB during our own resolution
            return "A->" + context.get(ProviderB.class);
        }

        @Override
        public void invalidate() {}

        @Override
        public ProviderScope scope() {
            return ProviderScope.GLOBAL;
        }
    }

    /** Provider B depends on Provider A, completing the cycle. */
    static class ProviderB implements ContextProvider<String> {
        @Override
        public String get(ValidationContext context) {
            // Attempt to resolve ProviderA during our own resolution
            return "B->" + context.get(ProviderA.class);
        }

        @Override
        public void invalidate() {}

        @Override
        public ProviderScope scope() {
            return ProviderScope.GLOBAL;
        }
    }

    /** Provider that depends on another provider without forming a cycle. */
    static class DependentProvider implements ContextProvider<String> {
        private String cached;

        @Override
        public String get(ValidationContext context) {
            if (cached == null) {
                Object globalValue = context.get(GlobalScopedProvider.class);
                cached = "dependent-on-" + globalValue;
            }
            return cached;
        }

        @Override
        public void invalidate() {
            cached = null;
        }

        @Override
        public ProviderScope scope() {
            return ProviderScope.ANNOTATION;
        }
    }

    /** Provider whose get() throws a RuntimeException, used to verify resolving-set cleanup. */
    static class FailingProvider implements ContextProvider<String> {
        @Override
        public String get(ValidationContext context) {
            throw new RuntimeException("provider computation failed");
        }

        @Override
        public void invalidate() {}

        @Override
        public ProviderScope scope() {
            return ProviderScope.GLOBAL;
        }
    }

    // ---------------------------------------------------------------
    // SC2: get() returns same cached instance on repeated calls
    // ---------------------------------------------------------------

    @Test
    @DisplayName("SC2: get() returns the same cached instance on repeated calls (assertSame)")
    void get_returnsCachedInstance() {
        context.register(AnnotationScopedProvider.class, new AnnotationScopedProvider());

        String first = context.get(AnnotationScopedProvider.class);
        String second = context.get(AnnotationScopedProvider.class);

        assertSame(first, second, "Repeated get() calls must return the same cached instance");
    }

    @Test
    @DisplayName("SC2: GLOBAL provider returns same cached instance on repeated calls")
    void get_globalProvider_returnsCachedInstance() {
        context.register(GlobalScopedProvider.class, new GlobalScopedProvider());

        Object first = context.get(GlobalScopedProvider.class);
        Object second = context.get(GlobalScopedProvider.class);

        assertSame(first, second, "GLOBAL provider must return the same cached instance");
    }

    // ---------------------------------------------------------------
    // SC3: setCurrentAnnotation() causes ANNOTATION providers to recompute
    // ---------------------------------------------------------------

    @Test
    @DisplayName("SC3: setCurrentAnnotation() causes ANNOTATION-scoped providers to recompute")
    void setCurrentAnnotation_invalidatesAnnotationProviders() {
        context.register(AnnotationScopedProvider.class, new AnnotationScopedProvider());

        String before = context.get(AnnotationScopedProvider.class);

        context.setCurrentAnnotation(new GFF3Annotation());

        String after = context.get(AnnotationScopedProvider.class);

        assertNotSame(before, after, "ANNOTATION provider must recompute after setCurrentAnnotation()");
    }

    @Test
    @DisplayName("SC3: setCurrentAnnotation() stores the annotation and is retrievable")
    void setCurrentAnnotation_storesAnnotation() {
        GFF3Annotation annotation = new GFF3Annotation();
        context.setCurrentAnnotation(annotation);

        assertSame(annotation, context.getCurrentAnnotation());
    }

    // ---------------------------------------------------------------
    // SC4: GLOBAL providers survive invalidate(ANNOTATION)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("SC4: GLOBAL providers survive invalidate(ANNOTATION)")
    void invalidateAnnotation_doesNotAffectGlobalProviders() {
        context.register(GlobalScopedProvider.class, new GlobalScopedProvider());
        context.register(AnnotationScopedProvider.class, new AnnotationScopedProvider());

        Object globalBefore = context.get(GlobalScopedProvider.class);
        String annotationBefore = context.get(AnnotationScopedProvider.class);

        // Trigger ANNOTATION-scope invalidation
        context.setCurrentAnnotation(new GFF3Annotation());

        Object globalAfter = context.get(GlobalScopedProvider.class);
        String annotationAfter = context.get(AnnotationScopedProvider.class);

        assertSame(globalBefore, globalAfter, "GLOBAL provider must survive ANNOTATION invalidation");
        assertNotSame(
                annotationBefore, annotationAfter, "ANNOTATION provider must recompute after ANNOTATION invalidation");
    }

    @Test
    @DisplayName("SC4: invalidate(GLOBAL) does not affect ANNOTATION providers (already cached)")
    void invalidateGlobal_doesNotAffectAnnotationProviders() {
        context.register(AnnotationScopedProvider.class, new AnnotationScopedProvider());

        String before = context.get(AnnotationScopedProvider.class);

        context.invalidate(ProviderScope.GLOBAL);

        String after = context.get(AnnotationScopedProvider.class);

        assertSame(before, after, "ANNOTATION provider must not be affected by GLOBAL invalidation");
    }

    // ---------------------------------------------------------------
    // SC5: Circular dependencies throw CircularDependencyException
    // ---------------------------------------------------------------

    @Test
    @DisplayName("SC5: Circular dependency between two providers throws CircularDependencyException")
    void get_circularDependency_throwsException() {
        context.register(ProviderA.class, new ProviderA());
        context.register(ProviderB.class, new ProviderB());

        CircularDependencyException ex =
                assertThrows(CircularDependencyException.class, () -> context.get(ProviderA.class));

        assertTrue(
                ex.getMessage().contains("Circular dependency detected"),
                "Exception message should describe the circular dependency");
    }

    @Test
    @DisplayName("SC5: After circular dependency error, resolving set is cleared and context is reusable")
    void get_afterCircularDependencyError_resolvingSetIsCleared() {
        context.register(ProviderA.class, new ProviderA());
        context.register(ProviderB.class, new ProviderB());
        context.register(GlobalScopedProvider.class, new GlobalScopedProvider());

        // Trigger the circular dependency
        assertThrows(CircularDependencyException.class, () -> context.get(ProviderA.class));

        // The resolving set should be clean -- a non-circular provider should resolve fine
        assertDoesNotThrow(
                () -> context.get(GlobalScopedProvider.class),
                "Context must be reusable after a circular dependency error");
    }

    // ---------------------------------------------------------------
    // Additional edge-case tests
    // ---------------------------------------------------------------

    @Test
    @DisplayName("get() for unregistered provider throws IllegalArgumentException")
    void get_unregisteredProvider_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> context.get(AnnotationScopedProvider.class));
    }

    @Test
    @DisplayName("register() replaces an existing provider")
    void register_replacesExistingProvider() {
        AnnotationScopedProvider first = new AnnotationScopedProvider();
        AnnotationScopedProvider second = new AnnotationScopedProvider();

        context.register(AnnotationScopedProvider.class, first);
        String valueFromFirst = context.get(AnnotationScopedProvider.class);

        context.register(AnnotationScopedProvider.class, second);
        String valueFromSecond = context.get(AnnotationScopedProvider.class);

        assertNotSame(valueFromFirst, valueFromSecond, "After replacing a provider, get() should use the new provider");
    }

    @Test
    @DisplayName("Provider can depend on another provider without forming a cycle")
    void get_nonCircularDependency_resolves() {
        context.register(GlobalScopedProvider.class, new GlobalScopedProvider());
        context.register(DependentProvider.class, new DependentProvider());

        String result = context.get(DependentProvider.class);

        assertNotNull(result);
        assertTrue(result.startsWith("dependent-on-"), "Dependent provider should resolve its dependency");
    }

    @Test
    @DisplayName("Resolving set is cleaned up even when provider.get() throws")
    void get_providerThrows_resolvingSetIsCleared() {
        context.register(FailingProvider.class, new FailingProvider());
        context.register(GlobalScopedProvider.class, new GlobalScopedProvider());

        // First call should propagate the exception
        assertThrows(RuntimeException.class, () -> context.get(FailingProvider.class));

        // But the context should still be usable for other providers
        assertDoesNotThrow(
                () -> context.get(GlobalScopedProvider.class), "Context must be reusable after a provider throws");
    }

    @Test
    @DisplayName("getCurrentAnnotation() returns null before any annotation is set")
    void getCurrentAnnotation_initiallyNull() {
        assertNull(context.getCurrentAnnotation());
    }

    @Test
    @DisplayName("invalidate(ANNOTATION) with no providers registered does not throw")
    void invalidateAnnotation_emptyRegistry_doesNotThrow() {
        assertDoesNotThrow(() -> context.invalidate(ProviderScope.ANNOTATION));
    }
}
