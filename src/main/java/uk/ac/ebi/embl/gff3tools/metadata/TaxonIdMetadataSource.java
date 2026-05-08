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
package uk.ac.ebi.embl.gff3tools.metadata;

import java.util.Optional;
import uk.ac.ebi.ena.taxonomy.taxon.Taxon;

/**
 * A {@link MasterMetadataSource} that builds a {@link MasterMetadata} from a single taxon ID,
 * resolving scientific name, common name, and lineage via the supplied {@link TaxonProvider}.
 *
 * <p>Acts as a global fallback (returns the same metadata for any seqId query). Used when the
 * caller wants to supply taxonomy alone without a full master entry. If the {@link TaxonProvider}
 * cannot resolve the ID, the resulting metadata still carries the literal taxon ID so downstream
 * conversion can emit a {@code db_xref="taxon:N"} qualifier.
 */
public class TaxonIdMetadataSource implements MasterMetadataSource {

    private final MasterMetadata metadata;

    public TaxonIdMetadataSource(Long taxonId, TaxonProvider taxonProvider) {
        this.metadata = fromTaxonId(taxonId, taxonProvider);
    }

    @Override
    public Optional<MasterMetadata> getMetadata(String seqId) {
        return Optional.of(metadata);
    }

    private static MasterMetadata fromTaxonId(Long taxonId, TaxonProvider taxonProvider) {
        MasterMetadata meta = new MasterMetadata();
        meta.setTaxon(String.valueOf(taxonId));
        taxonProvider.resolve(taxonId).ifPresent(taxon -> applyTaxonFields(meta, taxon));
        return meta;
    }

    private static void applyTaxonFields(MasterMetadata meta, Taxon taxon) {
        if (taxon.getScientificName() != null) {
            meta.setScientificName(taxon.getScientificName());
        }
        if (taxon.getCommonName() != null) {
            meta.setCommonName(taxon.getCommonName());
        }
        if (taxon.getLineage() != null) {
            meta.setLineage(taxon.getLineage());
        }
        if (taxon.getTaxId() != null) {
            meta.setTaxon(String.valueOf(taxon.getTaxId()));
        }
    }
}
