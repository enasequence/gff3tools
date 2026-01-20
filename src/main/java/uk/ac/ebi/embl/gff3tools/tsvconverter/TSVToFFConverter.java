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
package uk.ac.ebi.embl.gff3tools.tsvconverter;

import java.io.*;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.embl.api.entry.Entry;
import uk.ac.ebi.embl.flatfile.writer.embl.EmblEntryWriter;
import uk.ac.ebi.embl.gff3tools.Converter;
import uk.ac.ebi.embl.gff3tools.exception.*;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngine;

/**
 * Converts TSV files to EMBL flat file format (features/annotations only, no sequence).
 *
 * <p>This converter uses sequencetools' template processing to read TSV files and convert
 * them to Entry objects, then uses sequencetools' EmblEntryWriter to produce EMBL output.
 * The nucleotide sequence data is excluded from the output.
 *
 * <p>The TSV file must contain a template ID line (e.g., "Checklist ERT000002") in the
 * first 10 lines, followed by a header row and data rows.
 */
public class TSVToFFConverter implements Converter {
    private static final Logger LOG = LoggerFactory.getLogger(TSVToFFConverter.class);

    private final ValidationEngine validationEngine;
    private final Path inputFilePath;

    /**
     * Creates a new TSV to EMBL converter.
     *
     * @param validationEngine the validation engine to use
     * @param inputFilePath path to the input TSV file (needed to detect template ID)
     */
    public TSVToFFConverter(ValidationEngine validationEngine, Path inputFilePath) {
        this.validationEngine = validationEngine;
        this.inputFilePath = inputFilePath;
    }

    @Override
    public void convert(BufferedReader reader, BufferedWriter writer)
            throws ReadException, WriteException, ValidationException {

        // Note: We use inputFilePath rather than the reader because:
        // 1. We need to read the file twice (once for template ID, once for data)
        // 2. TSVEntryReader handles gzip detection internally
        try (TSVEntryReader entryReader = new TSVEntryReader(inputFilePath)) {
            LOG.info(
                    "Converting TSV file using template: {}",
                    entryReader.getTemplateInfo().getName());

            Entry entry;
            int entryCount = 0;
            while ((entry = entryReader.read()) != null) {
                entryCount++;

                // Clear sequence data - we only want features/annotations in output
                if (entry.getSequence() != null) {
                    entry.getSequence().setSequence(null);
                }

                // Use sequencetools' EmblEntryWriter to write the entry
                new EmblEntryWriter(entry).write(writer);
            }

            LOG.info("Converted {} entries from TSV to EMBL", entryCount);

        } catch (ReadException e) {
            throw e;
        } catch (IOException e) {
            throw new WriteException(e);
        }
    }
}
