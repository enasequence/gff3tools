package uk.ac.ebi.embl.gff3tools.gff3.reader;

import uk.ac.ebi.embl.gff3tools.exception.InvalidGFF3RecordException;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3toff.OffsetRange;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngine;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GFF3TranslationReader {

    static Pattern SEQUENCE_PATTERN = Pattern.compile("^[ACDEFGHIKLMNPQRSTVWY*]+$");
    ValidationEngine validationEngine;
    Path gff3Path;

    public GFF3TranslationReader(ValidationEngine validationEngine, Path gff3Path) {
        this.validationEngine = validationEngine;
        this.gff3Path = gff3Path;
    }

    /**
     * Reads the FASTA from the end of GFF3 file
     * @param gff3Path
     * @return
     */
    public Map<String, String> readTranslation(Path gff3Path) {
        Map<String, String> fastaMap = new LinkedHashMap<>();
        try (RandomAccessFile raf = new RandomAccessFile(gff3Path.toFile(), "r")) {

            // End of the file
            long pointer = raf.length() - 1;
            StringBuilder currentLine = new StringBuilder();
            StringBuilder sequence = new StringBuilder();

            while (pointer >= 0) {
                raf.seek(pointer);
                int b = raf.readByte();

                if (b == '\n' || b == '\r') {
                    if (!currentLine.isEmpty()) {
                        String line = currentLine.reverse().toString();
                        currentLine.setLength(0);

                        // Stop once we reach the marker
                        if (line.startsWith("##FASTA")) break;

                        if (line.startsWith(">")) {
                            // Encountered ID line, store ID and sequence
                            fastaMap.put(line, sequence.toString());
                            sequence.setLength(0);

                        } else if (!line.isBlank()) {
                            Matcher matcher = SEQUENCE_PATTERN.matcher(line);
                            if(!matcher.matches()){
                                validationEngine.handleSyntacticError(
                                        new InvalidGFF3RecordException(-1, "Invalid gff3 record \"" + line + "\""));
                            }
                            // Accumulate sequence
                            sequence.insert(0, line.trim());
                        }
                    }
                } else {
                    currentLine.append((char) b);
                }
                pointer--;
            }
        }catch (IOException | ValidationException e){
            throw new RuntimeException(e);
        }

        return fastaMap;
    }



    /**
     * Reads the FASTA from the end of GFF3 file
     * @return
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
                            line = line.replace(">","");
                            offsetMap.put(line, new OffsetRange(seqStart, seqEnd));
                            //System.out.println(readTranslation(seqStart, seqEnd));
                            seqEnd = pointer;
                        }
                    }
                } else {
                    currentLine.append((char) b);
                }
                pointer--;
            }
        }catch (IOException e){
            throw new RuntimeException(e);
        }

        return offsetMap;
    }

    /**
     * Reads the FASTA from the end of GFF3 file
     * @return
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
                    sequence.append((char)b);
                }
                start++;
            }
            Matcher matcher = SEQUENCE_PATTERN.matcher(sequence.toString());
            if(!matcher.matches()){
                validationEngine.handleSyntacticError(
                        new InvalidGFF3RecordException(-1, "Invalid sequence record \"" + sequence.toString() + "\""));
            }
        }catch (IOException | ValidationException e){
            throw new RuntimeException(e);
        }

        return sequence.toString();
    }
}
