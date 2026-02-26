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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
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
    static Pattern SPECIES_DIRECTIVE = Pattern.compile("^##species (?<species>.*)$");
    static Pattern SEQUENCE_REGION_DIRECTIVE = Pattern.compile(
            "^##sequence-region\\s+(?<accession>(?<accessionId>[^.]+)(?:\\.(?<accessionVersion>\\d+))?)\\s+(?<start>[0-9]+)\\s+(?<end>[0-9]+)$");
    static Pattern RESOLUTION_DIRECTIVE = Pattern.compile("^###$");
    static Pattern TRANSLATION_ID_PATTERN = Pattern.compile("^>(?<translationId>.*)");
    static Pattern COMMENT = Pattern.compile("^#.*$");

    BufferedReader bufferedReader;
    int lineCount;
    GFF3Annotation currentAnnotation;
    String currentAccession;
    Map<String, GFF3SequenceRegion> accessionSequenceRegionMap = new TreeMap<>();
    ValidationEngine validationEngine;
    public GFF3Species gff3Species;
    private final Set<String> processedAccessions;

    private Map<String, OffsetRange> translationMap;
    private final GFF3TranslationReader translationReader;

    // Used by GFF3 conversion process
    public GFF3FileReader(ValidationEngine validationEngine, Reader reader, Path gff3Path) {
        this.validationEngine = validationEngine;
        this.bufferedReader = new BufferedReader(reader);
        lineCount = 0;
        currentAnnotation = new GFF3Annotation();
        processedAccessions = new HashSet<>();
        translationReader = new GFF3TranslationReader(validationEngine, gff3Path);
    }

    // Used by GFF3 processing pipeline
    public GFF3FileReader(ValidationEngine validationEngine, Path gff3Path) throws IOException {
        this(validationEngine, Files.newBufferedReader(gff3Path),gff3Path);
    }

    public GFF3Annotation readAnnotation() throws IOException, ValidationException {

        String line;
        GFF3Feature feature;
        while ((line = readLine()) != null) {
            if (line.isBlank()) {
                // Ignore blank lines
                continue;
            }
            if (line.startsWith("##FASTA")) {
                break;
            }
            Matcher m = SPECIES_DIRECTIVE.matcher(line);
            if (m.matches()) {
                // Create species
                String species = m.group("species");
                gff3Species = new GFF3Species(species);
            } else if ((m = SEQUENCE_REGION_DIRECTIVE.matcher(line)).matches()) {
                // Create directive
                GFF3SequenceRegion sequenceRegion = readSequenceRegion(m);
                accessionSequenceRegionMap.put(sequenceRegion.accession(), sequenceRegion);
            } else if (RESOLUTION_DIRECTIVE.matcher(line).matches()) {
                if (!currentAnnotation.getFeatures().isEmpty() || currentAnnotation.getSequenceRegion() != null) {
                    GFF3Annotation previousAnnotation = currentAnnotation;
                    currentAnnotation = new GFF3Annotation();
                    validationEngine.validate(previousAnnotation, lineCount);
                    return previousAnnotation;
                }
                continue;
            } else if ((feature = readFeature(line)) != null) {
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
                        validationEngine.validate(previousAnnotation, lineCount);
                        processedAccessions.add(previousAnnotation.getAccession());
                        return previousAnnotation;
                    }
                } else {
                    currentAnnotation.addFeature(feature);
                }
            } else if (COMMENT.matcher(line).matches()) {
                // Skip comment
                continue;
            } else if (TRANSLATION_ID_PATTERN.matcher(line).matches()) {
                return null;
            } else {
                validationEngine.handleSyntacticError(
                        new InvalidGFF3RecordException(lineCount, "Invalid gff3 record \"" + line + "\""));
            }
        }

        // Handle the final annotation
        if (!currentAnnotation.getFeatures().isEmpty() || currentAnnotation.getSequenceRegion() != null) {
            GFF3Annotation finalAnnotation = currentAnnotation;
            currentAnnotation = new GFF3Annotation();
            validationEngine.validate(finalAnnotation, lineCount);
            processedAccessions.add(finalAnnotation.getAccession());
            return finalAnnotation;
        }

        // Handle all the GFF3 annotations without features.
        for (String accession : accessionSequenceRegionMap.keySet()) {
            if (!processedAccessions.contains(accession)) {
                GFF3Annotation annotation = new GFF3Annotation();
                annotation.setSequenceRegion(accessionSequenceRegionMap.get(accession));
                validationEngine.validate(annotation, lineCount);
                processedAccessions.add(accession);
                return annotation;
            }
        }
        return null;
    }

    private Map<String, OffsetRange> getTranslationMap() {
        if (translationMap == null) {
            translationMap = translationReader.readTranslationOffset();
        }
        return translationMap;
    }

    @FunctionalInterface
    public interface AnnotationHandler<T> {
        void handle(T entry) throws WriteException, ValidationException;
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

            validationEngine.executeExits();
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

    private GFF3Feature readFeature(String line) throws ValidationException {

        String[] parts = line.split("\t");
        if (parts.length < 9) {
            return null; // GFF3 features must have at least 9 fields
        }
        String accession = parts[0];
        String source = parts[1];
        String name = parts[2];
        String start_str = parts[3];
        String end_str = parts[4];
        String score = parts[5];
        String strand = parts[6];
        String phase = parts[7];
        String attributes = parts[8];

        Map<String, List<String>> attributesMap = attributesFromString(attributes);

        Optional<String> id = Optional.ofNullable(attributesMap.get("ID"))
                .filter((l) -> !l.isEmpty())
                .map((l) -> l.get(0));
        Optional<String> parentId = Optional.ofNullable(attributesMap.get("Parent"))
                .filter((l) -> !l.isEmpty())
                .map((l) -> l.get(0));

        if (!accession.isEmpty()
                && !source.isEmpty()
                && !name.isEmpty()
                && (strand.equals("+") || strand.equals("-") || strand.equals(".") || strand.equals("?"))
                && isValidNumber(start_str)
                && isValidNumber(end_str)) {

            String[] accessionParts = accession.split(".");

            String accessionId = accessionParts.length > 0 ? accessionParts[0] : parts[0];
            Optional<Integer> accessionVersion = Optional.ofNullable(
                            accessionParts.length > 1 ? accessionParts[0] : null)
                    .map(Integer::parseInt);

            long start = Long.parseLong(start_str);
            long end = Long.parseLong(end_str);

            GFF3Feature feature = new GFF3Feature(
                    id, parentId, accessionId, accessionVersion, source, name, start, end, score, strand, phase);
            feature.addAttributes(attributesMap);

            validationEngine.validate(feature, lineCount);
            return feature;
        } else {
            return null;
        }
    }

    private boolean isValidNumber(String n) {
        byte[] bytes = n.getBytes();
        for (byte aByte : bytes) {
            if (aByte < '0' || aByte > '9') {
                return false;
            }
        }
        return true;
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

    public Map<String, List<String>> attributesFromString(String line) {
        Map<String, List<String>> attributes = new LinkedHashMap<>();
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

    public Map<String, OffsetRange> getTranslationOffsetForAnnotation(GFF3Annotation annotation) {
        return getTranslationMap().entrySet().stream()
                .filter(e -> e.getKey().startsWith(annotation.getAccession()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public Map<String, OffsetRange> getTranslationOffsetMap() {
        return getTranslationMap();
    }

    public String getTranslation(OffsetRange offsetRange) {
        return translationReader.readTranslation(offsetRange);
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
