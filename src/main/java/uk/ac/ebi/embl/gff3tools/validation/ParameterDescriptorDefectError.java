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
 * Thrown when {@link ParameterProvider}'s no-arg constructor encounters a malformed {@link
 * Parameter} descriptor (from {@link ParameterDescriptors#scan()}). A malformed descriptor is a
 * programmer error, not a recoverable runtime condition, so it is surfaced as an {@link Error}
 * rather than an {@link Exception} — this guarantees it cannot be silently swallowed by {@code
 * ValidationRegistry.instantiateProviders()}'s {@code catch (Exception e)} block, which would
 * otherwise cause the entire {@link ResolvedParameters} provider to silently disappear.
 */
public class ParameterDescriptorDefectError extends Error {

    public ParameterDescriptorDefectError(String message, Throwable cause) {
        super(message, cause);
    }
}
