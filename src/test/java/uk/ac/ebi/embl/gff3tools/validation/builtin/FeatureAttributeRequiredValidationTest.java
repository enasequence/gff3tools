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
package uk.ac.ebi.embl.gff3tools.validation.builtin;

import java.util.HashMap;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.gff3tools.TestUtils;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;

public class FeatureAttributeRequiredValidationTest {

    private FeatureAttributeRequiredValidation validation;

    @BeforeEach
    public void setUp() {
        validation = new FeatureAttributeRequiredValidation();
    }

    public static <E> E getRandomEntryFromSet(Set<String> set) {
        Objects.requireNonNull(set, "set");
        if (set.isEmpty()) throw new NoSuchElementException("empty set");
        Object[] a = set.toArray();
        return (E) a[ThreadLocalRandom.current().nextInt(a.length)];
    }

    @Test
    public void testFeatureAttriuteRequiredValidationSuccess() {
        String featureName = getRandomEntryFromSet(validation.featuresToValidate);
        GFF3Feature feature = TestUtils.createGFF3Feature(featureName, ".", new HashMap<>() {
            {
                put("ID", "O1");
                put("Parent", "mom");
                put("attributeKey", "attributeValue");
            }
        });

        Assertions.assertDoesNotThrow(() -> validation.validateFeature(feature, 1));
    }

    @Test
    public void testFeatureAttriuteRequiredValidationFailure() {
        String featureName = getRandomEntryFromSet(validation.featuresToValidate);
        GFF3Feature feature = TestUtils.createGFF3Feature(featureName, new HashMap<>(){
            {
                put("ID", "O1");
                put("Parent", "mom");
            }
        });

        Assertions.assertThrows(ValidationException.class, () -> validation.validateFeature(feature, 1));
    }
}
