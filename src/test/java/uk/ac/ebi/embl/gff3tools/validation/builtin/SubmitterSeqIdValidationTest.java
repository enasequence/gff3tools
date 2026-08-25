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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.directives.GFF3SequenceRegion;

class SubmitterSeqIdValidationTest {

    private static final int LINE = 42;

    private SubmitterSeqIdValidation validation;

    @BeforeEach
    void setUp() {
        validation = new SubmitterSeqIdValidation();
    }

    private static GFF3Annotation annotationWithSeqId(String seqId) {
        return annotationWithSeqId(seqId, Optional.empty());
    }

    private static GFF3Annotation annotationWithSeqId(String seqId, Optional<Integer> version) {
        GFF3Annotation annotation = new GFF3Annotation();
        annotation.setSequenceRegion(new GFF3SequenceRegion(seqId, version, 1, 1000));
        return annotation;
    }

    private static String repeat(int length) {
        return "A".repeat(length);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ChrI_RagTag", "NODE_1", "scaffold-1.2", "contig:7", "id*", "id#3", "a", "SEQ(1)", "x,y;z"})
    void acceptsPermittedSubmitterSeqIds(String seqId) {
        assertDoesNotThrow(() -> validation.validateSubmitterSeqIdFormat(annotationWithSeqId(seqId), LINE));
    }

    @Test
    void acceptsSeqIdOfExactlyFiftyCharacters() {
        assertDoesNotThrow(() -> validation.validateSubmitterSeqIdFormat(
                annotationWithSeqId(repeat(SubmitterSeqIdValidation.MAX_LENGTH)), LINE));
    }

    @Test
    void rejectsSeqIdOfFiftyOneCharacters() {
        GFF3Annotation annotation = annotationWithSeqId(repeat(SubmitterSeqIdValidation.MAX_LENGTH + 1));

        ValidationException exception = assertThrows(
                ValidationException.class, () -> validation.validateSubmitterSeqIdFormat(annotation, LINE));

        assertEquals(SubmitterSeqIdValidation.SUBMITTER_SEQ_ID_FORMAT_RULE, exception.getValidationRule());
        assertEquals(LINE, exception.getLine());
        assertTrue(exception.getMessage().contains("51 characters"), exception.getMessage());
    }

    @Test
    void ignoresSequenceVersionWhenMeasuringLength() {
        GFF3Annotation annotation = annotationWithSeqId(repeat(SubmitterSeqIdValidation.MAX_LENGTH), Optional.of(1));

        assertDoesNotThrow(() -> validation.validateSubmitterSeqIdFormat(annotation, LINE));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"seq 1", "seq\t1", "seq>1", "seq[1]", "seq[1", "seq]1", "seq|1", "seq\"1", " seq1", "seq1 "})
    void rejectsCharactersProhibitedByInsdc(String seqId) {
        GFF3Annotation annotation = annotationWithSeqId(seqId);

        ValidationException exception = assertThrows(
                ValidationException.class, () -> validation.validateSubmitterSeqIdFormat(annotation, LINE));

        assertEquals(SubmitterSeqIdValidation.SUBMITTER_SEQ_ID_FORMAT_RULE, exception.getValidationRule());
        assertTrue(exception.getMessage().contains("not permitted by INSDC"), exception.getMessage());
    }

    @Test
    void reportsEveryDistinctProhibitedCharacter() {
        GFF3Annotation annotation = annotationWithSeqId("a b>c[d]e|f\"g");

        ValidationException exception = assertThrows(
                ValidationException.class, () -> validation.validateSubmitterSeqIdFormat(annotation, LINE));

        assertTrue(exception.getMessage().contains("whitespace"), exception.getMessage());
        assertTrue(exception.getMessage().contains("'>'"), exception.getMessage());
        assertTrue(exception.getMessage().contains("'['"), exception.getMessage());
        assertTrue(exception.getMessage().contains("']'"), exception.getMessage());
        assertTrue(exception.getMessage().contains("'|'"), exception.getMessage());
        assertTrue(exception.getMessage().contains("'\"'"), exception.getMessage());
    }

    @Test
    void rejectsEmptySeqId() {
        GFF3Annotation annotation = annotationWithSeqId("");

        ValidationException exception = assertThrows(
                ValidationException.class, () -> validation.validateSubmitterSeqIdFormat(annotation, LINE));

        assertEquals(SubmitterSeqIdValidation.SUBMITTER_SEQ_ID_FORMAT_RULE, exception.getValidationRule());
        assertTrue(exception.getMessage().contains("must not be empty"), exception.getMessage());
    }

    @Test
    void acceptsAccessionLikeSeqIdBecauseThatIsADifferentRule() {
        assertDoesNotThrow(() -> validation.validateSubmitterSeqIdFormat(annotationWithSeqId("AB123456"), LINE));
    }
}
