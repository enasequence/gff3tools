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

import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import uk.ac.ebi.embl.gff3tools.exception.*;
import uk.ac.ebi.embl.gff3tools.gff3.*;
import uk.ac.ebi.embl.gff3tools.gff3.directives.GFF3Header;
import uk.ac.ebi.embl.gff3tools.gff3.directives.GFF3SequenceRegion;
import uk.ac.ebi.embl.gff3tools.gff3.directives.GFF3Species;
import uk.ac.ebi.embl.gff3tools.utils.Gff3Utils;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngine;

public class GFF3FileReader implements AutoCloseable {

    static Pattern VERSION_DIRECTIVE = Pattern.compile(
            "^##gff-version (?<version>(?<major>[0-9]+)(\\.(?<minor>[0-9]+)(:?\\.(?<patch>[0-9]+))?)?)\\s*$");
    static Pattern SPECIES_DIRECTIVE =
            Pattern.compile("^##species (?<taxonomyUrl>\\S*?[?&]name=(?<species>[A-Za-z]+(?: [A-Za-z]+)*))\\s*$");
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
    Map<String, GFF3SequenceRegion> accessionSequenceRegionMap = new TreeMap<>();
    ValidationEngine validationEngine;
    public GFF3Species gff3Species;
    private final Set<String> processedAccessions;

    public GFF3FileReader(ValidationEngine validationEngine, Reader reader) {
        this.validationEngine = validationEngine;
        this.bufferedReader = new BufferedReader(reader);
        lineCount = 0;
        currentAnnotation = new GFF3Annotation();
        processedAccessions = new HashSet<>();
    }

    public GFF3Annotation readAnnotation() throws IOException, ValidationException {

        String line;
        while ((line = readLine()) != null) {
            if (line.isBlank()) {
                // Ignore blank lines
                continue;
            }
            Matcher m = SPECIES_DIRECTIVE.matcher(line);
            if (m.matches()) {
                // Create species
                String species = m.group("taxonomyUrl");
                gff3Species = new GFF3Species(species);
            } else if ((m = SEQUENCE_REGION_DIRECTIVE.matcher(line)).matches()) {
                // Create directive
                GFF3SequenceRegion sequenceRegion = readSequenceRegion(m);
                accessionSequenceRegionMap.put(sequenceRegion.accession(), sequenceRegion);
            } else if (RESOLUTION_DIRECTIVE.matcher(line).matches()) {
                if (!currentAnnotation.getFeatures().isEmpty() || currentAnnotation.getSequenceRegion() != null) {
                    GFF3Annotation previousAnnotation = currentAnnotation;
                    currentAnnotation = new GFF3Annotation();
                    validationEngine.validateAnnotation(previousAnnotation, lineCount);
                    return previousAnnotation;
                }
                continue;
            } else if (COMMENT.matcher(line).matches()) {
                // Skip comment
                continue;
            } else if ((m = GFF3_FEATURE.matcher(line)).matches()) {

                GFF3Feature feature = readFeature(m);

                if (!feature.accession().equals(currentAccession)) {
                    // In case of different accession create a new GFF3Annotation and return the
                    // previous one.
                    currentAccession = feature.accession();
                    GFF3Annotation previousAnnotation = currentAnnotation;
                    currentAnnotation = new GFF3Annotation();
                    currentAnnotation.addFeature(feature);

                    // Validate and set the corresponding sequence region to the current annotation
                    validateAndSetSequenceRegion();

                    if (!previousAnnotation.getFeatures().isEmpty()) {
                        validationEngine.validateAnnotation(previousAnnotation, lineCount);
                        processedAccessions.add(previousAnnotation.getAccession());
                        return previousAnnotation;
                    }
                } else {
                    currentAnnotation.addFeature(feature);
                }

            } else {
                validationEngine.handleSyntacticError(
                        new InvalidGFF3RecordException(lineCount, "Invalid gff3 record \"" + line + "\""));
            }
        }

        // Handle the final annotation
        if (!currentAnnotation.getFeatures().isEmpty() || currentAnnotation.getSequenceRegion() != null) {
            GFF3Annotation finalAnnotation = currentAnnotation;
            currentAnnotation = new GFF3Annotation();
            validationEngine.validateAnnotation(finalAnnotation, lineCount);
            processedAccessions.add(finalAnnotation.getAccession());
            return finalAnnotation;
        }

        // Handle all the GFF3 annotations without features.
        for (String accession : accessionSequenceRegionMap.keySet()) {
            if (!processedAccessions.contains(accession)) {
                GFF3Annotation annotation = new GFF3Annotation();
                annotation.setSequenceRegion(accessionSequenceRegionMap.get(accession));
                validationEngine.validateAnnotation(annotation, lineCount);
                processedAccessions.add(accession);
                return annotation;
            }
        }
        return null;
    }

    @FunctionalInterface
    public interface AnnotationHandler<T> {
        void handle(T entry) throws WriteException;
    }

    public void read(AnnotationHandler<GFF3Annotation> annotationHandler)
            throws ValidationException, ReadException, WriteException {

        try {
            GFF3Annotation previousAnnotation = null;
            GFF3Annotation currentAnnotation;
            /**
             * The GFF3 reader returns an annotation every time it encounters a '###' directive or when the accession
             * number of a feature changes.
             * Since an EMBL Flat File (FF) cannot have multiple entries with the same accession, all annotations
             * pertaining to the same accession
             * must be merged into a single EmblEntry before being written to the output.
             */
            while ((currentAnnotation = readAnnotation()) != null) {
                // Merge the annotations if the accession is the same
                if (isSameAnnotation(previousAnnotation, currentAnnotation)) {
                    previousAnnotation.merge(currentAnnotation);
                } else {
                    // The accession is different, so write the previous annotation to EMBL
                    if (previousAnnotation != null) {
                        // writeEntry(mapper, previousAnnotation, writer);
                        annotationHandler.handle(previousAnnotation);
                    }
                    previousAnnotation = currentAnnotation;
                }
            }
            // After the loop, handle the last accumulated annotation.
            annotationHandler.handle(previousAnnotation);

        } catch (IOException e) {
            throw new ReadException(e);
        }
    }

    private boolean isSameAnnotation(GFF3Annotation previousAnnotation, GFF3Annotation currentAnnotation) {
        return previousAnnotation != null && currentAnnotation.getAccession().equals(previousAnnotation.getAccession());
    }

    private GFF3SequenceRegion readSequenceRegion(Matcher m) {

        String accessionId = m.group("accessionId");
        Optional<Integer> accessionVersion =
                Optional.ofNullable(m.group("accessionVersion")).map(Integer::parseInt);
        long start = Long.parseLong(m.group("start"));
        long end = Long.parseLong(m.group("end"));

        return new GFF3SequenceRegion(accessionId, accessionVersion, start, end);
    }

    private GFF3Feature readFeature(Matcher m) throws ValidationException {

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

        validationEngine.validateFeature(feature, lineCount);
        return feature;

    }

    private void validateAndSetSequenceRegion() throws ValidationException {
        if (accessionSequenceRegionMap.containsKey(currentAccession)) {
            GFF3SequenceRegion sequenceRegion = accessionSequenceRegionMap.get(currentAccession);
            currentAnnotation.setSequenceRegion(sequenceRegion);
        } else {
            validationEngine.handleSyntacticError(new UndefinedSeqIdException(
                    lineCount, "Undefined sequence region for accession \"" + currentAccession + "\""));
        }
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
                validationEngine.handleSyntacticError(
                        new InvalidGFF3HeaderException(lineCount, "Invalid gff3 header \"" + line + "\""));
            }
        }
        validationEngine.handleSyntacticError(new InvalidGFF3HeaderException(lineCount, "GFF3 header not found"));
        return null;
    }

    public GFF3Species getSpecies() {
        return gff3Species;
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
