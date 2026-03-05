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
import java.util.Map;

/**
 * Holds a registry of {@link ContextProvider} instances and provides lazy,
 * type-safe resolution.
 */
public class ValidationContext {

    private final Map<Class<? extends ContextProvider<?>>, ContextProvider<?>> providers = new HashMap<>();

    /**
     * Resolve a provider's value.
     *
     * @param providerClass the provider class to look up
     * @param <T>           the value type produced by the provider
     * @return the cached or freshly computed value
     * @throws IllegalArgumentException if no provider is registered for the given class
     */
    @SuppressWarnings("unchecked")
    public <T> T get(Class<? extends ContextProvider<T>> providerClass) {
        ContextProvider<?> provider = providers.get(providerClass);
        if (provider == null) {
            throw new IllegalArgumentException("No provider registered for " + providerClass.getName());
        }
        return (T) provider.get(this);
    }

    /**
     * Register or replace a provider in the context.
     */
    public <T> void register(Class<? extends ContextProvider<T>> providerClass, ContextProvider<T> provider) {
        providers.put(providerClass, provider);
    }
}
