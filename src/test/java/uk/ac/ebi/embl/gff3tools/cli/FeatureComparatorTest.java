package uk.ac.ebi.embl.gff3tools.cli;

import org.junit.jupiter.api.*;
import uk.ac.ebi.embl.api.validation.helper.FlatFileComparatorException;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FeatureComparatorTest {

    private Path expectedFile;
    private Path actualFile;
    private Path ignoreFile;
    private Path expectedFileWithIgnoredLine;
    private Path actualFileWithIgnoredLine;


    @BeforeEach
    void setup() throws IOException {
        // Create temporary expected file
        expectedFile = Files.createTempFile("expected", ".embl");
        try (BufferedWriter writer = Files.newBufferedWriter(expectedFile)) {
            writer.write("ID   BN000065; SV 1; circular; genomic DNA; STD; HUM; 315242 BP.");
            writer.write("XX");
            writer.write("IAC   BN000065;");
            writer.write("XX");
            writer.write("FT   source          1..315242");
            writer.write("FT                   /mol_type=\"genomic DNA\"");
        }

        // Create temporary actual file (identical content)
        actualFile = Files.createTempFile("actual", ".embl");
        try (BufferedWriter writer = Files.newBufferedWriter(actualFile)) {
            writer.write("ID   BN000065; SV 1; circular; genomic DNA; STD; HUM; 315242 BP.");
            writer.write("XX");
            writer.write("IAC   BN000065;");
            writer.write("XX");
            writer.write("FT   source          1..315242");
            writer.write("FT                   /mol_type=\"genomic DNA\"");
        }

        // Create temporary expected file
        expectedFileWithIgnoredLine = Files.createTempFile("expectedFileWithIgnoredLine", ".embl");
        try (BufferedWriter writer = Files.newBufferedWriter(expectedFileWithIgnoredLine)) {

            writer.write("ID   BN000065; SV 1; circular; genomic DNA; STD; HUM; 315242 BP.");
            writer.write("XX");
            writer.write("IAC   BN000065;");
            writer.write("XX");
            writer.write("FT   source          1..315242");
            writer.write("FT                   /mol_type=\"genomic DNA\"");
            writer.write("FT                   /note=\"TIGR:TIGR03474; incFII_RepA: incFII family plasmid\"\n");
            writer.write("FT                   /note=\"TIGR:TIGR03474; incFII_RepA: incFII family XXX\"\n");
            writer.write("//");
        }

        // Create temporary actual file (identical content)
        actualFileWithIgnoredLine = Files.createTempFile("actualFileWithIgnoredLine", ".embl");
        try (BufferedWriter writer = Files.newBufferedWriter(actualFileWithIgnoredLine)) {
            writer.write("ID   BN000065; SV 1; circular; genomic DNA; STD; HUM; 315242 BP.");
            writer.write("XX");
            writer.write("IAC   BN000065;");
            writer.write("XX");
            writer.write("FT   source          1..315242");
            writer.write("FT                   /mol_type=\"genomic DNA\"");
            writer.write("FT                   /note=\"TIGR:TIGR03474; incFII_RepA: incFII family plasmid\"\n");
            writer.write("FT                   /note=\"TIGR:TIGR03474; incFII_RepA: incFII family YYY\"\n");
            writer.write("//");
        }


        // Optional: ignore file
        ignoreFile = Files.createTempFile("ignore", ".txt");
        try (BufferedWriter writer = Files.newBufferedWriter(ignoreFile)) {
            writer.write("FT                   /note=\"TIGR:TIGR03474; incFII_RepA: incFII family XXX\"\n");
            writer.write("FT                   /note=\"TIGR:TIGR03474; incFII_RepA: incFII family YYY\"\n");
            writer.write("//");
        }
    }

    @AfterEach
    void cleanup() throws IOException {
        Files.deleteIfExists(expectedFile);
        Files.deleteIfExists(actualFile);
        Files.deleteIfExists(expectedFileWithIgnoredLine);
        Files.deleteIfExists(actualFileWithIgnoredLine);
        Files.deleteIfExists(ignoreFile);
    }

    @Test
    void testCompareFilesIdentical() {
        assertDoesNotThrow(() -> FeatureComparator.compare(
                expectedFile.toString(),
                actualFile.toString(),
                List.of() // no extra ignore lines
        ));
    }

    @Test
    void testCompareFilesWithIgnoreLines() throws IOException {
        List<String> ignoreLines = Files.readAllLines(ignoreFile);
        assertDoesNotThrow(() -> FeatureComparator.compare(
                actualFileWithIgnoredLine.toString(),
                actualFileWithIgnoredLine.toString(),
                ignoreLines
        ));
    }

    @Test
    void testCompareFilesDifferThrowsException() throws IOException {
        // Modify actual file to make it different
        try (BufferedWriter writer = Files.newBufferedWriter(actualFile)) {
            writer.write("ID   TEST123;\n");
            writer.write("FT   source          1..100\n");
            writer.write("FT                   /note=\"DIFFERENT\"\n");
            writer.write("SQ   SEQUENCE\n");
            writer.write("//");
        }

        FlatFileComparatorException ex = assertThrows(FlatFileComparatorException.class, () -> {
            FeatureComparator.compare(expectedFile.toString(), actualFile.toString(), List.of());
        });

        assertTrue(ex.getMessage().contains("File comparison failed"));
    }
}
