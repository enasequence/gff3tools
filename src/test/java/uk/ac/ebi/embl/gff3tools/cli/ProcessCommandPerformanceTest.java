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

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Performance and stress tests for process sub-commands.
 * Tagged as 'performance' to allow selective execution.
 */
@Tag("performance")
public class ProcessCommandPerformanceTest {

    private ByteArrayOutputStream outContent;
    private ByteArrayOutputStream errContent;
    private PrintStream originalOut;
    private PrintStream originalErr;

    @BeforeEach
    void setUp() {
        outContent = new ByteArrayOutputStream();
        errContent = new ByteArrayOutputStream();
        originalOut = System.out;
        originalErr = System.err;
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    @Test
    void testLargeFileMemoryEfficiency() throws IOException {
        // Create a large GFF3 file with 10,000 sequence regions and 100,000 features
        // This tests R1: count operation must use streaming
        Path largeFile = Files.createTempFile("large_test", ".gff3");

        try (BufferedWriter writer = Files.newBufferedWriter(largeFile)) {
            writer.write("##gff-version 3\n");

            // Write 10,000 sequence regions
            for (int i = 1; i <= 10000; i++) {
                writer.write(String.format("##sequence-region SEQ%05d 1 %d\n", i, i * 100));
            }

            // Write 100,000 features (10 per sequence region)
            for (int i = 1; i <= 10000; i++) {
                String seqId = String.format("SEQ%05d", i);
                for (int j = 1; j <= 10; j++) {
                    int start = j * 100;
                    int end = start + 99;
                    writer.write(
                            String.format("%s\tENA\tgene\t%d\t%d\t.\t+\t.\tID=gene_%d_%d\n", seqId, start, end, i, j));
                }
            }
        }

        try {
            // Test counting (should be fast and memory-efficient)
            CountRegionsCommand countCmd = new CountRegionsCommand();
            countCmd.inputFilePath = largeFile;

            long startTime = System.currentTimeMillis();
            long startMemory =
                    Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

            assertDoesNotThrow(() -> countCmd.run());

            long endTime = System.currentTimeMillis();
            long endMemory =
                    Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

            long elapsedTime = endTime - startTime;
            long memoryUsed = endMemory - startMemory;

            // Count should complete in reasonable time (< 5 seconds for 10K regions)
            assertTrue(elapsedTime < 5000, "Counting 10,000 regions took " + elapsedTime + "ms, expected < 5000ms");

            // Memory usage should be minimal (< 100MB for streaming)
            assertTrue(
                    memoryUsed < 100 * 1024 * 1024,
                    "Memory usage was " + (memoryUsed / 1024 / 1024) + "MB, expected < 100MB");

        } finally {
            Files.deleteIfExists(largeFile);
        }
    }

    @Test
    void testReplaceIdsLargeFile() throws IOException {
        // Test replace-ids with 1,000 sequence regions
        Path largeFile = Files.createTempFile("large_replace", ".gff3");
        Path outputFile = Files.createTempFile("large_output", ".gff3");

        StringBuilder accessions = new StringBuilder();

        try (BufferedWriter writer = Files.newBufferedWriter(largeFile)) {
            writer.write("##gff-version 3\n");

            for (int i = 1; i <= 1000; i++) {
                writer.write(String.format("##sequence-region SEQ%04d 1 %d\n", i, i * 1000));

                // Add some features
                for (int j = 1; j <= 5; j++) {
                    writer.write(String.format(
                            "SEQ%04d\tENA\tgene\t%d\t%d\t.\t+\t.\tID=gene_%d_%d\n", i, j * 100, j * 100 + 99, i, j));
                }

                // Build accessions list
                if (i > 1) accessions.append(",");
                accessions.append(String.format("ACC%04d", i));
            }
        }

        try {
            ReplaceIdsCommand replaceCmd = new ReplaceIdsCommand();
            replaceCmd.inputFilePath = largeFile;
            replaceCmd.outputFilePath = outputFile.toString();
            replaceCmd.accessions =
                    java.util.Arrays.asList(accessions.toString().split(","));

            long startTime = System.currentTimeMillis();
            assertDoesNotThrow(() -> replaceCmd.run());
            long elapsedTime = System.currentTimeMillis() - startTime;

            // Should complete in reasonable time (< 10 seconds for 1000 regions)
            assertTrue(elapsedTime < 10000, "Replacing 1,000 regions took " + elapsedTime + "ms, expected < 10000ms");

            // Verify output file
            String output = Files.readString(outputFile);
            assertTrue(output.contains("##sequence-region ACC0001"));
            assertTrue(output.contains("##sequence-region ACC1000"));
            assertFalse(output.contains("SEQ0001\t"));
            assertTrue(output.contains("ACC0001\t"));

        } finally {
            Files.deleteIfExists(largeFile);
            Files.deleteIfExists(outputFile);
        }
    }
}
