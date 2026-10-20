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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import uk.ac.ebi.embl.api.entry.genomeassembly.AssemblyType;
import uk.ac.ebi.embl.gff3tools.validation.provider.AnalysisType;

public class AssemblyTypeValidatorTest {

    /** Every value of the vocabulary resolves from its canonical spelling. */
    @ParameterizedTest
    @EnumSource(AssemblyType.class)
    public void testCanonicalSpellingResolves(AssemblyType assemblyType) {
        assertEquals(
                Optional.of(assemblyType),
                AssemblyTypeValidator.parseAndValidate(AnalysisType.SEQUENCE_ASSEMBLY, assemblyType.getFixedValue()));
    }

    /** The manifest carries the upper-case spelling, which must resolve to the same value. */
    @ParameterizedTest
    @EnumSource(AssemblyType.class)
    public void testManifestSpellingResolves(AssemblyType assemblyType) {
        assertEquals(
                Optional.of(assemblyType),
                AssemblyTypeValidator.parseAndValidate(AnalysisType.SEQUENCE_ASSEMBLY, assemblyType.getValue()));
    }

    @ParameterizedTest
    @CsvSource({
        "'Clone Or Isolate', CLONEORISOLATE",
        "'  primary metagenome  ', PRIMARYMETAGENOME",
        "'BINNED metagenome', BINNEDMETAGENOME",
        "'metagenome-assembled genome (mag)', METAGENOME_ASSEMBLEDGENOME",
        "'covid-19 OUTBREAK', COVID_19_OUTBREAK"
    })
    public void testMatchingIgnoresCaseAndSurroundingWhitespace(String rawValue, AssemblyType expected) {
        assertEquals(
                Optional.of(expected),
                AssemblyTypeValidator.parseAndValidate(AnalysisType.SEQUENCE_ASSEMBLY, rawValue));
    }

    /** A caller that always forwards the manifest field must not have to distinguish these. */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    public void testUnsuppliedValueIsEmpty(String rawValue) {
        assertEquals(
                Optional.empty(), AssemblyTypeValidator.parseAndValidate(AnalysisType.SEQUENCE_ASSEMBLY, rawValue));
    }

    /** An unsupplied value is not a violation of the SEQUENCE_ASSEMBLY rule either. */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    public void testUnsuppliedValueIsEmptyForEveryAnalysisType(String rawValue) {
        for (AnalysisType analysisType : AnalysisType.values()) {
            assertEquals(Optional.empty(), AssemblyTypeValidator.parseAndValidate(analysisType, rawValue));
        }
    }

    @ParameterizedTest
    @CsvSource({"clone or isolat", "CLONEORISOLATE", "isolate", "'primary  metagenome'", "1"})
    public void testUnknownValueIsRejected(String rawValue) {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> AssemblyTypeValidator.parseAndValidate(AnalysisType.SEQUENCE_ASSEMBLY, rawValue));

        assertTrue(ex.getMessage().contains("Unknown assemblyType \"%s\"".formatted(rawValue)));
        // The message must name the alternatives, otherwise the caller cannot correct the manifest.
        assertTrue(ex.getMessage().contains(AssemblyType.CLONEORISOLATE.getFixedValue()));
        assertTrue(ex.getMessage().contains(AssemblyType.ENVIRONMENTALSINGLE_CELLAMPLIFIEDGENOME.getFixedValue()));
    }

    /** Only a sequence assembly has an assembly type; anything else means the caller is confused. */
    @ParameterizedTest
    @EnumSource(
            value = AnalysisType.class,
            names = {"SEQUENCE_ASSEMBLY"},
            mode = EnumSource.Mode.EXCLUDE)
    public void testValueSuppliedForOtherAnalysisTypeIsRejected(AnalysisType analysisType) {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> AssemblyTypeValidator.parseAndValidate(analysisType, "clone or isolate"));

        assertTrue(ex.getMessage()
                .contains("assemblyType \"clone or isolate\" must not be supplied for analysis type %s"
                        .formatted(analysisType)));
    }

    /**
     * The analysis type is checked before the value, so a caller supplying an assembly type where
     * none belongs is told that, rather than being sent to correct a spelling that should not be
     * there at all.
     */
    @Test
    public void testAnalysisTypeIsRejectedAheadOfAnUnknownValue() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> AssemblyTypeValidator.parseAndValidate(AnalysisType.TRANSCRIPTOME_ASSEMBLY, "not a real type"));

        assertTrue(ex.getMessage().contains("must not be supplied for analysis type"));
    }
}
