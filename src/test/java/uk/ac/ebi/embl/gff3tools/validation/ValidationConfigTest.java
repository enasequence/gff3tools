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
package uk.ac.ebi.embl.gff3tools.validation;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.gff3tools.validation.meta.RuleSeverity;

class ValidationConfigTest {

    @Test
    void loadDefaultRoundTripsAgainstThePropertiesFile() {
        ValidationConfig config = ValidationConfig.loadDefault();

        assertEquals(RuleSeverity.ERROR, config.getSeverity("FLATFILE_NO_SOURCE", RuleSeverity.WARN));
        assertEquals(RuleSeverity.WARN, config.getSeverity("FLATFILE_NO_ONTOLOGY_FEATURE", RuleSeverity.ERROR));
        assertEquals(RuleSeverity.WARN, config.getSeverity("GFF3_EXON_LENGTH_VALIDATION", RuleSeverity.ERROR));
        assertTrue(config.getFix("SOME_EXAMPLE_FIX", false));
        assertFalse(config.getValidatorOverrides().isEmpty());
        assertTrue(config.getValidatorOverrides().getOrDefault("DUPLICATE_FEATURE_VALIDATION", false));
    }

    @Test
    void loadDefaultProducesAFreshMutableConfigEachCall() {
        ValidationConfig first = ValidationConfig.loadDefault();
        first.getRuleOverrides().put("SOME_TEST_ONLY_RULE", RuleSeverity.OFF);

        ValidationConfig second = ValidationConfig.loadDefault();
        assertFalse(second.getRuleOverrides().containsKey("SOME_TEST_ONLY_RULE"));
    }
}
