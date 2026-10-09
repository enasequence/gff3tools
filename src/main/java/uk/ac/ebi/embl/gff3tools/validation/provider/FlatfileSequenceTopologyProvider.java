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
 * The topology each flat file entry declares on its ID line, keyed by the accession its annotation
 * is validated under. Filled in as entries are read, so an entry's topology is known by the time its
 * annotation is validated.
 *
 * <p>Only a declared topology is recorded: the reader leaves a missing or placeholder topology
 * ({@code XXX}) unset, and that stays unknown here rather than becoming linear.
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

    /**
     * An empty provider knows no topology, so the classpath-scanned instance is kept off the context.
     * Flat file conversion registers its own instance directly.
     */
    @Override
    public boolean isActive() {
        return !topologies.isEmpty();
    }

    /** Records the topology an entry declares; a {@code null} (undeclared) topology is not recorded. */
    public void record(String accession, Sequence.Topology topology) {
        if (topology == null) {
            return;
        }
        topologies.put(accession, topology == Sequence.Topology.CIRCULAR ? Topology.CIRCULAR : Topology.LINEAR);
    }

    /** The topology recorded for the accession, or empty when its entry declared none. */
    public Optional<Topology> getTopology(String accession) {
        return Optional.ofNullable(topologies.get(accession));
    }
}
