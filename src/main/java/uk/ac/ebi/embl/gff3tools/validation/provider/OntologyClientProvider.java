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

import uk.ac.ebi.embl.gff3tools.utils.OntologyClient;
import uk.ac.ebi.embl.gff3tools.validation.ContextProvider;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;

/**
 * Provider that supplies the shared {@link OntologyClient} instance.
 *
 * <p>The ontology client is created once and cached for the lifetime of the validation run.
 */
public class OntologyClientProvider implements ContextProvider<OntologyClient> {

    private OntologyClient cached;

    @Override
    public OntologyClient get(ValidationContext context) {
        if (cached == null) {
            cached = OntologyClient.getInstance();
        }
        return cached;
    }
}
