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
package uk.ac.ebi.embl.gff3tools.validation.fix;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.gff3tools.TestUtils;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.FastaHeaderProvider;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.FastaHeaderSource;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.utils.FastaHeader;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;

public class FastaHeaderNormalisationFixTest {

    private static final String ACCESSION = "ACC1";

    private FastaHeaderNormalisationFix fix;

    @BeforeEach
    public void setUp() {
        fix = new FastaHeaderNormalisationFix();
    }

    /** Wires the fix with a context exposing the given header, runs the vocabulary fix, and returns it. */
    private FastaHeader runVocabularyFix(FastaHeader header) {
        injectContextWithHeaders(Map.of(ACCESSION, header));
        fix.normaliseHeaderValues(annotationFor(ACCESSION), 1);
        return header;
    }

    private void injectContextWithHeaders(Map<String, FastaHeader> headersByAccession) {
        FastaHeaderProvider provider = new FastaHeaderProvider();
        provider.addSource(new FastaHeaderSource() {
            @Override
            public Optional<FastaHeader> getHeader(String seqId) {
                return Optional.ofNullable(headersByAccession.get(seqId));
            }
        });
        ValidationContext context = new ValidationContext();
        context.register(FastaHeaderProvider.class, provider);
        TestUtils.injectContext(fix, context);
    }

    private GFF3Annotation annotationFor(String accession) {
        GFF3Annotation annotation = new GFF3Annotation();
        GFF3Feature feature = TestUtils.createGFF3Feature("CDS", accession, 1L, 10L, new HashMap<>());
        annotation.addFeature(feature);
        return annotation;
    }

    @Test
    public void normalisesMoleculeTypeCase() {
        FastaHeader header = new FastaHeader();
        header.setMoleculeType("GENOMIC DNA");
        assertEquals("genomic DNA", runVocabularyFix(header).getMoleculeType());
    }

    @Test
    public void normalisesTopologyCase() {
        FastaHeader header = new FastaHeader();
        header.setTopology("Circular");
        assertEquals("circular", runVocabularyFix(header).getTopology());
    }

    @Test
    public void normalisesChromosomeLocationCase() {
        FastaHeader header = new FastaHeader();
        header.setChromosomeLocation("mitochondrion");
        assertEquals("Mitochondrion", runVocabularyFix(header).getChromosomeLocation());
    }

    @Test
    public void normalisesNuclearChromosomeLocationToAbsent() {
        // "nuclear" is not part of the INSDC /organelle vocabulary -- per the ENA assembly
        // submission docs, a nuclear chromosome_location is expressed by omitting the field.
        FastaHeader header = new FastaHeader();
        header.setChromosomeLocation("nuclear");
        assertNull(runVocabularyFix(header).getChromosomeLocation());
    }

    @Test
    public void normalisesNuclearChromosomeLocationToAbsentRegardlessOfCase() {
        FastaHeader header = new FastaHeader();
        header.setChromosomeLocation("NUCLEAR");
        assertNull(runVocabularyFix(header).getChromosomeLocation());
    }

    @Test
    public void normalisesChromosomeTypeDashAndCase() {
        FastaHeader header = new FastaHeader();
        header.setChromosomeType("Linkage-Group");
        assertEquals("linkage_group", runVocabularyFix(header).getChromosomeType());
    }

    @Test
    public void trimsEdgeWhitespaceBeforeMatching() {
        FastaHeader header = new FastaHeader();
        header.setTopology("  linear \t");
        assertEquals("linear", runVocabularyFix(header).getTopology());
    }

    @Test
    public void normalisesAllVocabularyFieldsTogether() {
        FastaHeader header = new FastaHeader();
        header.setMoleculeType("mrna");
        header.setTopology("LINEAR");
        header.setChromosomeType("plasmid");
        header.setChromosomeLocation("CHLOROPLAST");

        FastaHeader result = runVocabularyFix(header);

        assertEquals("mRNA", result.getMoleculeType());
        assertEquals("linear", result.getTopology());
        assertEquals("plasmid", result.getChromosomeType());
        assertEquals("Chloroplast", result.getChromosomeLocation());
    }

    @Test
    public void leavesDescriptionUntouched() {
        FastaHeader header = new FastaHeader();
        header.setDescription("My Genomic DNA Sample");
        header.setMoleculeType("genomic DNA");
        assertEquals("My Genomic DNA Sample", runVocabularyFix(header).getDescription());
    }

    @Test
    public void leavesUnknownValuesUntouched() {
        FastaHeader header = new FastaHeader();
        header.setMoleculeType("DNA");
        header.setTopology("triangle");
        FastaHeader result = runVocabularyFix(header);
        assertEquals("DNA", result.getMoleculeType());
        assertEquals("triangle", result.getTopology());
    }

    @Test
    public void leavesNullFieldsUntouched() {
        FastaHeader header = new FastaHeader();
        header.setMoleculeType("genomic DNA");
        FastaHeader result = runVocabularyFix(header);
        assertNull(result.getTopology());
        assertNull(result.getChromosomeType());
        assertNull(result.getChromosomeLocation());
    }

    // ----------------------- ASCII7 folding edge cases -----------------------

    @Test
    public void foldsDiacriticsThenMatchesVocabulary() {
        // ASCII folding (é -> e) runs first, letting the value match the controlled vocabulary.
        FastaHeader header = new FastaHeader();
        header.setMoleculeType("génomic DNA");
        assertEquals("genomic DNA", runVocabularyFix(header).getMoleculeType());
    }

    @Test
    public void foldsDiacriticsCaseAndDashTogether() {
        // Diacritic (ñ -> n) + dash->underscore + case-insensitive match all compose.
        FastaHeader header = new FastaHeader();
        header.setChromosomeType("Liñkage-Group");
        assertEquals("linkage_group", runVocabularyFix(header).getChromosomeType());
    }

    @Test
    public void foldsTopologyDiacriticAndCase() {
        FastaHeader header = new FastaHeader();
        header.setTopology("Línear");
        assertEquals("linear", runVocabularyFix(header).getTopology());
    }

    @Test
    public void replacesNonLatin1CharactersWithQuestionMark() {
        // Characters beyond Latin-1 (here Greek alpha) become '?'.
        FastaHeader header = new FastaHeader();
        header.setDescription("Genome α");
        assertEquals("Genome ?", runVocabularyFix(header).getDescription());
    }

    @Test
    public void replacesSmartQuoteWithQuestionMark() {
        FastaHeader header = new FastaHeader();
        header.setDescription("John’s genome");
        assertEquals("John?s genome", runVocabularyFix(header).getDescription());
    }

    @Test
    public void mapsSpecialLatinLettersToAscii() {
        // Æ -> A and ß -> s via the converter's explicit character mapping.
        FastaHeader header = new FastaHeader();
        header.setDescription("Ælfred Straße");
        assertEquals("Alfred Strase", runVocabularyFix(header).getDescription());
    }

    @Test
    public void stripsNonPrintableControlCharacters() {
        // A lone ASCII control char (BEL, 0x07) triggers conversion and is stripped.
        FastaHeader header = new FastaHeader();
        header.setDescription("abcd");
        assertEquals("abcd", runVocabularyFix(header).getDescription());
    }

    @Test
    public void foldsChromosomeNameEvenThoughItIsNotControlledVocabulary() {
        FastaHeader header = new FastaHeader();
        header.setChromosomeName("chrömosome1");
        assertEquals("chromosome1", runVocabularyFix(header).getChromosomeName());
    }

    @Test
    public void leavesCleanAsciiValuesUnchanged() {
        FastaHeader header = new FastaHeader();
        header.setDescription("Plain text 123");
        header.setMoleculeType("genomic DNA");
        FastaHeader result = runVocabularyFix(header);
        assertEquals("Plain text 123", result.getDescription());
        assertEquals("genomic DNA", result.getMoleculeType());
    }

    @Test
    public void leavesEmptyStringUnchanged() {
        FastaHeader header = new FastaHeader();
        header.setMoleculeType("");
        assertEquals("", runVocabularyFix(header).getMoleculeType());
    }

    @Test
    public void leavesNonVocabularyValueWithInternalWhitespaceUntouched() {
        // canonicalise only trims edges; an unmatched value keeps its internal spacing.
        FastaHeader header = new FastaHeader();
        header.setChromosomeType("not a type");
        assertEquals("not a type", runVocabularyFix(header).getChromosomeType());
    }

    // ----------------------------- guard cases -------------------------------

    @Test
    public void ignoresAnnotationWhenNoHeaderRegisteredForAccession() {
        injectContextWithHeaders(Map.of());
        assertDoesNotThrow(() -> fix.normaliseHeaderValues(annotationFor(ACCESSION), 1));
    }

    @Test
    public void ignoresAnnotationWhenNoFastaHeaderProviderRegistered() {
        TestUtils.injectContext(fix, new ValidationContext());
        assertDoesNotThrow(() -> fix.normaliseHeaderValues(annotationFor(ACCESSION), 1));
    }
}
