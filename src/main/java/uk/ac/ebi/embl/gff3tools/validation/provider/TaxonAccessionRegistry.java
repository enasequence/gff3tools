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

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import uk.ac.ebi.embl.gff3tools.validation.ContextProvider;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;

/**
 * Records each accession's {@link TaxonomyIdentifier} as input is read — e.g. by
 * {@code TSVToGFF3Converter}, since a TSV file can carry a different organism per row. A downstream
 * {@link TaxonProvider} implementation (e.g. in gff3-validations, backed by a taxonomy API client)
 * resolves these into actual {@code Taxon} objects via {@code context.get(TaxonAccessionRegistry.class)}.
 *
 * <p>Lookup is strictly per-accession — there is no "first seen" or submission-wide fallback here,
 * so a feature never silently ends up with a different row's organism.
 */
public class TaxonAccessionRegistry implements ContextProvider<TaxonAccessionRegistry> {

    private final Map<String, TaxonomyIdentifier> identifiersByAccession = new ConcurrentHashMap<>();

    /**
     * Records the taxonomy identifier for the given accession. A {@code null} accession or
     * identifier is ignored.
     */
    public void record(String accession, TaxonomyIdentifier identifier) {
        if (accession != null && identifier != null) {
            identifiersByAccession.put(accession, identifier);
        }
    }

    /**
     * Returns the taxonomy identifier recorded for the given accession, if any.
     */
    public Optional<TaxonomyIdentifier> find(String accession) {
        return accession == null ? Optional.empty() : Optional.ofNullable(identifiersByAccession.get(accession));
    }

    @Override
    public TaxonAccessionRegistry get(ValidationContext context) {
        return this;
    }

    @Override
    public Class<TaxonAccessionRegistry> type() {
        return TaxonAccessionRegistry.class;
    }
}
