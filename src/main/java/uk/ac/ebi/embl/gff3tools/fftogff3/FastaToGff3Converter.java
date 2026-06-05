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
package uk.ac.ebi.embl.gff3tools.fftogff3;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.zip.GZIPInputStream;
import lombok.extern.slf4j.Slf4j;
import uk.ac.ebi.embl.fastareader.sequenceutils.GapRegion;
import uk.ac.ebi.embl.gff3tools.Converter;
import uk.ac.ebi.embl.gff3tools.cli.SequenceFormat;
import uk.ac.ebi.embl.gff3tools.exception.ReadException;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.exception.WriteException;
import uk.ac.ebi.embl.gff3tools.gff3.*;
import uk.ac.ebi.embl.gff3tools.gff3.directives.GFF3Header;
import uk.ac.ebi.embl.gff3tools.gff3.directives.GFF3SequenceRegion;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngine;
import uk.ac.ebi.embl.gff3tools.validation.provider.FileSequenceSource;

/**
 * Converts a FASTA (or plain) sequence file to a GFF3 file containing only gap features.
 *
 * <p>Each sequence entry is scanned for contiguous runs of {@code N}/{@code n} bases.
 * Every run whose length is at least {@code minGapLength} becomes a {@code gap} feature with
 * {@code estimated_length}, {@code gap_type}, and {@code linkage_evidence} attributes. Runs
 * shorter than {@code minGapLength} are ignored, mirroring the INSDC assembly behaviour
 * implemented in sequencetools ({@code SequenceToGapFeatureBasesFix}).
 */
@Slf4j
public class FastaToGff3Converter implements Converter {

    private static final int GZIP_MAGIC_BYTE1 = 0x1f;
    private static final int GZIP_MAGIC_BYTE2 = 0x8b;
    private static final String GAP_TYPE_DEFAULT = "within scaffold";
    private static final String LINKAGE_EVIDENCE_DEFAULT = "unspecified";

    /** Default minimum gap length, matching sequencetools {@code Entry.DEFAULT_MIN_GAP_LENGTH}. */
    public static final int DEFAULT_MIN_GAP_LENGTH = 10;

    private final ValidationEngine validationEngine;
    private final Path inputFilePath;
    private final SequenceFormat sequenceFormat;
    private final int minGapLength;

    public FastaToGff3Converter(
            ValidationEngine validationEngine, Path inputFilePath, SequenceFormat sequenceFormat, int minGapLength) {
        this.validationEngine = validationEngine;
        this.inputFilePath = inputFilePath;
        this.sequenceFormat = sequenceFormat;
        // Defensive: a run of N is only ever a gap if it has at least one base.
        this.minGapLength = Math.max(1, minGapLength);
    }

    @Override
    public void convert(BufferedReader reader, BufferedWriter writer)
            throws ReadException, WriteException, ValidationException {

        // The BufferedReader is not used; fastareader requires a File.
        Path effectivePath = resolveGzippedPath(inputFilePath);

        FileSequenceSource source = new FileSequenceSource(effectivePath, sequenceFormat, null);

        // Trigger lazy initialisation so getFormatReader() is non-null
        source.getSeqIdToHeader();

        GFF3Header header = new GFF3Header(GFF3Header.DEFAULT_VERSION);
        List<GFF3Annotation> annotations = new ArrayList<>();

        for (long ordinal : source.getFormatReader().getOrderedIds()) {
            String seqId = findSeqIdForOrdinal(source, ordinal);
            if (seqId == null) {
                log.warn("No sequence ID found for ordinal {}", ordinal);
                continue;
            }

            long length;
            List<GapRegion> gaps;
            try {
                length = source.getFormatReader().getStats(ordinal).totalBases();
                gaps = source.getFormatReader().getGapRegions(ordinal);
            } catch (Exception e) {
                throw new ReadException(
                        "Failed to read sequence for ordinal " + ordinal + ": " + e.getMessage(),
                        ReadException.wrapAsIOException(e));
            }

            GFF3Annotation annotation = new GFF3Annotation();
            annotation.setSequenceRegion(new GFF3SequenceRegion(seqId, Optional.empty(), 1, length));

            int gapIndex = 0;
            for (GapRegion gap : gaps) {
                // Rule: only runs of N at least minGapLength long are reported as gaps,
                // matching the INSDC assembly behaviour in sequencetools.
                if (gap.lengthBases() < minGapLength) {
                    continue;
                }
                String id = gapIndex == 0 ? "gap" : "gap_" + gapIndex;
                GFF3Feature feature = new GFF3Feature(
                        Optional.of(id),
                        Optional.empty(),
                        seqId,
                        Optional.empty(),
                        ".",
                        "gap",
                        gap.startBase,
                        gap.endBase,
                        ".",
                        "+",
                        ".");
                feature.addAttribute(GFF3Attributes.ATTRIBUTE_ID, id);
                feature.addAttribute(GFF3Attributes.ESTIMATED_LENGTH, String.valueOf(gap.lengthBases()));
                feature.addAttribute(GFF3Attributes.GAP_TYPE, GAP_TYPE_DEFAULT);
                feature.addAttribute(GFF3Attributes.LINKAGE_EVIDENCE, LINKAGE_EVIDENCE_DEFAULT);
                annotation.addFeature(feature);
                gapIndex++;
            }

            annotations.add(annotation);
        }

        GFF3File file =
                GFF3File.builder().header(header).annotations(annotations).build();

        file.writeGFF3String(writer);
        source.close();

        // Clean up temporary decompressed file if one was created
        if (!effectivePath.equals(inputFilePath)) {
            try {
                Files.deleteIfExists(effectivePath);
            } catch (IOException e) {
                log.warn("Failed to delete temporary decompressed file: {}", effectivePath);
            }
        }
    }

    /**
     * Returns the original path if the file is not gzip-compressed,
     * otherwise decompresses to a temporary file and returns that path.
     */
    private Path resolveGzippedPath(Path path) throws ReadException {
        boolean gzipped;
        try (InputStream peekStream = Files.newInputStream(path)) {
            int byte1 = peekStream.read();
            int byte2 = peekStream.read();
            gzipped = (byte1 == GZIP_MAGIC_BYTE1 && byte2 == GZIP_MAGIC_BYTE2);
        } catch (IOException e) {
            throw new ReadException("Error checking file format: " + path, e);
        }

        if (!gzipped) {
            return path;
        }

        try {
            Path tempFile = Files.createTempFile("gff3tools-fasta-", ".fasta");
            try (InputStream gzipIn = new GZIPInputStream(Files.newInputStream(path))) {
                Files.copy(gzipIn, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }
            return tempFile;
        } catch (IOException e) {
            throw new ReadException("Failed to decompress gzipped FASTA: " + path, e);
        }
    }

    private String findSeqIdForOrdinal(FileSequenceSource source, long ordinal) {
        for (Map.Entry<String, Long> entry : source.getSeqIdToOrdinal().entrySet()) {
            if (entry.getValue() == ordinal) {
                return entry.getKey();
            }
        }
        return null;
    }
}
