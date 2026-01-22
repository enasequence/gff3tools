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

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

/**
 * Tests for FileConversionCommand file extension handling.
 */
class FileConversionCommandTest {

    @Test
    void testGetFileExtension_simple() {
        assertEquals("tsv", FileConversionCommand.getFileExtension(Path.of("input.tsv")));
        assertEquals("gff3", FileConversionCommand.getFileExtension(Path.of("output.gff3")));
        assertEquals("embl", FileConversionCommand.getFileExtension(Path.of("data.embl")));
    }

    @Test
    void testGetFileExtension_gzipped() {
        // .tsv.gz should be recognized as tsv
        assertEquals("tsv", FileConversionCommand.getFileExtension(Path.of("input.tsv.gz")));
    }

    @Test
    void testGetFileExtension_noExtension() {
        assertNull(FileConversionCommand.getFileExtension(Path.of("noextension")));
    }

    @Test
    void testGetFileExtension_withPath() {
        assertEquals("tsv", FileConversionCommand.getFileExtension(Path.of("/some/path/to/input.tsv")));
        assertEquals("tsv", FileConversionCommand.getFileExtension(Path.of("/some/path/to/input.tsv.gz")));
    }

    @Test
    void testConversionFileFormat_tsvIncluded() {
        // Verify tsv is a valid format
        ConversionFileFormat format = ConversionFileFormat.valueOf("tsv");
        assertEquals(ConversionFileFormat.tsv, format);
    }

    @Test
    void testOutputSequenceOption_longForm() {
        FileConversionCommand cmd = new FileConversionCommand();
        new CommandLine(cmd).parseArgs("--output-sequence", "output.fasta", "input.embl", "output.gff3");
        assertEquals(Path.of("output.fasta"), cmd.fastaOutputPath);
    }

    @Test
    void testOutputSequenceOption_shortForm() {
        FileConversionCommand cmd = new FileConversionCommand();
        new CommandLine(cmd).parseArgs("-os", "sequences.fa", "input.embl", "output.gff3");
        assertEquals(Path.of("sequences.fa"), cmd.fastaOutputPath);
    }

    @Test
    void testOutputSequenceOption_notProvided() {
        FileConversionCommand cmd = new FileConversionCommand();
        new CommandLine(cmd).parseArgs("input.embl", "output.gff3");
        assertNull(cmd.fastaOutputPath);
    }

    @Test
    void testOutputSequenceOption_withOtherOptions() {
        FileConversionCommand cmd = new FileConversionCommand();
        new CommandLine(cmd).parseArgs("-f", "embl", "-t", "gff3", "-os", "seqs.fasta", "input.embl", "output.gff3");
        assertEquals(Path.of("seqs.fasta"), cmd.fastaOutputPath);
        assertEquals(ConversionFileFormat.embl, cmd.fromFileType);
        assertEquals(ConversionFileFormat.gff3, cmd.toFileType);
    }
}
