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

import java.util.ArrayList;
import java.util.List;
import uk.ac.ebi.embl.gff3tools.sequence.readers.CompositeSequenceReader;
import uk.ac.ebi.embl.gff3tools.sequence.readers.SequenceReader;
import uk.ac.ebi.embl.gff3tools.validation.ContextProvider;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;

/**
 * A {@link ContextProvider} that aggregates multiple {@link SequenceSource} instances
 * and exposes them as a single {@link SequenceReader} via {@link CompositeSequenceReader}.
 *
 * <p>Returns {@code null} from {@link #get} when no sources have been added,
 * allowing downstream consumers (e.g. TranslationFix) to skip gracefully.
 */
public class CompositeSequenceProvider implements ContextProvider<SequenceReader> {

    private final List<SequenceSource> sources = new ArrayList<>();
    private CompositeSequenceReader cachedReader;

    /**
     * Registers a sequence source. Sources are queried in registration order.
     */
    public void addSource(SequenceSource source) {
        this.sources.add(source);
        this.cachedReader = null; // invalidate cache
    }

    /**
     * Returns {@code true} if at least one source has been registered.
     */
    public boolean hasSources() {
        return !sources.isEmpty();
    }

    @Override
    public SequenceReader get(ValidationContext context) {
        if (sources.isEmpty()) {
            return null;
        }
        if (cachedReader == null) {
            cachedReader = new CompositeSequenceReader(sources);
        }
        return cachedReader;
    }

    @Override
    public Class<SequenceReader> type() {
        return SequenceReader.class;
    }
}
