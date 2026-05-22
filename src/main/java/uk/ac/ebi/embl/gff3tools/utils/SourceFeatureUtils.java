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
package uk.ac.ebi.embl.gff3tools.utils;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import uk.ac.ebi.ena.taxonomy.taxon.Taxon;

public class SourceFeatureUtils {

    public static void dumpSourceFeatureDto(List<SourceFeatureDTO> sourceFeatures, Path out) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.addMixIn(Taxon.class, TaxonMixIn.class);
        mapper.writerWithDefaultPrettyPrinter().writeValue(out.toFile(), sourceFeatures);
    }

    public static List<SourceFeatureDTO> loadSourceFeatureDto(Path in) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.addMixIn(Taxon.class, TaxonMixIn.class);
        return mapper.readValue(
                in.toFile(), mapper.getTypeFactory().constructCollectionType(List.class, SourceFeatureDTO.class));
    }

    public static abstract class TaxonMixIn {

        @JsonAlias("formal")
        abstract void setFormalName(Boolean formalName);
    }
}
