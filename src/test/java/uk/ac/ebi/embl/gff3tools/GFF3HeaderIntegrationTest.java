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
package uk.ac.ebi.embl.gff3tools;

import static org.junit.jupiter.api.Assertions.*;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import picocli.CommandLine;
import uk.ac.ebi.embl.gff3tools.cli.Main;

class GFF3HeaderIntegrationTest {

    private Path getResourcePath(String resourceName) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        URL resource = classLoader.getResource(resourceName);
        assertNotNull(resource, "Resource not found: " + resourceName);
        return Paths.get(resource.getPath());
    }

    static Stream<Arguments> headerTestCases() {
        return Stream.of(
                Arguments.of(
                        "gff3toff_header_tests/fasta_header_basic.gff3",
                        "sequence/fasta/header_test.json",
                        "gff3toff_header_tests/fasta_header_basic.embl",
                        "linear basic header"),
                Arguments.of(
                        "gff3toff_header_tests/fasta_header_circular.gff3",
                        "sequence/fasta/header_circular.json",
                        "gff3toff_header_tests/fasta_header_circular.embl",
                        "circular header"),
                Arguments.of(
                        "gff3toff_header_tests/fasta_header_chromosome.gff3",
                        "sequence/fasta/header_chromosome.json",
                        "gff3toff_header_tests/fasta_header_chromosome.embl",
                        "chromosome header"),
                Arguments.of(
                        "gff3toff_header_tests/fasta_header_plasmid_mito.gff3",
                        "sequence/fasta/header_plasmid_mito.json",
                        "gff3toff_header_tests/fasta_header_plasmid_mito.embl",
                        "plasmid + mitochondrion header"),
                Arguments.of(
                        "gff3toff_header_tests/circular_origin_spanning.gff3",
                        "sequence/fasta/header_circular.json",
                        "gff3toff_header_tests/circular_origin_spanning.embl",
                        "origin-spanning features written past the sequence end"),
                // The same features with one line per segment, segments after the origin in virtual
                // space (INSDC GFF3): both forms must map to the same flat file.
                Arguments.of(
                        "gff3toff_header_tests/circular_origin_spanning_virtual_segments.gff3",
                        "sequence/fasta/header_circular.json",
                        "gff3toff_header_tests/circular_origin_spanning.embl",
                        "origin-spanning features written as one line per segment in virtual space"),
                // INSDC GFF3 specification, Figure 3: the last segment lies wholly past the origin.
                Arguments.of(
                        "gff3toff_header_tests/circular_origin_spanning_figure3.gff3",
                        "sequence/fasta/header_circular.json",
                        "gff3toff_header_tests/circular_origin_spanning_figure3.embl",
                        "multi-exon CDS whose last segment lies wholly past the origin"),
                // Round trip: the GFF3 that flat file to GFF3 writes for fftogff3_rules/circular_virtual_space.embl
                // maps back to the original locations. The one difference is notation: a join of
                // complemented pieces comes back as the equivalent complement(join(...)).
                Arguments.of(
                        "fftogff3_rules/circular_virtual_space.gff3",
                        "sequence/fasta/header_circular.json",
                        "gff3toff_header_tests/circular_virtual_space_round_trip.embl",
                        "flat file to GFF3 virtual space maps back to the original locations"),
                Arguments.of(
                        "gff3toff_header_tests/circular_origin_lapping_start.gff3",
                        "sequence/fasta/header_circular.json",
                        "gff3toff_header_tests/circular_origin_lapping_start.embl",
                        "feature lapping its own start is not split"));
    }

    @ParameterizedTest(name = "{3}")
    @MethodSource("headerTestCases")
    void testWriteEMBLWithFastaHeader(
            String gff3Resource, String headerJsonResource, String expectedEmblResource, String label)
            throws Exception {
        Path gff3 = getResourcePath(gff3Resource);
        Path headerJson = getResourcePath(headerJsonResource);
        Path outFile = Files.createTempFile("header-test-", ".embl");

        try {
            String[] args = {"conversion", "--fasta-header", headerJson.toString(), gff3.toString(), outFile.toString()
            };
            StringWriter err = new StringWriter();
            StringWriter out = new StringWriter();
            CommandLine command = new CommandLine(new Main());
            command.setErr(new PrintWriter(err));
            command.setOut(new PrintWriter(out));

            int exitCode = command.execute(args);
            assertEquals(0, exitCode, "Wrong exit code.\nout: " + out + "\nerr: " + err);

            String actual = Files.readString(outFile);
            String expected = Files.readString(getResourcePath(expectedEmblResource));
            assertEquals(expected.trim(), actual.trim(), "EMBL output mismatch for " + label);
        } finally {
            Files.deleteIfExists(outFile);
        }
    }

    // ---- CDS translation on a circular sequence, end to end -------------------------------

    // Two 120 bp circular sequences (topology from the FASTA's embedded headers), each with a 26 aa
    // CDS (M + 25 A) crossing the origin: on SEQID1 a single line 91..171 (+); on SEQID2 two lines
    // 91..120 and 133..183 (-), the second wholly past the origin after a 12 nt intron.
    private static final String CIRCULAR_CDS_GFF3 = "gff3toff_header_tests/circular_cds_translation.gff3";
    private static final String CIRCULAR_CDS_FASTA = "gff3toff_header_tests/circular_cds_translation.fasta";
    private static final String CIRCULAR_CDS_PROTEIN = "M" + "A".repeat(25);

    @Test
    void writesOriginSpanningCdsFromCircularFasta() throws Exception {
        Path outFile = Files.createTempFile("circular-cds-", ".embl");
        try {
            int exitCode = convert(
                    "--sequence",
                    getResourcePath(CIRCULAR_CDS_FASTA).toString(),
                    getResourcePath(CIRCULAR_CDS_GFF3).toString(),
                    outFile.toString());
            assertEquals(0, exitCode);

            String expected = Files.readString(getResourcePath("gff3toff_header_tests/circular_cds_translation.embl"));
            assertEquals(expected.trim(), Files.readString(outFile).trim());
        } finally {
            Files.deleteIfExists(outFile);
        }
    }

    /**
     * The protein translated from the sequence is compared with a translation attribute, so a wrong
     * one fails the conversion: the CDSs are read straight through the origin.
     */
    @ParameterizedTest(name = "translation {0} accepted: {1}")
    @CsvSource({"MAAAAAAAAAAAAAAAAAAAAAAAAA, true", "MAAAAAAAAAAAAAAAAAAAAAAAAG, false"})
    void comparesTranslationOfOriginSpanningCds(String translation, boolean accepted) throws Exception {
        assertEquals(CIRCULAR_CDS_PROTEIN.length(), translation.length());
        Path gff3 = Files.createTempFile("circular-cds-", ".gff3");
        Path outFile = Files.createTempFile("circular-cds-", ".embl");
        try {
            String withoutFasta =
                    Files.readString(getResourcePath(CIRCULAR_CDS_GFF3)).split("##FASTA")[0];
            Files.writeString(
                    gff3, withoutFasta.replace("transl_table=11;", "transl_table=11;translation=" + translation + ";"));

            int exitCode = convert(
                    "--sequence", getResourcePath(CIRCULAR_CDS_FASTA).toString(), gff3.toString(), outFile.toString());
            if (accepted) {
                assertEquals(0, exitCode);
            } else {
                assertNotEquals(0, exitCode);
            }
        } finally {
            Files.deleteIfExists(gff3);
            Files.deleteIfExists(outFile);
        }
    }

    private int convert(String... options) {
        String[] args = new String[options.length + 1];
        args[0] = "conversion";
        System.arraycopy(options, 0, args, 1, options.length);
        CommandLine command = new CommandLine(new Main());
        command.setErr(new PrintWriter(new StringWriter()));
        command.setOut(new PrintWriter(new StringWriter()));
        return command.execute(args);
    }
}
