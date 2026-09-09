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

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ParameterDescriptorsTest {

    @Test
    void scanFindsAnnotatedParametersFromFixture() {
        List<ParameterDescriptor> descriptors = ParameterDescriptors.scan();

        Optional<ParameterDescriptor> longParam = descriptors.stream()
                .filter(d -> d.key().equals("PARAM_FIXTURE_LONG.MIN_AMINO_ACIDS"))
                .findFirst();
        assertTrue(longParam.isPresent());
        assertEquals("PARAM_FIXTURE_LONG", longParam.get().rule());
        assertEquals("MIN_AMINO_ACIDS", longParam.get().name());
        assertEquals(ParameterType.LONG, longParam.get().type());
        assertEquals("25", longParam.get().defaultValue());
        assertFalse(longParam.get().mandatory());

        Optional<ParameterDescriptor> stringParam = descriptors.stream()
                .filter(d -> d.key().equals("PARAM_FIXTURE_STRING.LABEL"))
                .findFirst();
        assertTrue(stringParam.isPresent());

        Optional<ParameterDescriptor> mandatoryParam = descriptors.stream()
                .filter(d -> d.key().equals("PARAM_FIXTURE_MANDATORY.MANDATORY_LABEL"))
                .findFirst();
        assertTrue(mandatoryParam.isPresent());
        assertTrue(mandatoryParam.get().mandatory());
    }

    @Test
    void nonStringOptionalParameterWithNoDefaultIsBuildTimeDefect() throws Exception {
        Parameter noDefault = BadFixture.class.getDeclaredMethod("noDefault").getAnnotation(Parameter.class);
        assertThrows(
                IllegalStateException.class,
                () -> ParameterDescriptors.buildDescriptor(
                        "BAD_RULE", noDefault, BadFixture.class, BadFixture.class.getDeclaredMethod("noDefault")));

        Parameter badDefault = BadFixture.class.getDeclaredMethod("badDefault").getAnnotation(Parameter.class);
        assertThrows(
                IllegalStateException.class,
                () -> ParameterDescriptors.buildDescriptor(
                        "BAD_RULE", badDefault, BadFixture.class, BadFixture.class.getDeclaredMethod("badDefault")));
    }

    private static final class BadFixture {
        @Parameter(name = "NO_DEFAULT", type = ParameterType.LONG)
        void noDefault() {}

        @Parameter(name = "BAD_DEFAULT", type = ParameterType.LONG, defaultValue = "not-a-number")
        void badDefault() {}
    }
}
