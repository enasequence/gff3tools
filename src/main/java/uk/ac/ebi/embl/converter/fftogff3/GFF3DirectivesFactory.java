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
package uk.ac.ebi.embl.converter.fftogff3;

import io.vavr.Function0;
import java.util.Optional;
import uk.ac.ebi.embl.api.entry.Entry;
import uk.ac.ebi.embl.api.entry.feature.Feature;
import uk.ac.ebi.embl.api.entry.qualifier.OrganismQualifier;
import uk.ac.ebi.embl.converter.exception.*;
import uk.ac.ebi.embl.converter.gff3.directives.GFF3SequenceRegion;
import uk.ac.ebi.embl.converter.gff3.directives.GFF3Species;
import uk.ac.ebi.ena.taxonomy.taxon.Taxon;

public class GFF3DirectivesFactory {

    boolean ignoreSpecies;

    public GFF3DirectivesFactory(boolean ignoreSpecies) {
        this.ignoreSpecies = ignoreSpecies;
    }

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

    public GFF3Species extractSpecies(Entry entry) throws NoSourcePresentException {

        Feature feature =
                Optional.ofNullable(entry.getPrimarySourceFeature()).orElseThrow(NoSourcePresentException::new);

        Optional<OrganismQualifier> qualifier =
                feature.getQualifiers("organism").stream().findFirst().map(q -> (OrganismQualifier) q);

        return new GFF3Species(buildTaxonomyUrl(qualifier));
    }

    public GFF3SequenceRegion extractSequenceRegion(Entry entry) throws NoSourcePresentException {

        String accession = entry.getPrimaryAccession();
        if (accession != null && !accession.isEmpty()) {
            String[] parts = accession.split("[.]");
            String sequenceId = parts[0];
            Optional<Integer> sequenceVersion;
            if (parts.length == 2) {
                sequenceVersion = Optional.of(Integer.parseInt(parts[1]));
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
