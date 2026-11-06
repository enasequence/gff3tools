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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger LOGGER = LoggerFactory.getLogger(EmblEntryMetadataSource.class);

    private static final Pattern WGS_SET_PREFIX = Pattern.compile("^([A-Za-z]{4}|[A-Za-z]{6})(\\d{2})");

    private final MasterMetadata metadata;

    public EmblEntryMetadataSource(Entry entry) {
        this.metadata = fromEntry(entry);
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

    private static MasterMetadata fromEntry(Entry entry) {
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
        // WGS/TSA/TLS lines, set by sequencetools' MasterWGS/MasterTSA/MasterTLS readers (registered
        // by the --master-entry reader in Gff3ProviderFactory). Only WGS sets get contig
        // materialisation downstream (GFF3Mapper), so wgsSet is only derived for WGS; TSA/TLS keep
        // the plain SET path.
        String contigDataclass = entry.getContigDataclass();
        if (contigDataclass != null) {
            meta.setContigDataclass(contigDataclass);
            if (Entry.WGS_DATACLASS.equals(contigDataclass)) {
                meta.setWgsSet(deriveWgsSet(entry));
            }
        }
        if (entry.getVersion() != null) {
            meta.setVersion(entry.getVersion());
        }
        if (entry.getComment() != null && entry.getComment().getText() != null) {
            meta.setComment(entry.getComment().getText());
        }

        if (entry.getSequence() != null) {
            if (entry.getSequence().getMoleculeType() != null) {
                meta.setMoleculeType(entry.getSequence().getMoleculeType());
            }
            if (entry.getSequence().getTopology() != null) {
                meta.setTopology(entry.getSequence().getTopology().name().toLowerCase());
            }
        }

        // Source feature fields. SourceFeature.getTaxon() returns the Taxon populated
        // by the EMBL reader (from OS/OC/organism lines), so we read all taxon-shaped
        // fields from there.
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

            // Fallback: recover the taxon ID from db_xref="taxon:N" when the Taxon path
            // didn't supply one. Only the raw ID is recovered; scientific name and lineage
            // remain whatever the EMBL reader provided.
            if (meta.getTaxon() == null) {
                sourceFeature.getQualifiers("db_xref").stream()
                        .map(Qualifier::getValue)
                        .filter(v -> v != null && v.startsWith("taxon:"))
                        .findFirst()
                        .map(v -> v.substring("taxon:".length()))
                        .ifPresent(meta::setTaxon);
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

        if (!entry.getKeywords().isEmpty()) {
            meta.setKeywords(entry.getKeywords().stream()
                    .map(uk.ac.ebi.embl.api.entry.Text::getText)
                    .filter(t -> t != null)
                    .toList());
        }

        if (!entry.getProjectAccessions().isEmpty()) {
            meta.setProject(entry.getProjectAccessions().stream()
                    .map(uk.ac.ebi.embl.api.entry.Text::getText)
                    .filter(t -> t != null)
                    .findFirst()
                    .orElse(null));
        }

        if (!entry.getSecondaryAccessions().isEmpty()) {
            meta.setSecondaryAccessions(entry.getSecondaryAccessions().stream()
                    .map(uk.ac.ebi.embl.api.entry.Text::getText)
                    .filter(t -> t != null)
                    .toList());
        }

        return meta;
    }

    /**
     * The WGS set from the first contig range on the WGS line, falling back to the master's own
     * accession (also a WGS-set accession, e.g. {@code CAXMYH010000000}) when the WGS line carries
     * no parseable range. Without it GFF3Mapper would emit a WGS contig lacking the root-set DR line.
     */
    private static String deriveWgsSet(Entry entry) {
        String wgsSet = entry.getContigAccessions().isEmpty()
                ? null
                : wgsSetFromContigRange(entry.getContigAccessions().get(0).getText());
        if (wgsSet == null) {
            wgsSet = wgsSetFromContigRange(entry.getPrimaryAccession());
        }
        if (wgsSet == null) {
            LOGGER.warn(
                    "WGS master '{}' has no parseable WGS set accession; the root-set DR line will be omitted",
                    entry.getPrimaryAccession());
        }
        return wgsSet;
    }

    /**
     * "CAXMYH010000001-CAXMYH010000009" or "CAXMYH010000000" -> "CAXMYH01" (4- or 6-letter prefix
     * + 2-digit set version); null when the value does not start with a WGS accession.
     */
    static String wgsSetFromContigRange(String range) {
        if (range == null) {
            return null;
        }
        Matcher m = WGS_SET_PREFIX.matcher(range.trim());
        return m.find() ? m.group(1).toUpperCase() + m.group(2) : null;
    }
}
