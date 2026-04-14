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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Unified metadata model for GFF3 conversions. This is the union of all metadata fields
 * needed by both conversion directions (GFF3-to-EMBL and EMBL-to-GFF3).
 *
 * <p>Directly deserializable from MasterEntry JSON via Jackson. Fields present in both
 * FastaHeader and MasterEntry use the same semantics. Unknown JSON properties are ignored
 * to allow forward-compatible schema evolution.
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class AnnotationMetadata {

    // ── Identity ──
    private String id;
    private String accession;
    private List<String> secondaryAccessions;

    // ── Descriptive ──
    private String description;
    private String title;
    private Integer version;

    // ── Sequence classification ──
    private String moleculeType;
    private String topology;
    private String division;
    private String dataClass;

    // ── Project / Sample ──
    private String project;
    private String sample;

    // ── Taxonomy ──
    private String taxon;
    private String scientificName;
    private String commonName;
    private String lineage;

    // ── Keywords / Comments ──
    private List<String> keywords;
    private String comment;

    // ── Publications and References ──
    private List<CrossReference> publications;
    private List<ReferenceData> references;

    // ── Chromosome ──
    private String chromosomeName;
    private String chromosomeType;
    private String chromosomeLocation;

    // ── Dates ──
    private String firstPublic;
    private String lastUpdated;

    // ── Assembly (carried but not mapped to EMBL lines) ──
    private String assemblyLevel;
    private String assemblyType;
}
