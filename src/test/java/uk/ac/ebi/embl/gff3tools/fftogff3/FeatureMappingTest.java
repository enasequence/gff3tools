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

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.api.entry.feature.Feature;
import uk.ac.ebi.embl.api.entry.feature.FeatureFactory;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;

class FeatureMappingTest {

    private final FeatureFactory featureFactory = new FeatureFactory();

    @Test
    void unmappedFeatureThrowsValidationExceptionNotNPE() {
        Feature feature = featureFactory.createFeature("precursor_RNA");

        ValidationException exception =
                assertThrows(ValidationException.class, () -> FeatureMapping.getGFF3FeatureName(feature));
        assertTrue(
                exception.getMessage().contains("precursor_RNA"),
                "Exception message should contain the unmapped feature name");
    }

    @Test
    void completelyUnknownFeatureThrowsValidationException() {
        Feature feature = featureFactory.createFeature("totally_invented_feature");

        ValidationException exception =
                assertThrows(ValidationException.class, () -> FeatureMapping.getGFF3FeatureName(feature));
        assertTrue(
                exception.getMessage().contains("totally_invented_feature"),
                "Exception message should contain the unmapped feature name");
    }

    @Test
    void mappedFeatureReturnsSOTerm() throws ValidationException {
        Feature feature = featureFactory.createFeature("CDS");

        String soTerm = FeatureMapping.getGFF3FeatureName(feature);
        assertNotNull(soTerm, "Mapped feature should return a non-null SO term");
        assertEquals("CDS", soTerm);
    }
}
