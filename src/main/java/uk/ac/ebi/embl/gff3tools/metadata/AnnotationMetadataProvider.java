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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Chain-of-responsibility provider that merges {@link AnnotationMetadata} from multiple
 * {@link AnnotationMetadataSource} instances. Sources are queried in registration order
 * (highest-priority first). Merging is field-level: a null field in a higher-priority source
 * does not shadow a non-null field in a lower-priority source.
 */
public class AnnotationMetadataProvider {

    private final List<AnnotationMetadataSource> sources;

    public AnnotationMetadataProvider() {
        this.sources = new ArrayList<>();
    }

    /**
     * Registers a metadata source. Sources are queried in registration order
     * (first registered = highest priority).
     */
    public void addSource(AnnotationMetadataSource source) {
        this.sources.add(source);
    }

    /**
     * Returns a merged {@link AnnotationMetadata} for the given seqId. Fields are merged
     * across all sources: the first non-null value for each field wins.
     */
    public Optional<AnnotationMetadata> getMetadata(String seqId) {
        AnnotationMetadata merged = null;

        for (AnnotationMetadataSource source : sources) {
            Optional<AnnotationMetadata> opt = source.getMetadata(seqId);
            if (opt.isEmpty()) {
                continue;
            }
            if (merged == null) {
                merged = new AnnotationMetadata();
            }
            mergeFields(merged, opt.get());
        }

        return Optional.ofNullable(merged);
    }

    /**
     * Merges non-null fields from {@code source} into {@code target}. Only fills fields
     * that are currently null in {@code target} (first-writer-wins semantics).
     */
    private void mergeFields(AnnotationMetadata target, AnnotationMetadata source) {
        if (target.getId() == null) target.setId(source.getId());
        if (target.getAccession() == null) target.setAccession(source.getAccession());
        if (target.getSecondaryAccessions() == null) target.setSecondaryAccessions(source.getSecondaryAccessions());
        if (target.getDescription() == null) target.setDescription(source.getDescription());
        if (target.getTitle() == null) target.setTitle(source.getTitle());
        if (target.getVersion() == null) target.setVersion(source.getVersion());
        if (target.getMoleculeType() == null) target.setMoleculeType(source.getMoleculeType());
        if (target.getTopology() == null) target.setTopology(source.getTopology());
        if (target.getDivision() == null) target.setDivision(source.getDivision());
        if (target.getDataClass() == null) target.setDataClass(source.getDataClass());
        if (target.getProject() == null) target.setProject(source.getProject());
        if (target.getSample() == null) target.setSample(source.getSample());
        if (target.getTaxon() == null) target.setTaxon(source.getTaxon());
        if (target.getScientificName() == null) target.setScientificName(source.getScientificName());
        if (target.getCommonName() == null) target.setCommonName(source.getCommonName());
        if (target.getLineage() == null) target.setLineage(source.getLineage());
        if (target.getKeywords() == null) target.setKeywords(source.getKeywords());
        if (target.getComment() == null) target.setComment(source.getComment());
        if (target.getPublications() == null) target.setPublications(source.getPublications());
        if (target.getReferences() == null) target.setReferences(source.getReferences());
        if (target.getChromosomeName() == null) target.setChromosomeName(source.getChromosomeName());
        if (target.getChromosomeType() == null) target.setChromosomeType(source.getChromosomeType());
        if (target.getChromosomeLocation() == null) target.setChromosomeLocation(source.getChromosomeLocation());
        if (target.getFirstPublic() == null) target.setFirstPublic(source.getFirstPublic());
        if (target.getLastUpdated() == null) target.setLastUpdated(source.getLastUpdated());
        if (target.getAssemblyLevel() == null) target.setAssemblyLevel(source.getAssemblyLevel());
        if (target.getAssemblyType() == null) target.setAssemblyType(source.getAssemblyType());
    }
}
