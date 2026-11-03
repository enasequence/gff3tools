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
package uk.ac.ebi.embl.gff3tools.validation.provider;

import java.util.Set;
import java.util.stream.Collectors;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;

/**
 * A {@link LocusTagProvider} backed by a pre-fetched, immutable set of registered prefixes.
 *
 * <p>The no-arg constructor creates an inactive instance so
 * the classpath scanner can auto-register it without activating the registered-prefix rule. Callers
 * must use the {@link #LocusTagPrefixProvider(Set)} constructor to supply real prefixes fetched
 * before building the validation engine.
 *
 * <p>An explicitly constructed instance is always active, even when the prefix set is empty. An
 * empty set means the lookup returned nothing (no prefixes registered for the project, or a failed
 * lookup), so every locus_tag prefix is rejected — failing fast rather than silently passing all.
 */
public class LocusTagPrefixProvider implements LocusTagProvider {

    private final Set<String> prefixes;

    /** Inactive instance for classpath scanning — does not activate the registered-prefix rule. */
    public LocusTagPrefixProvider() {
        this.prefixes = null;
    }

    public LocusTagPrefixProvider(Set<String> prefixes) {
        this.prefixes = prefixes == null
                ? Set.of()
                : prefixes.stream().map(String::toUpperCase).collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public Set<String> getPrefixes() {
        return prefixes != null ? prefixes : Set.of();
    }

    @Override
    public LocusTagProvider get(ValidationContext context) {
        return this;
    }

    @Override
    public boolean isActive() {
        return prefixes != null;
    }
}
