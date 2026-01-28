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
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.embl.api.entry.Entry;
import uk.ac.ebi.embl.api.validation.ValidationResult;
import uk.ac.ebi.embl.api.validation.submission.SubmissionOptions;
import uk.ac.ebi.embl.gff3tools.exception.*;
import uk.ac.ebi.embl.template.*;

/**
 * Reads TSV rows and converts each to an Entry object using sequencetools template processing.
 *
 * <p>This class wraps sequencetools' template processing infrastructure to provide
 * an iterator-like interface for reading EMBL Entry objects from template-based TSV files.
 *
 * <p>The reader accepts a BufferedReader, allowing the caller to handle gzip decompression
 * if needed (e.g., in FileConversionCommand).
 */
public class TSVEntryReader implements Closeable {
    private static final Logger LOG = LoggerFactory.getLogger(TSVEntryReader.class);

    private static final int MAX_LINES_FOR_TEMPLATE_ID = 10;

    private final CSVReader csvReader;
    private final TemplateProcessor templateProcessor;
    private final SubmissionOptions options;
    private final TemplateInfo templateInfo;
    private final BufferedReader replayReader;

    private int currentLineNumber = 0;

    /**
     * Creates a TSVEntryReader from a BufferedReader.
     *
     * <p>The reader will scan the first lines to extract the template ID, buffer those lines,
     * and then continue reading the rest of the TSV data.
     *
     * @param reader BufferedReader for the TSV input (caller handles gzip decompression)
     * @throws ReadException if the file cannot be read
     * @throws TemplateNotFoundException if template ID is not found in file or template doesn't exist
     */
    public TSVEntryReader(BufferedReader reader) throws ReadException, TemplateNotFoundException {
        this.options = createDefaultOptions();

        try {
            // 1. Extract template ID while buffering initial lines
            List<String> bufferedLines = new ArrayList<>();
            String templateId = extractTemplateId(reader, bufferedLines);
            LOG.info("Detected template ID: {}", templateId);

            // 2. Load template
            this.templateInfo = loadTemplate(templateId);
            this.templateProcessor = new TemplateProcessor(templateInfo, options);

            // 3. Create CSVReader with a BufferedReader that replays buffered lines first
            this.replayReader = createReplayReader(bufferedLines, reader);
            this.csvReader = new CSVReader(replayReader, templateInfo.getTokens(), 0);

        } catch (TemplateNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new ReadException(
                    "Failed to initialize TSV reader: " + e.getMessage(), ReadException.wrapAsIOException(e));
        }
    }

    /**
     * Reads the next TSV row and converts it to an Entry.
     *
     * @return Entry object or null if no more rows
     * @throws ReadException if there's an error reading the TSV (includes TSVParseException)
     */
    public Entry read() throws ReadException {
        try {
            CSVLine csvLine = csvReader.readTemplateSpreadsheetLine();
            if (csvLine == null) {
                return null;
            }

            currentLineNumber = csvLine.getLineNumber();

            TemplateProcessorResultSet result = templateProcessor.process(csvLine.getEntryTokenMap(), options);

            // Check for validation errors from template processing
            ValidationResult validationResult = result.getValidationResult();
            if (!validationResult.isValid()) {
                String errorMessages = formatValidationErrors(validationResult);
                throw new TSVParseException("Template validation failed: " + errorMessages, currentLineNumber);
            }

            Entry entry = result.getEntry();
            if (entry == null) {
                throw new TSVParseException("Template processing produced no entry", currentLineNumber);
            }

            return entry;

        } catch (TSVParseException e) {
            throw e;
        } catch (TemplateUserError e) {
            throw new TSVParseException(e.getMessage(), currentLineNumber, e);
        } catch (Exception e) {
            throw new ReadException("Error reading TSV row: " + e.getMessage(), ReadException.wrapAsIOException(e));
        }
    }

    /**
     * Returns the current line number being processed.
     */
    public int getCurrentLineNumber() {
        return currentLineNumber;
    }

    /**
     * Returns the loaded template info.
     */
    public TemplateInfo getTemplateInfo() {
        return templateInfo;
    }

    @Override
    public void close() throws IOException {
        // Close the replay reader which wraps the original BufferedReader
        if (replayReader != null) {
            replayReader.close();
        }
    }

    /**
     * Extracts template ID from the first lines of the TSV.
     * Buffers lines read so they can be replayed for the CSVReader.
     */
    private String extractTemplateId(BufferedReader reader, List<String> bufferedLines)
            throws IOException, TemplateNotFoundException {

        for (int i = 0; i < MAX_LINES_FOR_TEMPLATE_ID; i++) {
            String line = reader.readLine();
            if (line == null) {
                break;
            }
            bufferedLines.add(line);

            String templateId = CSVReader.getChecklistIdFromIdLine(line);
            if (templateId != null) {
                return templateId;
            }
        }

        throw new TemplateNotFoundException("No template ID found in TSV file header. "
                + "Expected a line like 'Checklist ERT000002' or '#template_accession ERT000002' "
                + "in the first " + MAX_LINES_FOR_TEMPLATE_ID + " lines.");
    }

    /**
     * Creates a BufferedReader that first replays buffered lines, then continues from the original reader.
     */
    private BufferedReader createReplayReader(List<String> bufferedLines, BufferedReader continuation) {
        // Build string from buffered lines
        StringBuilder sb = new StringBuilder();
        for (String line : bufferedLines) {
            sb.append(line).append("\n");
        }

        // Create a reader that combines buffered content with continuation
        Reader combinedReader = new SequenceReader(new StringReader(sb.toString()), continuation);
        return new BufferedReader(combinedReader);
    }

    /**
     * A Reader that reads from two Readers in sequence.
     */
    private static class SequenceReader extends Reader {
        private final Reader first;
        private final Reader second;
        private boolean firstExhausted = false;

        SequenceReader(Reader first, Reader second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public int read(char[] cbuf, int off, int len) throws IOException {
            if (!firstExhausted) {
                int result = first.read(cbuf, off, len);
                if (result != -1) {
                    return result;
                }
                firstExhausted = true;
            }
            return second.read(cbuf, off, len);
        }

        @Override
        public void close() throws IOException {
            first.close();
            second.close();
        }
    }

    /**
     * Loads template XML from sequencetools resources.
     */
    private TemplateInfo loadTemplate(String templateId) throws TemplateNotFoundException {
        try {
            // Use TemplateProcessor to get the template XML, then parse with TemplateLoader
            TemplateProcessor processor = new TemplateProcessor();
            String templateXml = processor.getTemplate(templateId);

            if (templateXml == null || templateXml.isEmpty()) {
                throw new TemplateNotFoundException("Template not found: " + templateId
                        + ". Make sure the template ID is correct and exists in sequencetools.");
            }

            // Parse XML to TemplateInfo
            TemplateLoader loader = new TemplateLoader();
            return loader.loadTemplateFromString(templateXml);

        } catch (TemplateNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new TemplateNotFoundException("Failed to load template " + templateId + ": " + e.getMessage(), e);
        }
    }

    /**
     * Formats validation errors from sequencetools ValidationResult into a readable string.
     */
    private String formatValidationErrors(ValidationResult result) {
        StringBuilder sb = new StringBuilder();
        result.getMessages().forEach(msg -> {
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(msg.getMessage());
        });
        return sb.toString();
    }

    /**
     * Creates default SubmissionOptions for template processing.
     */
    private SubmissionOptions createDefaultOptions() {
        SubmissionOptions options = new SubmissionOptions();
        // Use minimal configuration - template processing doesn't require most options
        options.isWebinCLI = false;
        options.isFixMode = true;
        return options;
    }
}
