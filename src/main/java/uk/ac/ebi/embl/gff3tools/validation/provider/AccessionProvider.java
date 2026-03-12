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

import java.util.List;
import java.util.Map;
import uk.ac.ebi.embl.gff3tools.validation.ContextProvider;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;

/**
 * Provides the accession replacement map to the validation engine.
 *
 * <p>Not auto-discovered — must be supplied via
 * {@link uk.ac.ebi.embl.gff3tools.validation.ValidationEngineBuilder#withProvider(ContextProvider)}
 * from the CLI process command.
 */
public class AccessionProvider implements ContextProvider<AccessionMap> {

    private final AccessionMap accessionMap;

    /** Map mode: explicit old→new mapping. */
    public AccessionProvider(Map<String, String> map) {
        this.accessionMap = new AccessionMap(map);
    }

    /** List mode: new accessions assigned on demand during reading. */
    public AccessionProvider(List<String> accessions) {
        this.accessionMap = new AccessionMap(accessions);
    }

    @Override
    public AccessionMap get(ValidationContext context) {
        return accessionMap;
    }

    @Override
    public Class<AccessionMap> type() {
        return AccessionMap.class;
    }
}
