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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import uk.ac.ebi.embl.gff3tools.TestUtils;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.FastaHeaderProvider;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.FastaHeaderSource;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.utils.FastaHeader;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;

public class ChromosomeNameFixTest {

    private static final String ACCESSION = "ACC1";

    private ChromosomeNameFix fix;

    @BeforeEach
    public void setUp() {
        fix = new ChromosomeNameFix();
    }

    /**
     * Wires the fix with a context exposing a single header (keyed by {@link #ACCESSION}) carrying the
     * given chromosome name, runs the fix against a matching annotation, and returns the (possibly)
     * normalised chromosome name.
     */
    private String runFix(String chromosomeName) {
        FastaHeader header = new FastaHeader();
        header.setChromosomeName(chromosomeName);
        injectContextWithHeaders(Map.of(ACCESSION, header));

        fix.fix(annotationFor(ACCESSION), 1);
        return header.getChromosomeName();
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
    public void removesWhitespace() {
        assertEquals("1A2B", runFix(" 1A\t2B \n"));
    }

    @Test
    public void replacesIllegalCharactersWithUnderscore() {
        assertEquals("a_b_c_d_e_f", runFix("a\\b/c|d=e;f"));
    }

    @Test
    public void trimsLeadingAndTrailingUnderscores() {
        assertEquals("name", runFix("___name___"));
    }

    @Test
    public void collapsesDuplicateUnderscoreRuns() {
        assertEquals("a_b_c", runFix("a__b___c"));
    }

    @ParameterizedTest
    @CsvSource({
        "chromosome1, 1",
        "CHROMOSOME1, 1",
        "chrom1, 1",
        "CHROM1, 1",
        "chrm1, 1",
        "chr1, 1",
        "CHR1, 1",
        "Chromosome 1, 1",
    })
    public void removesChromosomeKeywords(String input, String expected) {
        assertEquals(expected, runFix(input));
    }

    @ParameterizedTest
    @CsvSource({
        "linkage-group3, 3",
        "linkage group3, 3",
        "LINKAGE-GROUP3, 3",
        "Linkage Group 3, 3",
    })
    public void removesLinkageGroupKeywords(String input, String expected) {
        assertEquals(expected, runFix(input));
    }

    @Test
    public void removesPlasmidKeyword() {
        assertEquals("A", runFix("plasmidA"));
    }

    @Test
    public void removesPlasmidKeywordCaseInsensitive() {
        assertEquals("A", runFix("PLASMID A"));
    }

    @Test
    public void preservesPlasmidWithinMegaplasmid() {
        assertEquals("megaplasmid1", runFix("megaplasmid 1"));
    }

    @Test
    public void appliesAllRulesCombined() {
        // whitespace + keyword removal + illegal chars + collapse + trim
        assertEquals("1_A", runFix("  chromosome 1 |= A  "));
    }

    @Test
    public void leavesAlreadyCleanNameUnchanged() {
        assertEquals("scaffold42", runFix("scaffold42"));
    }

    @Test
    public void leavesNullChromosomeNameUnchanged() {
        assertNull(runFix(null));
    }

    @Test
    public void ignoresAnnotationWhenNoHeaderRegisteredForAccession() {
        injectContextWithHeaders(Map.of());
        assertDoesNotThrow(() -> fix.fix(annotationFor(ACCESSION), 1));
    }

    @Test
    public void ignoresAnnotationWhenNoFastaHeaderProviderRegistered() {
        TestUtils.injectContext(fix, new ValidationContext());
        assertDoesNotThrow(() -> fix.fix(annotationFor(ACCESSION), 1));
    }
}
