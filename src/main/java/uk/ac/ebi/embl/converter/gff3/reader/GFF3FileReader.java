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
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import uk.ac.ebi.embl.converter.gff3.*;

public class GFF3FileReader {
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

    public GFF3Annotation readAnnotation() throws IOException, GFF3ValidationError {

        String line;
        while ((line = readLine()) != null) {
            if (line.isBlank()) {
                // Ignore blank lines
                continue;
            }
            Matcher m = DIRECTIVE_SEQUENCE.matcher(line);
            if (m.matches()) {
                GFF3Annotation completedAnnotation = gff3Annotation;
                // Create new gff3 annotation for each sequence directive
                gff3Annotation = new GFF3Annotation();

                String accession = m.group("accession");
                long start = Long.parseLong(m.group("start"));
                long end = Long.parseLong(m.group("end"));
                // TODO: Validation no multiple sequence accession
                // TODO: Validation Sequence accession is required!
                GFF3Directives.GFF3SequenceRegion sequenceAccession =
                        new GFF3Directives.GFF3SequenceRegion(accession, start, end);

                gff3Annotation.getDirectives().add(sequenceAccession);
                if (completedAnnotation != null) {
                    return completedAnnotation;
                }
                continue;
            }
            m = COMMENT.matcher(line);
            if (m.matches()) {
                // Skip comment
                continue;
            }
            m = GFF3_FEATURE.matcher(line);
            if (m.matches()) {
                String accession = m.group("accession");
                String source = m.group("source");
                String name = m.group("name");
                long start = Long.parseLong(m.group("start"));
                long end = Long.parseLong(m.group("end"));
                String score = m.group("score");
                String strand = m.group("strand");
                String phase = m.group("phase");
                String attributes = m.group("attributes");
                Map<String, String> attributesMap = attributesFromString(attributes);
                Optional<String> id =
                        attributesMap.containsKey("ID") ? Optional.of(attributesMap.get("ID")) : Optional.empty();
                Optional<String> parentId = attributesMap.containsKey("Parent")
                        ? Optional.of(attributesMap.get("Parent"))
                        : Optional.empty();

                GFF3Feature feature = new GFF3Feature(
                        id, parentId, accession, source, name, start, end, score, strand, phase, attributesMap);

                gff3Annotation.addFeature(feature);
            } else {
                throw new GFF3ValidationError(lineCount, "Invalid gff3 record \"" + line + "\"");
            }
        }

        GFF3Annotation completedAnnotation = gff3Annotation;
        gff3Annotation = null;
        return completedAnnotation;
    }

    private static Map<String, String> attributesFromString(String line) {
        Map<String, String> attributes = new HashMap<>();
        String[] parts = line.split(";");
        for (String part : parts) {
            String[] keyValue = part.split("=");
            attributes.put(keyValue[0], keyValue[1]);
        }
        return attributes;
    }

    public GFF3Header readHeader() throws IOException, GFF3ValidationError {
        String line;
        while ((line = readLine()) != null) {
            if (line.isBlank()) {
                continue;
            }

            Matcher m = DIRECTIVE_VERSION.matcher(line);
            if (m.matches()) {
                String version = m.group("version");
                GFF3Header header = new GFF3Header(version);
                return header;
            } else if (!COMMENT.matcher(line).matches()) {
                // If the line is not a comment throw, otherwise ignore it
                throw new GFF3ValidationError(lineCount, "Invalid GFF3 header");
            }
        }
        throw new GFF3ValidationError(lineCount, "GFF3 header not found");
    }

    private String readLine() throws IOException {
        this.lineCount++;
        return this.bufferedReader.readLine();
    }
}
