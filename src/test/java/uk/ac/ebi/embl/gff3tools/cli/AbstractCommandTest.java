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

class AbstractCommandTest {

    private final AbstractCommand command = new AbstractCommand() {
        @Override
        public void run() {}
    };

    @Test
    void parseSequenceSpec_keyAndPath() {
        AbstractCommand.ParsedSequenceSpec parsed = command.parseSequenceSpec("chr1:/data/seq.fasta");
        assertEquals("chr1", parsed.key());
        assertEquals(Path.of("/data/seq.fasta"), parsed.path());
    }

    @Test
    void parseSequenceSpec_pathOnly_unix() {
        AbstractCommand.ParsedSequenceSpec parsed = command.parseSequenceSpec("/home/user/file.fasta");
        assertNull(parsed.key());
        assertEquals(Path.of("/home/user/file.fasta"), parsed.path());
    }

    @Test
    void parseSequenceSpec_pathOnly_relative() {
        AbstractCommand.ParsedSequenceSpec parsed = command.parseSequenceSpec("data/file.fasta");
        assertNull(parsed.key());
        assertEquals(Path.of("data/file.fasta"), parsed.path());
    }

    @Test
    void parseSequenceSpec_windowsAbsolutePath_parsedAsKeyed() {
        // Known limitation: Windows absolute paths like C:\path\file.fasta are parsed as
        // key="C", path="\path\file.fasta" because the single-letter prefix before ':'
        // passes the key heuristic. Users on Windows should use forward slashes or
        // rely on extension-based format inference without key:path syntax.
        AbstractCommand.ParsedSequenceSpec parsed = command.parseSequenceSpec("C:\\path\\file.fasta");
        assertEquals("C", parsed.key());
        assertEquals(Path.of("\\path\\file.fasta"), parsed.path());
    }

    @Test
    void parseSequenceSpec_noColon() {
        AbstractCommand.ParsedSequenceSpec parsed = command.parseSequenceSpec("file.fasta");
        assertNull(parsed.key());
        assertEquals(Path.of("file.fasta"), parsed.path());
    }
}
