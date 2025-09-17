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
package uk.ac.ebi.embl.gff3tools.fftogff3;

import io.vavr.Function0;
import java.util.Optional;
import uk.ac.ebi.embl.api.entry.Entry;
import uk.ac.ebi.embl.api.entry.feature.Feature;
import uk.ac.ebi.embl.api.entry.qualifier.OrganismQualifier;
import uk.ac.ebi.embl.gff3tools.exception.*;
import uk.ac.ebi.embl.gff3tools.gff3.directives.GFF3SequenceRegion;
import uk.ac.ebi.embl.gff3tools.gff3.directives.GFF3Species;
import uk.ac.ebi.ena.taxonomy.taxon.Taxon;

public class GFF3DirectivesFactory {

    static final String BASE_TAXON_URL = "https://www.ncbi.nlm.nih.gov/Taxonomy/Browser/wwwtax.cgi";

    private String buildTaxonomyUrl(Optional<OrganismQualifier> qualifier) {
        Function0<String> getOrganism = () -> qualifier
                .map(OrganismQualifier::getValue)
                .map((name) -> "%s?name=%s".formatted(BASE_TAXON_URL, name))
                .orElseGet(() -> null);

        return qualifier
                .map(OrganismQualifier::getTaxon)
                .map(Taxon::getTaxId)
                .map((Long id) -> "%s?id=%d".formatted(BASE_TAXON_URL, id))
                .orElseGet(getOrganism);
    }

    public GFF3Species createSpecies(Entry entry, Entry masterEntry) throws NoSourcePresentException {
        Entry sourceEntry = masterEntry == null ? entry : masterEntry;

        Feature feature =
                Optional.ofNullable(sourceEntry.getPrimarySourceFeature()).orElseThrow(NoSourcePresentException::new);

        Optional<OrganismQualifier> qualifier =
                feature.getQualifiers("organism").stream().findFirst().map(q -> (OrganismQualifier) q);

        return new GFF3Species(buildTaxonomyUrl(qualifier));
    }

    public GFF3SequenceRegion createSequenceRegion(Entry entry)
            throws NoSourcePresentException, NoAccessionPresentException {

        String accession =
                Optional.ofNullable(entry.getSequence().getAccession()).orElseThrow(NoAccessionPresentException::new);
        if (accession != null && !accession.isEmpty()) {
            String[] parts = accession.split("[.]");
            String sequenceId = parts[0];
            Optional<Integer> sequenceVersion;
            if (parts.length == 2) {
                sequenceVersion = Optional.of(Integer.parseInt(parts[1]));
            } else if (entry.getSequence() != null && entry.getSequence().getVersion() != null) {
                // version from ID line.
                sequenceVersion = Optional.of(entry.getSequence().getVersion());
            } else {
                sequenceVersion = Optional.of(1);
            }
            Feature feature =
                    Optional.ofNullable(entry.getPrimarySourceFeature()).orElseThrow(NoSourcePresentException::new);

            long start = feature.getLocations().getMinPosition();
            long end = feature.getLocations().getMaxPosition();

            return new GFF3SequenceRegion(sequenceId, sequenceVersion, start, end);
        }
        return null;
    }
}
