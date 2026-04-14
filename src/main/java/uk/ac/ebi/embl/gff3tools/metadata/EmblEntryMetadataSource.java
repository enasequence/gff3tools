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
import uk.ac.ebi.embl.api.entry.feature.Feature;
import uk.ac.ebi.embl.api.entry.qualifier.OrganismQualifier;
import uk.ac.ebi.embl.api.entry.qualifier.Qualifier;

/**
 * An {@link AnnotationMetadataSource} that adapts a parsed EMBL flatfile {@link Entry}
 * (master entry) into {@link AnnotationMetadata}. Maps all available Entry fields to the
 * metadata model for backward compatibility.
 *
 * <p>Acts as a global fallback: returns the same metadata for any seqId query.
 */
public class EmblEntryMetadataSource implements AnnotationMetadataSource {

    private final AnnotationMetadata metadata;

    public EmblEntryMetadataSource(Entry entry) {
        this.metadata = fromEntry(entry);
    }

    @Override
    public Optional<AnnotationMetadata> getMetadata(String seqId) {
        return Optional.of(metadata);
    }

    /**
     * Returns the pre-built AnnotationMetadata for use outside the provider chain
     * (e.g. in FFToGff3Converter direction).
     */
    public AnnotationMetadata getMetadata() {
        return metadata;
    }

    private static AnnotationMetadata fromEntry(Entry entry) {
        AnnotationMetadata meta = new AnnotationMetadata();

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

        // Source feature fields
        Feature sourceFeature = entry.getPrimarySourceFeature();
        if (sourceFeature != null) {
            // Extract organism (scientific name) and taxon ID from OrganismQualifier
            Optional<OrganismQualifier> organismQualifier = sourceFeature.getQualifiers("organism").stream()
                    .filter(q -> q instanceof OrganismQualifier)
                    .map(q -> (OrganismQualifier) q)
                    .findFirst();

            organismQualifier.map(OrganismQualifier::getValue).ifPresent(meta::setScientificName);

            // Prefer taxon ID from OrganismQualifier's Taxon object (parsed by EMBL reader)
            organismQualifier
                    .map(OrganismQualifier::getTaxon)
                    .map(taxon -> taxon.getTaxId())
                    .map(String::valueOf)
                    .ifPresent(meta::setTaxon);

            // Extract lineage from Taxon object (OC line data)
            organismQualifier
                    .map(OrganismQualifier::getTaxon)
                    .map(taxon -> taxon.getLineage())
                    .ifPresent(meta::setLineage);

            // Extract common name from Taxon object
            organismQualifier
                    .map(OrganismQualifier::getTaxon)
                    .map(taxon -> taxon.getCommonName())
                    .ifPresent(meta::setCommonName);

            // Fallback: extract taxon from db_xref qualifier if not found via OrganismQualifier
            if (meta.getTaxon() == null) {
                sourceFeature.getQualifiers("db_xref").stream()
                        .map(Qualifier::getValue)
                        .filter(v -> v != null && v.startsWith("taxon:"))
                        .findFirst()
                        .map(v -> v.substring("taxon:".length()))
                        .ifPresent(meta::setTaxon);
            }

            // If no OrganismQualifier found, try regular organism qualifier for name
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
}
