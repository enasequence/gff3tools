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
import java.nio.file.Path;
import java.util.Map;
import java.util.OptionalLong;
import java.util.TreeMap;
import lombok.extern.slf4j.Slf4j;
import uk.ac.ebi.embl.fastareader.FastaReader;
import uk.ac.ebi.embl.fastareader.exception.FastaFileException;
import uk.ac.ebi.embl.fastareader.sequenceutils.SequenceAlphabet;
import uk.ac.ebi.embl.fastareader.sequenceutils.SequenceIndex;
import uk.ac.ebi.embl.gff3tools.exception.ReadException;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngine;

/**
 * Reads protein translation sequences stored in the FASTA section at the end of
 * a GFF3 file.
 *
 * <p>The FASTA boundary is located with {@link FastaSectionLocator} (an
 * O(log fileSize) binary search over raw byte offsets). A single
 * {@link FastaReader} is then opened at that offset, indexes the embedded FASTA
 * section in one forward pass, and serves each translation via buffered
 * positional slice reads. The reader is cached so extraction can reuse it, and
 * must therefore be closed once all translations have been read.
 */
@Slf4j
public class GFF3TranslationReader implements AutoCloseable {

    ValidationEngine validationEngine;
    Path gff3Path;
    private FastaReader fastaReader;

    public GFF3TranslationReader(ValidationEngine validationEngine, Path gff3Path) {
        this.validationEngine = validationEngine;
        this.gff3Path = gff3Path;
    }

    /**
     * Locates the FASTA section and builds a map from translation id (the FASTA
     * header text with the single leading {@code >} stripped) to the
     * {@link FastaReader} sequential entry id. Returns an empty map when the file
     * has no FASTA section, opening no {@link FastaReader} in that case.
     *
     * <p>A {@link TreeMap} preserves the alphabetical external ordering of ids.
     */
    public Map<String, Long> readTranslationOffset() throws ReadException {
        Map<String, Long> offsetMap = new TreeMap<>();

        OptionalLong boundary = FastaSectionLocator.locate(gff3Path);
        if (boundary.isEmpty()) {
            return offsetMap;
        }

        try {
            fastaReader = new FastaReader(
                    gff3Path.toFile(), SequenceAlphabet.defaultProteinAlphabet(), boundary.getAsLong());
        } catch (FastaFileException | IOException e) {
            throw new ReadException(
                    "Error reading translations from " + gff3Path + ": " + e.getMessage(),
                    ReadException.wrapAsIOException(e));
        }

        for (long id : fastaReader.getOrderedIds()) {
            String header = fastaReader.getHeaderline(id).orElse("");
            String translationId = header.startsWith(">") ? header.substring(1) : header;
            offsetMap.put(translationId, id);
        }

        return offsetMap;
    }

    /**
     * Reads the translation sequence for the given {@link FastaReader} entry id,
     * with newlines stripped and the result uppercased. A zero-base entry yields
     * an empty string.
     */
    public String readTranslation(Long id) throws ReadException {
        SequenceIndex idx = fastaReader.getSequenceIndex(id);
        long n = idx.totalBases();
        if (n == 0) {
            return "";
        }
        try {
            return fastaReader.getSequenceSlice(id, 1, n).toUpperCase();
        } catch (Exception e) {
            throw new ReadException("Error reading translation from " + gff3Path + ": " + e.getMessage());
        }
    }

    @Override
    public void close() throws IOException {
        if (fastaReader != null) {
            fastaReader.close();
        }
    }
}
