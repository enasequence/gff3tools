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
import uk.ac.ebi.embl.api.entry.Entry;
import uk.ac.ebi.embl.api.entry.feature.SourceFeature;
import uk.ac.ebi.embl.api.entry.qualifier.Qualifier;
import uk.ac.ebi.ena.taxonomy.taxon.Taxon;

/**
 * An {@link MasterMetadataSource} that adapts a parsed EMBL flatfile {@link Entry}
 * (master entry) into {@link MasterMetadata}. Maps all available Entry fields to the
 * metadata model for backward compatibility.
 *
 * <p>Acts as a global fallback: returns the same metadata for any seqId query.
 */
public class EmblEntryMetadataSource implements MasterMetadataSource {

    private final MasterMetadata metadata;

    public EmblEntryMetadataSource(Entry entry) {
        this(entry, new TaxonProvider());
    }

    public EmblEntryMetadataSource(Entry entry, TaxonProvider taxonProvider) {
        this.metadata = fromEntry(entry, taxonProvider);
    }

    @Override
    public Optional<MasterMetadata> getMetadata(String seqId) {
        return Optional.of(metadata);
    }

    /**
     * Returns the pre-built MasterMetadata for use outside the provider chain
     * (e.g. in FFToGff3Converter direction).
     */
    public MasterMetadata getMetadata() {
        return metadata;
    }

    private static MasterMetadata fromEntry(Entry entry, TaxonProvider taxonProvider) {
        MasterMetadata meta = new MasterMetadata();

        if (entry.getPrimaryAccession() != null) {
            meta.setAccession(entry.getPrimaryAccession());
        }
        if (entry.getDescription() != null && entry.getDescription().getText() != null) {
            meta.setDescription(entry.getDescription().getText());
        }
        if (entry.getDivision() != null) {
            meta.setDivision(entry.getDivision());
        }
        if (entry.getDataClass() != null) {
            meta.setDataClass(entry.getDataClass());
        }
        if (entry.getVersion() != null) {
            meta.setVersion(entry.getVersion());
        }
        if (entry.getComment() != null && entry.getComment().getText() != null) {
            meta.setComment(entry.getComment().getText());
        }

        // Sequence-level fields
        if (entry.getSequence() != null) {
            if (entry.getSequence().getMoleculeType() != null) {
                meta.setMoleculeType(entry.getSequence().getMoleculeType());
            }
            if (entry.getSequence().getTopology() != null) {
                meta.setTopology(entry.getSequence().getTopology().name().toLowerCase());
            }
        }

        // Source feature fields. SourceFeature.getTaxon() returns the Taxon populated
        // by the EMBL reader (from OS/OC/organism lines) regardless of whether the
        // underlying organism qualifier is the OrganismQualifier subclass or a plain Qualifier,
        // so we read all taxon-shaped fields from there.
        SourceFeature sourceFeature = entry.getPrimarySourceFeature();
        if (sourceFeature != null) {
            Taxon taxon = sourceFeature.getTaxon();
            if (taxon != null) {
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

            // Fallback: recover taxon metadata from db_xref="taxon:N" when the Taxon path
            // didn't supply a taxon ID. The db_xref itself only stores the ID, so resolve it
            // via taxonomy to fill lineage/commonName/scientificName where possible.
            if (meta.getTaxon() == null) {
                sourceFeature.getQualifiers("db_xref").stream()
                        .map(Qualifier::getValue)
                        .filter(v -> v != null && v.startsWith("taxon:"))
                        .findFirst()
                        .map(v -> v.substring("taxon:".length()))
                        .ifPresent(taxId -> applyTaxonFromDbXref(meta, taxId, taxonProvider));
            }

            // Fallback: recover scientific name from a plain /organism qualifier value
            // when the Taxon path didn't supply one.
            if (meta.getScientificName() == null) {
                sourceFeature.getQualifiers("organism").stream()
                        .findFirst()
                        .map(Qualifier::getValue)
                        .ifPresent(meta::setScientificName);
            }
        }

        // Keywords
        if (!entry.getKeywords().isEmpty()) {
            meta.setKeywords(entry.getKeywords().stream()
                    .map(uk.ac.ebi.embl.api.entry.Text::getText)
                    .filter(t -> t != null)
                    .toList());
        }

        // Project accessions
        if (!entry.getProjectAccessions().isEmpty()) {
            meta.setProject(entry.getProjectAccessions().stream()
                    .map(uk.ac.ebi.embl.api.entry.Text::getText)
                    .filter(t -> t != null)
                    .findFirst()
                    .orElse(null));
        }

        // Secondary accessions
        if (!entry.getSecondaryAccessions().isEmpty()) {
            meta.setSecondaryAccessions(entry.getSecondaryAccessions().stream()
                    .map(uk.ac.ebi.embl.api.entry.Text::getText)
                    .filter(t -> t != null)
                    .toList());
        }

        return meta;
    }

    private static void applyTaxonFromDbXref(MasterMetadata meta, String taxId, TaxonProvider taxonProvider) {
        meta.setTaxon(taxId);
        try {
            Long numericTaxId = Long.parseLong(taxId);
            taxonProvider.getTaxonByTaxId(numericTaxId).ifPresent(taxon -> applyTaxonFields(meta, taxon));
        } catch (NumberFormatException ignored) {
            // Keep the taxon ID recovered from db_xref even if it is non-numeric.
        }
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
