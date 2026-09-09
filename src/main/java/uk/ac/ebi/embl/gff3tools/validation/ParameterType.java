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
 * The declared type of a {@link Parameter}. Governs how raw string values (from
 * {@code --params} or an annotation's {@code defaultValue}) are coerced.
 */
public enum ParameterType {
    STRING {
        @Override
        public Object coerce(String value) {
            return value;
        }
    },
    LONG {
        @Override
        public Object coerce(String value) {
            return Long.parseLong(value);
        }
    };

    /**
     * Coerces a raw string value to this type's runtime representation.
     *
     * @throws NumberFormatException if the value does not coerce to this type
     */
    public abstract Object coerce(String value);
}
