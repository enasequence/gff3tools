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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import uk.ac.ebi.embl.gff3tools.gff3.directives.GFF3SequenceRegion;
import uk.ac.ebi.embl.gff3tools.validation.ContextProvider;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;

/**
 * Each GFF3 {@code ##sequence-region}, by accession, recorded as the directive is read. Feature rules
 * run line by line, before the region is set on their annotation, and look it up here.
 */
public class SequenceRegionProvider implements ContextProvider<SequenceRegionProvider> {

    private final Map<String, GFF3SequenceRegion> sequenceRegions = new HashMap<>();

    @Override
    public SequenceRegionProvider get(ValidationContext context) {
        return this;
    }

    @Override
    public Class<SequenceRegionProvider> type() {
        return SequenceRegionProvider.class;
    }

    public void record(GFF3SequenceRegion sequenceRegion) {
        sequenceRegions.put(sequenceRegion.accession(), sequenceRegion);
    }

    /** Empty when the file declared none for the accession. */
    public Optional<GFF3SequenceRegion> getSequenceRegion(String accession) {
        return Optional.ofNullable(sequenceRegions.get(accession));
    }
}
