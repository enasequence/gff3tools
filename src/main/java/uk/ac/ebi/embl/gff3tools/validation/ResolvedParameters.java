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

import java.util.Locale;
import java.util.Map;

/**
 * The resolved, typed parameter values produced by {@link ParameterProvider}. Accessors are
 * keyed by the same namespaced {@code RULE.PARAM} string used throughout {@code --params}
 * declarations; the key is upper-cased on lookup, matching {@code --params}' own casing rule.
 */
public final class ResolvedParameters {

    private final Map<String, Object> values;

    ResolvedParameters(Map<String, Object> values) {
        this.values = Map.copyOf(values);
    }

    public String getString(String key) {
        return (String) get(key);
    }

    public long getLong(String key) {
        return (Long) get(key);
    }

    private Object get(String key) {
        String normalized = key.toUpperCase(Locale.ROOT);
        if (!values.containsKey(normalized)) {
            throw new IllegalArgumentException("No resolved parameter for key: " + key);
        }
        return values.get(normalized);
    }
}
