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
package uk.ac.ebi.embl.converter.gff3.reader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.embl.converter.gff3.*;
import uk.ac.ebi.embl.converter.utils.Gff3Utils;
import uk.ac.ebi.embl.converter.validation.RuleSeverityState;
import uk.ac.ebi.embl.converter.validation.ValidationRule;

public class GFF3FileReader implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(GFF3FileReader.class);

    static Pattern DIRECTIVE_VERSION = Pattern.compile(
            "^##gff-version (?<version>(?<major>[0-9]+)(\\.(?<minor>[0-9]+)(:?\\.(?<patch>[0-9]+))?)?)\\s*$");
    static Pattern DIRECTIVE_SEQUENCE = Pattern.compile(
            "^##sequence-region\\s(?<accession>(?<accessionID>.+)([.](?<accessionVersion>[0-9]+)))\\s(?<start>[0-9]+)\\s(?<end>[0-9]+)");
    static Pattern DIRECTIVE_SPECIES = Pattern.compile("^##species\\s(?<species>.+)$");
    static Pattern COMMENT = Pattern.compile("^#.*$");
    static Pattern GFF3_FEATURE = Pattern.compile(
            "^(?<accession>.+)\\t(?<source>.+)\\t(?<name>.+)\\t(?<start>[0-9]+)\\t(?<end>[0-9]+)\\t(?<score>.+)\\t(?<strand>\\+|\\-)\\t(?<phase>.+)\\t(?<attributes>.+)?$");

    BufferedReader bufferedReader;
    int lineCount;
    GFF3Annotation gff3Annotation;

    public GFF3FileReader(Reader reader) {
        this.bufferedReader = new BufferedReader(reader);
        lineCount = 0;
        gff3Annotation = null;
    }

    public GFF3Annotation readAnnotation() throws IOException, InvalidGFF3RecordException {

        String line;
        while ((line = readLine()) != null) {
            if (line.isBlank()) {
                // Ignore blank lines
                continue;
            }
            Matcher m = DIRECTIVE_SEQUENCE.matcher(line);
            if (m.matches()) {
                // Create directive
                GFF3Directives.GFF3SequenceRegion sequenceDirective = getSequenceDirective(line);

                GFF3Annotation previousAnnotation = gff3Annotation;
                // New gff3 annotation for each sequence directive
                gff3Annotation = new GFF3Annotation();
                gff3Annotation.getDirectives().add(sequenceDirective);

                // return previous annotation
                if (previousAnnotation != null) {
                    return previousAnnotation;
                }
            } else if (COMMENT.matcher(line).matches()) {
                // Skip comment
                continue;
            } else if (GFF3_FEATURE.matcher(line).matches()) {
                parseAndAddFeature(line);
            } else {

                InvalidGFF3RecordException error =
                        new InvalidGFF3RecordException(lineCount, "Invalid gff3 record \"" + line + "\"");
                switch (RuleSeverityState.INSTANCE.getSeverity(ValidationRule.GFF3_INVALID_RECORD)) {
                    case OFF -> {}
                    case WARN -> {
                        LOG.warn(error.getMessage());
                    }
                    case ERROR -> {
                        throw error;
                    }
                }
            }
        }

        GFF3Annotation finalAnnotation = gff3Annotation;
        gff3Annotation = null;
        return finalAnnotation;
    }

    private GFF3Directives.GFF3SequenceRegion getSequenceDirective(String line) {
        // Extra check for line match
        Matcher m = DIRECTIVE_SEQUENCE.matcher(line);
        if (!m.matches()) return null;

        String accession = m.group("accession");
        long start = Long.parseLong(m.group("start"));
        long end = Long.parseLong(m.group("end"));

        // TODO: Validation no multiple sequence accession
        // TODO: Validation Sequence accession is required!
        return new GFF3Directives.GFF3SequenceRegion(accession, start, end);
    }

    private void parseAndAddFeature(String line) throws InvalidGFF3RecordException {
        // Extra check for line match
        Matcher m = GFF3_FEATURE.matcher(line);
        if (!m.matches()) {
            InvalidGFF3RecordException error = new InvalidGFF3RecordException(lineCount, line);
            switch (RuleSeverityState.INSTANCE.getSeverity(ValidationRule.GFF3_INVALID_RECORD)) {
                case OFF -> {
                    return;
                }
                case WARN -> {
                    LOG.warn(error.getMessage());
                    return;
                }
                case ERROR -> {
                    throw error;
                }
            }
        }

        String accession = m.group("accession");
        String source = m.group("source");
        String name = m.group("name");
        long start = Long.parseLong(m.group("start"));
        long end = Long.parseLong(m.group("end"));
        String score = m.group("score");
        String strand = m.group("strand");
        String phase = m.group("phase");
        String attributes = m.group("attributes");

        Map<String, Object> attributesMap = attributesFromString(attributes);

        Optional<String> id = Optional.ofNullable((String) attributesMap.get("ID"));
        Optional<String> parentId = Optional.ofNullable((String) attributesMap.get("Parent"));

        GFF3Feature feature =
                new GFF3Feature(id, parentId, accession, source, name, start, end, score, strand, phase, attributesMap);

        gff3Annotation.addFeature(feature);
    }

    public Map<String, Object> attributesFromString(String line) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        String[] parts = line.split(";");
        for (String part : parts) {
            if (part.contains("=")) {
                String[] keyValue = part.split("=");
                if (keyValue.length == 2) {
                    String key = keyValue[0].trim();
                    List<String> values = Arrays.stream(keyValue[1].trim().split(","))
                            .map(GFF3FileReader::urlDecode)
                            .toList();
                    Gff3Utils.addAttributes(attributes, key, values);
                }
            }
        }
        return attributes;
    }

    public GFF3Header readHeader() throws IOException, InvalidGFF3HeaderException {
        String line;
        while ((line = readLine()) != null) {
            if (line.isBlank()) {
                continue;
            }

            Matcher m = DIRECTIVE_VERSION.matcher(line);
            if (m.matches()) {
                String version = m.group("version");
                return new GFF3Header(version);
            } else if (!COMMENT.matcher(line).matches()) {
                // If the line is not a comment throw, otherwise ignore it
                InvalidGFF3HeaderException error = new InvalidGFF3HeaderException(lineCount, line);
                switch (RuleSeverityState.INSTANCE.getSeverity(ValidationRule.GFF3_INVALID_HEADER)) {
                    case ERROR -> {
                        throw error;
                    }
                    case WARN -> {
                        LOG.warn(error.getMessage());
                    }
                    case OFF -> {}
                }
            }
        }
        InvalidGFF3HeaderException error = new InvalidGFF3HeaderException(lineCount, "GFF3 header not found");
        switch (RuleSeverityState.INSTANCE.getSeverity(ValidationRule.GFF3_INVALID_HEADER)) {
            case OFF -> {}
            case WARN -> {
                LOG.warn(error.getMessage());
            }
            case ERROR -> {
                throw error;
            }
        }
        return null;
    }

    private String readLine() throws IOException {
        this.lineCount++;
        return bufferedReader.readLine();
    }

    private static String urlDecode(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }

    @Override
    public void close() throws IOException {
        bufferedReader.close();
    }
}
