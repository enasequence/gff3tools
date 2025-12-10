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
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.TreeMap;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class GFF3TranslationReader {

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

        try (FileChannel channel = FileChannel.open(gff3Path, StandardOpenOption.READ)) {

            long fileSize = channel.size();
            long position = fileSize;
            long seqEnd = fileSize - 1;
            long cursorPos;

            // 1 MB Buffer size
            final int bufferSize = 1024 * 1024;

            ByteBuffer buffer = ByteBuffer.allocate(bufferSize);

            StringBuilder lineBuffer = new StringBuilder();
            boolean stop = false;

            while (position > 0 && !stop) {
                // Move read window backwards
                long bytesToRead = Math.min(position, bufferSize);
                position -= bytesToRead;

                buffer.clear();
                channel.position(position);
                channel.read(buffer);
                buffer.flip();

                // Scan block backwards
                for (int i = (int) bytesToRead - 1; i >= 0; i--) {

                    byte b = buffer.get(i);
                    // absolute byte position in file
                    cursorPos = position + i;

                    if (b == '\n' || b == '\r') {

                        // Empty lines are ignored
                        if (lineBuffer.length() > 0) {
                            String line = lineBuffer.reverse().toString();
                            lineBuffer.setLength(0);

                            // Hit FASTA section
                            if (line.startsWith("##FASTA")) {
                                stop = true;
                                break;
                            }

                            // Exit when line is not a sequence
                            if (!isValidSequence(line) && !line.startsWith(">")) {
                                if (offsetMap.isEmpty()) {
                                    log.info("Translation sequence not found");
                                    stop = true;
                                    break;
                                } else {
                                    // Error when unwanted lines appear in the middle of a translation block
                                    throw new RuntimeException("Invalid GFF3 translation sequence: " + line);
                                }
                            }

                            // Header line → store offset range
                            if (line.startsWith(">")) {
                                // remove '>'
                                String id = line.substring(1);
                                long seqStart = cursorPos + line.length() + 1;
                                offsetMap.put(id, new OffsetRange(seqStart, seqEnd));
                                seqEnd = cursorPos; // next sequence ends here
                            }
                        }

                    } else {
                        // Buffer the character
                        lineBuffer.append((char) b);
                    }
                }
            }

            // Handle last line if needed
            if (!lineBuffer.isEmpty() && !stop) {
                String line = lineBuffer.reverse().toString();
                if (line.startsWith(">")) {
                    String id = line.substring(1);
                    offsetMap.put(id, new OffsetRange(position, seqEnd));
                }
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

        StringBuilder sequenceBuilder = new StringBuilder();
        String sequence;
        long start = offset.start;
        long end = offset.end;
        try (RandomAccessFile raf = new RandomAccessFile(gff3Path.toFile(), "r")) {

            while (start <= end) {
                raf.seek(start);
                int b = raf.readByte();
                if (b != '\n') {
                    sequenceBuilder.append((char) b);
                }
                start++;
            }
            sequence = sequenceBuilder.toString().toUpperCase();
            if (!isValidSequence(sequence)) {
                validationEngine.handleSyntacticError(
                        new InvalidGFF3RecordException(-1, "Invalid sequenceBuilder record \"" + sequence + "\""));
            }
        } catch (IOException | ValidationException e) {
            throw new RuntimeException(e);
        }

        return sequence;
    }

    public static boolean isValidSequence(String seq) {
        seq = seq.toUpperCase();
        for (int i = 0; i < seq.length(); i++) {
            char c = seq.charAt(i);
            // Accept uppercase letters A–Z and *
            if (!((c >= 'A' && c <= 'Z') || c == '*')) {
                return false;
            }
        }
        return true;
    }
}
