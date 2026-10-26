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
import uk.ac.ebi.embl.api.entry.sequence.Sequence;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.utils.ControlledVocabularyUtils.Topology;
import uk.ac.ebi.embl.gff3tools.validation.ContextProvider;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;

/**
 * Each flat file entry's ID-line topology, by accession, recorded before the entry is validated. An
 * undeclared topology ({@code XXX}) is not recorded, so it stays unknown rather than linear.
 */
public class FlatfileSequenceTopologyProvider implements ContextProvider<FlatfileSequenceTopologyProvider> {

    private final Map<String, Topology> topologies = new HashMap<>();

    @Override
    public FlatfileSequenceTopologyProvider get(ValidationContext context) {
        return this;
    }

    @Override
    public Class<FlatfileSequenceTopologyProvider> type() {
        return FlatfileSequenceTopologyProvider.class;
    }

    /** A {@code null} (undeclared) topology is not recorded. */
    public void record(String accession, Sequence.Topology topology) {
        if (topology == null) {
            return;
        }
        topologies.put(accession, topology == Sequence.Topology.CIRCULAR ? Topology.CIRCULAR : Topology.LINEAR);
    }

    /** Empty when the entry declared none. */
    public Optional<Topology> getTopology(String accession) {
        return Optional.ofNullable(topologies.get(accession));
    }
}
