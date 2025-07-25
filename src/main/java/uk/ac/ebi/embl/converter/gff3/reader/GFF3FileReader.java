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
import uk.ac.ebi.embl.converter.exception.*;
import uk.ac.ebi.embl.converter.gff3.*;
import uk.ac.ebi.embl.converter.utils.Gff3Utils;
import uk.ac.ebi.embl.converter.validation.RuleSeverityState;

public class GFF3FileReader implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(GFF3FileReader.class);

    static Pattern VERSION_DIRECTIVE = Pattern.compile(
            "^##gff-version (?<version>(?<major>[0-9]+)(\\.(?<minor>[0-9]+)(:?\\.(?<patch>[0-9]+))?)?)\\s*$");
    static Pattern SEQUENCE_REGION_DIRECTIVE = Pattern.compile(
            "^##sequence-region\\s+(?<accession>(?<accessionId>[^.]+)(?:\\.(?<accessionVersion>\\d+))?)\\s+(?<start>[0-9]+)\\s+(?<end>[0-9]+)$");
    static Pattern RESOLUTION_DIRECTIVE = Pattern.compile("^###$");
    static Pattern COMMENT = Pattern.compile("^#.*$");
    static Pattern GFF3_FEATURE = Pattern.compile(
            "^(?<accession>(?<accessionId>[^.]+)(?:\\.(?<accessionVersion>\\d+))?)\\t(?<source>.+)\\t(?<name>.+)\\t(?<start>[0-9]+)\\t(?<end>[0-9]+)\\t(?<score>.+)\\t(?<strand>\\+|\\-|\\.|\\?)\\t(?<phase>.+)\\t(?<attributes>.+)?$");

    BufferedReader bufferedReader;
    int lineCount;
    GFF3Annotation currentAnnotation;
    String currentAccession;
    Map<String, GFF3SequenceRegion> accessionSequenceRegionMap = new HashMap<>();

    public GFF3FileReader(Reader reader) {
        this.bufferedReader = new BufferedReader(reader);
        lineCount = 0;
        currentAnnotation = new GFF3Annotation();
    }

    public GFF3Annotation readAnnotation() throws IOException, ValidationException {

        String line;
        while ((line = readLine()) != null) {
            if (line.isBlank()) {
                // Ignore blank lines
                continue;
            }
            Matcher m = SEQUENCE_REGION_DIRECTIVE.matcher(line);
            if (m.matches()) {
                // Create directive
                GFF3SequenceRegion sequenceDirective = getSequenceDirective(m);
                accessionSequenceRegionMap.put(sequenceDirective.accession(), sequenceDirective);
            } else if (RESOLUTION_DIRECTIVE.matcher(line).matches()) {
                if (!currentAnnotation.getFeatures().isEmpty()) {
                    GFF3Annotation previousAnnotation = currentAnnotation;
                    currentAnnotation = new GFF3Annotation();
                    return previousAnnotation;
                }
                continue;
            } else if (COMMENT.matcher(line).matches()) {
                // Skip comment
                continue;
            } else if (GFF3_FEATURE.matcher(line).matches()) {
                GFF3Annotation annotation = parseAndAddFeature(line);
                if (annotation != null) {
                    return annotation;
                }
            } else {
                RuleSeverityState.handleValidationException(
                        new InvalidGFF3RecordException(lineCount, "Invalid gff3 record \"" + line + "\""));
            }
        }

        if (!currentAnnotation.getFeatures().isEmpty()) {
            GFF3Annotation finalAnnotation = currentAnnotation;
            currentAnnotation = new GFF3Annotation();
            return finalAnnotation;
        }
        return null;
    }

    private GFF3SequenceRegion getSequenceDirective(Matcher m) {

        String accessionId = m.group("accessionId");
        Optional<Integer> accessionVersion =
                Optional.ofNullable(m.group("accessionVersion")).map(Integer::parseInt);
        long start = Long.parseLong(m.group("start"));
        long end = Long.parseLong(m.group("end"));

        return new GFF3SequenceRegion(accessionId, accessionVersion, start, end);
    }

    private GFF3Annotation parseAndAddFeature(String line) throws ValidationException {
        // Extra check for line match
        Matcher m = GFF3_FEATURE.matcher(line);
        if (!m.matches()) {
            RuleSeverityState.handleValidationException(new InvalidGFF3RecordException(lineCount, line));
            return null;
        }

        String accession = m.group("accession");
        String accessionId = m.group("accessionId");
        Optional<Integer> accessionVersion =
                Optional.ofNullable(m.group("accessionVersion")).map(Integer::parseInt);
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

        GFF3Feature feature = new GFF3Feature(
                id,
                parentId,
                accessionId,
                accessionVersion,
                source,
                name,
                start,
                end,
                score,
                strand,
                phase,
                attributesMap);

        // TODO: Validate that the new annotation was not used before the current
        // annotation. Meaning that features are out of order
        if (!accession.equals(currentAccession)) {
            // In case of different accession create a new GFF3Annotation and return the
            // previous one.
            currentAccession = accession;
            GFF3Annotation previousAnnotation = currentAnnotation;
            currentAnnotation = new GFF3Annotation();
            currentAnnotation.addFeature(feature);

            // Add the corresponding sequence region to the current annotation
            if (accessionSequenceRegionMap.containsKey(currentAccession)) {
                GFF3SequenceRegion sequenceRegion = accessionSequenceRegionMap.get(currentAccession);
                currentAnnotation.setSequenceRegion(sequenceRegion);
            } else {
                RuleSeverityState.handleValidationException(new UndefinedSeqIdException(lineCount, line));
            }

            if (!previousAnnotation.getFeatures().isEmpty()) {
                return previousAnnotation;
            }
        } else {
            currentAnnotation.addFeature(feature);
        }

        return null;
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

    public GFF3Header readHeader() throws IOException, ValidationException {
        String line;
        while ((line = readLine()) != null) {
            if (line.isBlank()) {
                continue;
            }

            Matcher m = VERSION_DIRECTIVE.matcher(line);
            if (m.matches()) {
                String version = m.group("version");
                return new GFF3Header(version);
            } else if (!COMMENT.matcher(line).matches()) {
                RuleSeverityState.handleValidationException(new InvalidGFF3HeaderException(lineCount, line));
            }
        }
        RuleSeverityState.handleValidationException(new InvalidGFF3HeaderException(lineCount, "GFF3 header not found"));
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
