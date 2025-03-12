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
import uk.ac.ebi.embl.converter.IConversionRule;
import uk.ac.ebi.embl.converter.gff3.GFF3SourceMetadata;
import uk.ac.ebi.ena.taxonomy.taxon.Taxon;

public class FFGFF3SourceAttributesFactory implements IConversionRule<Entry, GFF3SourceMetadata> {

  @Override
  public GFF3SourceMetadata from(Entry entry) throws ConversionError {

    Feature feature =
        Optional.ofNullable(entry.getPrimarySourceFeature())
            .orElseThrow(FFGFF3HeadersFactory.NoSourcePresent::new);

    Optional<OrganismQualifier> qualifier =
        feature.getQualifiers("organism").stream().findFirst().map(q -> (OrganismQualifier) q);

    Function0<String> getOrganism =
        () ->
            qualifier
                .map(OrganismQualifier::getValue)
                .map("https://www.ncbi.nlm.nih.gov/Taxonomy/Browser/wwwtax.cgi?name=%s"::formatted)
                .orElseGet(() -> null);

    String species =
        qualifier
            .map(OrganismQualifier::getTaxon)
            .map(Taxon::getTaxId)
            .map("https://www.ncbi.nlm.nih.gov/Taxonomy/Browser/wwwtax.cgi?id=%d"::formatted)
            .orElseGet(getOrganism);

    return new GFF3SourceMetadata(species);
  }
}
