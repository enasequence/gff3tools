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
package uk.ac.ebi.embl.gff3tools.cli;

import static org.junit.jupiter.api.Assertions.*;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import picocli.CommandLine;

/**
 * Flat file to GFF3 conversion of a circular entry, whose topology is declared only on the ID line.
 * The sequence is supplied as plain sequence, so no FASTA header can declare the topology instead.
 *
 * <p>Each case runs the circular entry, then the same entry declared linear: the linear run must fail,
 * and pass again with the rule under test switched off, so the rule under test is the only reason the
 * linear run fails and the circular run has nothing else to trip on.
 */
class FFToGFF3CircularityIntegrationTest {

    private static final String ACCESSION = "TST01.1";

    @TempDir
    Path tempDir;

    static Stream<Arguments> circularityCases() {
        return Stream.of(
                Arguments.of("origin_spanning_join.embl", "sequence.seq", "JOINED_LOCATION_ORDER"),
                Arguments.of("end_past_sequence_length.embl", "sequence.seq", "FEATURE_END_EXCEEDS_SEQUENCE_LENGTH"),
                Arguments.of("terminal_gaps.embl", "terminal_gaps.seq", "NO_TERMINAL_GAPS"));
    }

    @ParameterizedTest(name = "{2}")
    @MethodSource("circularityCases")
    void circularFlatFileTopologyReachesValidation(String emblResource, String sequenceResource, String rule)
            throws Exception {
        Path circularEmbl = getResourcePath("fftogff3_circularity/" + emblResource);
        Path sequence = getResourcePath("fftogff3_circularity/" + sequenceResource);
        Path linearEmbl = tempDir.resolve("linear-" + emblResource);
        Files.writeString(linearEmbl, Files.readString(circularEmbl).replace("; circular;", "; linear;"));

        assertNotEquals(0, convert(linearEmbl, sequence, null), "Linear entry should violate " + rule);
        assertEquals(
                0, convert(linearEmbl, sequence, rule), "Linear entry should only violate " + rule + ", nothing else");
        assertEquals(0, convert(circularEmbl, sequence, null), "Circular entry should not violate " + rule);
    }

    private int convert(Path embl, Path sequence, String ruleOff) {
        List<String> args = new ArrayList<>(List.of("conversion"));
        if (ruleOff != null) {
            args.addAll(List.of("--rules", ruleOff + ":OFF"));
        }
        args.addAll(List.of(
                "--sequence",
                ACCESSION + ":" + sequence,
                embl.toString(),
                tempDir.resolve("out.gff3").toString()));

        CommandLine command = new CommandLine(new Main()).registerConverter(CliRulesOption.class, new RuleConverter());
        command.setErr(new PrintWriter(new StringWriter()));
        command.setOut(new PrintWriter(new StringWriter()));
        return command.execute(args.toArray(String[]::new));
    }

    private Path getResourcePath(String resourceName) {
        URL resource = Thread.currentThread().getContextClassLoader().getResource(resourceName);
        assertNotNull(resource, "Resource not found: " + resourceName);
        return Paths.get(resource.getPath());
    }
}
