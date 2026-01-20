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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.embl.api.entry.Entry;
import uk.ac.ebi.embl.api.validation.ValidationResult;
import uk.ac.ebi.embl.api.validation.submission.SubmissionOptions;
import uk.ac.ebi.embl.gff3tools.exception.*;
import uk.ac.ebi.embl.template.*;

/**
 * Reads TSV rows and converts each to an Entry object using sequencetools template processing.
 * Supports both gzipped and plain text TSV files.
 *
 * <p>This class wraps sequencetools' template processing infrastructure to provide
 * an iterator-like interface for reading EMBL Entry objects from template-based TSV files.
 */
public class TSVEntryReader implements Closeable {
    private static final Logger LOG = LoggerFactory.getLogger(TSVEntryReader.class);

    private static final int GZIP_MAGIC_BYTE1 = 0x1f;
    private static final int GZIP_MAGIC_BYTE2 = 0x8b;
    private static final int MAX_LINES_FOR_TEMPLATE_ID = 10;

    private final CSVReader csvReader;
    private final TemplateProcessor templateProcessor;
    private final SubmissionOptions options;
    private final TemplateInfo templateInfo;
    private final InputStream inputStream;

    private int currentLineNumber = 0;

    /**
     * Creates a TSVEntryReader from a file path.
     * Automatically detects gzipped vs plain text input.
     *
     * @param inputPath Path to the TSV file (can be gzipped or plain text)
     * @throws ReadException if the file cannot be read
     * @throws TemplateNotFoundException if template ID is not found in file or template doesn't exist
     */
    public TSVEntryReader(Path inputPath) throws ReadException, TemplateNotFoundException {
        this.options = createDefaultOptions();

        try {
            // 1. Extract template ID (need to read file once for ID)
            String templateId = extractTemplateId(inputPath);
            LOG.info("Detected template ID: {}", templateId);

            // 2. Load template
            this.templateInfo = loadTemplate(templateId);
            this.templateProcessor = new TemplateProcessor(templateInfo, options);

            // 3. Create input stream (handles gzip detection)
            this.inputStream = createInputStream(inputPath);
            this.csvReader = new CSVReader(inputStream, templateInfo.getTokens(), 0);

        } catch (TemplateNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new ReadException("Failed to initialize TSV reader: " + e.getMessage(), wrapAsIOException(e));
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
            throw new ReadException("Error reading TSV row: " + e.getMessage(), wrapAsIOException(e));
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
        if (inputStream != null) {
            inputStream.close();
        }
    }

    /**
     * Detects if file is gzipped and creates appropriate InputStream.
     */
    private InputStream createInputStream(Path path) throws IOException {
        InputStream fis = Files.newInputStream(path);
        BufferedInputStream bis = new BufferedInputStream(fis);

        if (isGzipped(bis)) {
            LOG.debug("Detected gzipped TSV file");
            return new GZIPInputStream(bis);
        }

        LOG.debug("Detected plain text TSV file");
        return bis;
    }

    /**
     * Checks if the stream starts with gzip magic bytes.
     */
    private boolean isGzipped(BufferedInputStream bis) throws IOException {
        bis.mark(2);
        int byte1 = bis.read();
        int byte2 = bis.read();
        bis.reset();

        return (byte1 == GZIP_MAGIC_BYTE1 && byte2 == GZIP_MAGIC_BYTE2);
    }

    /**
     * Extracts template ID from TSV file header.
     * Scans the first 10 lines looking for a checklist ID pattern.
     */
    private String extractTemplateId(Path path) throws IOException, TemplateNotFoundException {
        try (InputStream is = createInputStream(path);
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {

            for (int i = 0; i < MAX_LINES_FOR_TEMPLATE_ID; i++) {
                String line = reader.readLine();
                if (line == null) {
                    break;
                }

                String templateId = CSVReader.getChecklistIdFromIdLine(line);
                if (templateId != null) {
                    return templateId;
                }
            }
        }

        throw new TemplateNotFoundException("No template ID found in TSV file header. "
                + "Expected a line like 'Checklist ERT000002' or '#template_accession ERT000002' " + "in the first "
                + MAX_LINES_FOR_TEMPLATE_ID + " lines.");
    }

    /**
     * Loads template XML from sequencetools resources.
     */
    private TemplateInfo loadTemplate(String templateId) throws TemplateNotFoundException {
        try {
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

    /**
     * Wraps an exception as IOException if it isn't already one.
     */
    private IOException wrapAsIOException(Exception e) {
        if (e instanceof IOException) {
            return (IOException) e;
        }
        return new IOException(e);
    }
}
