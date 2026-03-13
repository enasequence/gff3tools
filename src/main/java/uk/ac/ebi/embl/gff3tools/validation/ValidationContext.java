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

    private final Map<Class<?>, ContextProvider<?>> providers = new HashMap<>();

    /**
     * Resolve a provider's value by the value type it produces.
     *
     * @param type the value type to look up
     * @param <T>  the value type
     * @return the cached or freshly computed value
     * @throws IllegalArgumentException if no provider is registered for the given type
     */
    @SuppressWarnings("unchecked")
    public <T> T get(Class<T> type) {
        ContextProvider<?> provider = providers.get(type);
        if (provider == null) {
            throw new IllegalArgumentException("No provider registered for " + type.getName());
        }
        return (T) provider.get(this);
    }

    /**
     * Register or replace a provider in the context.
     */
    public <T> void register(Class<T> type, ContextProvider<T> provider) {
        providers.put(type, provider);
    }

    public boolean contains(Class<?> type) {
        return providers.containsKey(type);
    }
}
