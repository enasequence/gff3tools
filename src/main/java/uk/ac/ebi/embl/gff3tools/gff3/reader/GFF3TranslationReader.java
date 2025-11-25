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
package uk.ac.ebi.embl.gff3tools.gff3.reader;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import uk.ac.ebi.embl.gff3tools.exception.InvalidGFF3RecordException;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngine;

/**
 * Utility class for efficiently reading protein translation sequences stored in
 * the FASTA section at the end of a GFF3 file.
 *
 * This reader works directly on the underlying file using RandomAccessFile
 * to avoid loading the entire GFF3 or FASTA content into memory. It scans
 * backwards from the end of the file to locate ##FASTA directive
 */
public class GFF3TranslationReader {

    static Pattern SEQUENCE_PATTERN = Pattern.compile("^[ACDEFGHIKLMNPQRSTVWXY*]+$");
    ValidationEngine validationEngine;
    Path gff3Path;

    public GFF3TranslationReader(ValidationEngine validationEngine, Path gff3Path) {
        this.validationEngine = validationEngine;
        this.gff3Path = gff3Path;
    }

    /**
     * Scans the GFF3 file backwards to locate the FASTA section and extract
     * byte offsets for each translation sequence.
     *
     * The method reads from the end of the file until the ##FASTA
     * directive is encountered. For each FASTA header line (>accession|id),
     * the method records the byte offset where the sequence starts and ends.
     */
    public Map<String, OffsetRange> readTranslationOffset() {
        Map<String, OffsetRange> offsetMap = new TreeMap<>();
        try (RandomAccessFile raf = new RandomAccessFile(gff3Path.toFile(), "r")) {

            // End of the file
            long pointer = raf.length() - 1;
            StringBuilder currentLine = new StringBuilder();

            long seqStart = 0;
            long seqEnd = pointer;

            while (pointer >= 0) {
                seqStart = pointer;
                raf.seek(pointer);
                int b = raf.readByte();

                if (b == '\n' || b == '\r') {
                    if (!currentLine.isEmpty()) {
                        String line = currentLine.reverse().toString();
                        currentLine.setLength(0);

                        // Stop once we reach the marker
                        if (line.startsWith("##FASTA")) break;

                        if (line.startsWith(">")) {
                            // Encountered ID line, store ID and offset
                            seqStart = pointer + line.length() + 1;
                            line = line.replace(">", "");
                            offsetMap.put(line, new OffsetRange(seqStart, seqEnd));
                            seqEnd = pointer;
                        }
                    }
                } else {
                    currentLine.append((char) b);
                }
                pointer--;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return offsetMap;
    }

    /**
     * Reads a sequence from the GFF3 FASTA section based on the provided byte offset range
     * All newline characters are removed to produce a continuous sequence string.
     * The resulting string is validated using the SEQUENCE_PATTERN
     */
    public String readTranslation(OffsetRange offset) {

        StringBuilder sequence = new StringBuilder();
        long start = offset.start;
        long end = offset.end;
        try (RandomAccessFile raf = new RandomAccessFile(gff3Path.toFile(), "r")) {

            while (start <= end) {
                raf.seek(start);
                int b = raf.readByte();
                if (b != '\n') {
                    sequence.append((char) b);
                }
                start++;
            }
            Matcher matcher = SEQUENCE_PATTERN.matcher(sequence.toString());
            if (!matcher.matches()) {
                validationEngine.handleSyntacticError(
                        new InvalidGFF3RecordException(-1, "Invalid sequence record \"" + sequence.toString() + "\""));
            }
        } catch (IOException | ValidationException e) {
            throw new RuntimeException(e);
        }

        return sequence.toString();
    }
}
