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

/**
 * A typed provider that lazily computes and caches a value for the validation context.
 *
 * @param <T> the type of value this provider produces
 */
public interface ContextProvider<T> {

    /**
     * Lazily compute or return the cached value. Implementations may resolve
     * other providers via the supplied {@code context}.
     */
    T get(ValidationContext context);

    /**
     * Flush the cached value so the next call to {@link #get(ValidationContext)}
     * recomputes it.
     */
    void invalidate();

    /**
     * Declare whether this provider is {@link ProviderScope#GLOBAL} or
     * {@link ProviderScope#ANNOTATION} scoped.
     */
    ProviderScope scope();
}
