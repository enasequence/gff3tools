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

/**
 * What's known about an accession's organism before it's resolved to a full {@code Taxon} — either
 * a taxon ID or a scientific name, whichever the input format supplied. Core only records this
 * identity; resolving it to an actual {@code Taxon} (via a taxonomy API) is a downstream concern
 * (see {@link TaxonProvider}).
 */
public sealed interface TaxonomyIdentifier {

    record ByTaxId(long taxId) implements TaxonomyIdentifier {}

    record ByScientificName(String scientificName) implements TaxonomyIdentifier {}
}
