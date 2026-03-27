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

import uk.ac.ebi.embl.gff3tools.validation.ContextProvider;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;

/**
 * Auto-discovered provider that supplies a shared {@link TranslationState} instance.
 *
 * <p>The state is eagerly created and shared across the fix and validation phases.
 */
public class TranslationStateProvider implements ContextProvider<TranslationState> {

    private final TranslationState state = new TranslationState();

    @Override
    public TranslationState get(ValidationContext context) {
        return state;
    }

    @Override
    public Class<TranslationState> type() {
        return TranslationState.class;
    }
}
