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

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.gff3tools.utils.OntologyClient;
import uk.ac.ebi.embl.gff3tools.validation.ProviderScope;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;

class OntologyClientProviderTest {

    private OntologyClientProvider provider;
    private ValidationContext context;

    @BeforeEach
    void setUp() {
        provider = new OntologyClientProvider();
        context = new ValidationContext();
    }

    @Test
    @DisplayName("scope() returns GLOBAL")
    void scope_returnsGlobal() {
        assertEquals(ProviderScope.GLOBAL, provider.scope());
    }

    @Test
    @DisplayName("get() returns a non-null OntologyClient")
    void get_returnsNonNull() {
        OntologyClient client = provider.get(context);
        assertNotNull(client);
    }

    @Test
    @DisplayName("get() returns the same cached instance on repeated calls")
    void get_returnsCachedInstance() {
        OntologyClient first = provider.get(context);
        OntologyClient second = provider.get(context);
        assertSame(first, second, "Repeated get() calls must return the same cached instance");
    }

    @Test
    @DisplayName("invalidate() is a no-op -- get() still returns the same instance")
    void invalidate_isNoOp() {
        OntologyClient before = provider.get(context);
        provider.invalidate();
        OntologyClient after = provider.get(context);
        assertSame(before, after, "invalidate() should be a no-op for OntologyClientProvider");
    }
}
