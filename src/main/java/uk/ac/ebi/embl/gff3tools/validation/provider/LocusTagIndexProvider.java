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

import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.utils.OntologyClient;
import uk.ac.ebi.embl.gff3tools.validation.ContextProvider;
import uk.ac.ebi.embl.gff3tools.validation.ProviderScope;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;

/**
 * ANNOTATION-scoped provider that lazily builds a {@link LocusTagIndex} for the
 * current annotation.
 *
 * <p>Depends on {@link OntologyClientProvider} for ontology lookups. The cache
 * is cleared on {@link #invalidate()} (triggered automatically when the engine
 * transitions to a new annotation), and the index is recomputed lazily on the
 * next {@link #get(ValidationContext)} call.
 */
public class LocusTagIndexProvider implements ContextProvider<LocusTagIndex> {

    private LocusTagIndex cached;

    @Override
    public LocusTagIndex get(ValidationContext context) {
        if (cached == null) {
            OntologyClient ontology = context.get(OntologyClientProvider.class);
            GFF3Annotation annotation = context.getCurrentAnnotation();
            cached = LocusTagIndex.build(annotation.getFeatures(), ontology);
        }
        return cached;
    }

    @Override
    public void invalidate() {
        cached = null;
    }

    @Override
    public ProviderScope scope() {
        return ProviderScope.ANNOTATION;
    }
}
