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
 * A single declared parameter, scanned from a {@link Parameter} annotation on a
 * {@code @ValidationMethod}/{@code @FixMethod}-annotated method.
 *
 * @param key the namespaced, upper-cased {@code RULE.PARAM} key, derived as {@code rule + "." +
 *     name} at scan time
 * @param rule the owning rule/fix name, taken from the method's own {@code @ValidationMethod}/
 *     {@code @FixMethod} annotation
 * @param name the parameter's own name, as declared on {@link Parameter}
 */
public record ParameterDescriptor(
        String key,
        String rule,
        String name,
        ParameterType type,
        String description,
        boolean mandatory,
        String defaultValue) {}
