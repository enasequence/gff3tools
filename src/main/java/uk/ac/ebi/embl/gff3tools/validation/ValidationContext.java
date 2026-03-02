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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import uk.ac.ebi.embl.gff3tools.exception.CircularDependencyException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;

/**
 * Holds a registry of {@link ContextProvider} instances and provides lazy,
 * type-safe resolution with circular dependency detection. The context also
 * tracks the current {@link GFF3Annotation} and automatically invalidates
 * {@link ProviderScope#ANNOTATION}-scoped providers when it changes.
 */
public class ValidationContext {

    private final Map<Class<? extends ContextProvider<?>>, ContextProvider<?>> providers = new HashMap<>();
    private final Set<Class<?>> resolving = new HashSet<>();
    private GFF3Annotation currentAnnotation;

    /**
     * Lazily resolve a provider's value with circular dependency detection.
     *
     * @param providerClass the provider class to look up
     * @param <T>           the value type produced by the provider
     * @return the cached or freshly computed value
     * @throws CircularDependencyException if the provider is already being resolved
     * @throws IllegalArgumentException    if no provider is registered for the given class
     */
    @SuppressWarnings("unchecked")
    public <T> T get(Class<? extends ContextProvider<T>> providerClass) {
        ContextProvider<?> provider = providers.get(providerClass);
        if (provider == null) {
            throw new IllegalArgumentException("No provider registered for " + providerClass.getName());
        }

        if (!resolving.add(providerClass)) {
            throw new CircularDependencyException(
                    "Circular dependency detected while resolving " + providerClass.getName());
        }

        try {
            return (T) provider.get(this);
        } finally {
            resolving.remove(providerClass);
        }
    }

    /**
     * Register or replace a provider in the context.
     */
    public <T> void register(Class<? extends ContextProvider<T>> providerClass, ContextProvider<T> provider) {
        providers.put(providerClass, provider);
    }

    /**
     * Invalidate all providers matching the given scope.
     */
    public void invalidate(ProviderScope scope) {
        for (ContextProvider<?> provider : providers.values()) {
            if (provider.scope() == scope) {
                provider.invalidate();
            }
        }
    }

    /**
     * Set the current annotation and trigger ANNOTATION-scope invalidation.
     */
    public void setCurrentAnnotation(GFF3Annotation annotation) {
        this.currentAnnotation = annotation;
        invalidate(ProviderScope.ANNOTATION);
    }

    public GFF3Annotation getCurrentAnnotation() {
        return currentAnnotation;
    }
}
