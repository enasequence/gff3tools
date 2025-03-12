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

import java.util.Optional;
import uk.ac.ebi.embl.api.entry.Entry;
import uk.ac.ebi.embl.api.entry.feature.Feature;
import uk.ac.ebi.embl.converter.IConversionRule;
import uk.ac.ebi.embl.converter.gff3.GFF3Headers;

public class FFGFF3HeadersFactory implements IConversionRule<Entry, GFF3Headers> {

  @Override
  public GFF3Headers from(Entry entry) {

    Feature feature =
        Optional.ofNullable(entry.getPrimarySourceFeature()).orElseThrow(NoSourcePresent::new);

    return new GFF3Headers(
        "3.1.26",
        entry.getPrimaryAccession() + ".1",
        feature.getLocations().getMinPosition(),
        feature.getLocations().getMaxPosition());
  }

  public static class NoSourcePresent extends ConversionError {}
  ;
}
