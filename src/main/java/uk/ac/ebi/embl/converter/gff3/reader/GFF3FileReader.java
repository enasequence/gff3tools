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

    Scanner scanner;

    public GFF3FileReader(Reader reader) {
        this.scanner = new Scanner(reader);
    }

    private GFF3Annotation readAnnotation() throws IOException, GFF3ValidationError {
        scanner.useDelimiter("\n");
        List<GFF3Directives.GFF3Directive> directives = new ArrayList<>();
        Map<String, List<GFF3Feature>> geneMap = new HashMap<>();
        List<GFF3Feature> nonGeneFeatures = new ArrayList<>();
        Map<String, GFF3Feature> featureIdx = new HashMap<>();

        while (scanner.hasNext()) {
            String line = scanner.next();
            if (line.isBlank()) {
                // Ignore blank lines
                break;
            }
            Matcher m = DIRECTIVE_SEQUENCE.matcher(line);
            if (m.matches()) {
                String accession = m.group("accession");
                long start = Long.parseLong(m.group("start"));
                long end = Long.parseLong(m.group("end"));
                // TODO: Validation no multiple sequence accession
                // TODO: Validation Sequence accession is required!
                GFF3Directives.GFF3SequenceRegion sequenceAccession =
                        new GFF3Directives.GFF3SequenceRegion(accession, start, end);
                directives.add(sequenceAccession);
                continue;
            }
            m = DIRECTIVE_SPECIES.matcher(line);
            if (m.matches()) {
                GFF3Directives.GFF3Species species = new GFF3Directives.GFF3Species(m.group("species"));
                // TODO: Validation no multiple species
                directives.add(species);
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
                id.ifPresent(s -> featureIdx.put(s, feature));

                if (parentId.isPresent()) {
                    if (featureIdx.containsKey(parentId.get())) {
                        featureIdx.get(parentId.get()).addChild(feature);
                    }
                    // TODO: Validation check, no parent found
                } else if (attributesMap.containsKey("gene")) {
                    String gene = attributesMap.get("gene");
                    geneMap.putIfAbsent(gene, new ArrayList<>());
                    List<GFF3Feature> geneFeatures = geneMap.get(gene);
                    geneFeatures.add(feature);
                } else {
                    nonGeneFeatures.add(feature);
                }
                // TODO: Validation check accession matches sequence region.

                continue;
            }
            break;
        }

        if (directives.isEmpty() && geneMap.isEmpty() && nonGeneFeatures.isEmpty()) {
            return null;
        }
        return new GFF3Annotation(new GFF3Directives(directives), geneMap, nonGeneFeatures);
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

    private List<GFF3Annotation> readAnnotationList() throws IOException, GFF3ValidationError, GFF3ValidationError {
        List<GFF3Annotation> annotations = new ArrayList<>();
        while (true) {
            // readAnnotation
            GFF3Annotation annotation = readAnnotation();
            if (annotation == null) {
                break;
            } else {
                annotations.add(annotation);
            }
        }
        return annotations;
    }

    public GFF3File read() throws IOException, GFF3ValidationError {

        scanner.useDelimiter("\n");
        if (scanner.hasNext()) {
            String line = scanner.next();
            Matcher m = DIRECTIVE_VERSION.matcher(line);
            if (m.matches()) {
                String version = m.group("version");
                GFF3Header header = new GFF3Header(version);
                List<GFF3Annotation> annotations = this.readAnnotationList();
                GFF3File gff3File = new GFF3File(header, annotations);
                return gff3File;
            } else {
                throw new GFF3ValidationError("Invalid GFF3 header");
            }
        } else {
            return null;
        }
    }
}
