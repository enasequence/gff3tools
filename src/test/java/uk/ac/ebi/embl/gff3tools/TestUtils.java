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
package uk.ac.ebi.embl.gff3tools;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import uk.ac.ebi.embl.fastareader.SequenceRangeOption;
import uk.ac.ebi.embl.fastareader.SequenceStats;
import uk.ac.ebi.embl.fastareader.sequenceutils.GapRegion;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.gff3.directives.GFF3SequenceRegion;
import uk.ac.ebi.embl.gff3tools.utils.OntologyClient;
import uk.ac.ebi.embl.gff3tools.utils.OntologyTerm;
import uk.ac.ebi.embl.gff3tools.validation.ContextProvider;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;
import uk.ac.ebi.embl.gff3tools.validation.ValidationRegistry;
import uk.ac.ebi.embl.gff3tools.validation.provider.SequenceSource;

public class TestUtils {

    /** {@code seq1}: ATG, 29 TTT codons and TAA (1-93), then 7 bases. Translates to {@link #CDS_TRANSLATION}. */
    public static final String CDS_SEQUENCE = "ATG" + "TTT".repeat(29) + "TAA" + "TTTTTTT";

    /** The translation of {@code cds1} over {@link #CDS_SEQUENCE} 1-93. */
    public static final String CDS_TRANSLATION = "M" + "F".repeat(29);

    /** A protein of the same length that is not {@link #CDS_TRANSLATION}. */
    public static final String WRONG_CDS_TRANSLATION = "M" + "K".repeat(29);

    /**
     * Writes {@code input.gff3} to {@code dir}: one CDS {@code cds1} over {@code seq1} 1-93 with the
     * extra column-9 attributes, and a {@code ##FASTA} section holding {@code fastaRecords} when it
     * isn't empty.
     */
    public static Path writeCdsGff3(Path dir, String extraAttributes, String fastaRecords) throws IOException {
        Path gff3 = dir.resolve("input.gff3");
        Files.writeString(
                gff3,
                "##gff-version 3\n##sequence-region seq1 1 100\n"
                        + "seq1\t.\tCDS\t1\t93\t.\t+\t0\tID=cds1" + extraAttributes + "\n"
                        + (fastaRecords.isEmpty() ? "" : "##FASTA\n" + fastaRecords));
        return gff3;
    }

    /** Writes {@code cds.fasta} to {@code dir}: {@code >seq1} with a JSON header and {@link #CDS_SEQUENCE}. */
    public static Path writeCdsSequenceFasta(Path dir) throws IOException {
        Path fasta = dir.resolve("cds.fasta");
        Files.writeString(
                fasta,
                ">seq1 | {\"description\":\"test\", \"molecule_type\":\"genomic DNA\", \"topology\":\"linear\"}\n"
                        + CDS_SEQUENCE + "\n");
        return fasta;
    }

    /** The records of a GFF3 document's {@code ##FASTA} section, header to protein, lines joined. */
    public static Map<String, String> fastaRecordsOf(String gff3) {
        Map<String, String> records = new LinkedHashMap<>();
        int marker = gff3.indexOf("##FASTA");
        if (marker < 0) {
            return records;
        }
        String header = null;
        for (String line : gff3.substring(marker + "##FASTA".length()).split("\n")) {
            if (line.startsWith(">")) {
                header = line.substring(1).trim();
                records.put(header, "");
            } else if (header != null && !line.isBlank()) {
                records.merge(header, line.trim(), String::concat);
            }
        }
        return records;
    }

    /** The reports of one rule. */
    public static List<ValidationException> reportsOf(List<ValidationException> reports, String rule) {
        return reports.stream()
                .filter(report -> rule.equals(report.getValidationRule()))
                .toList();
    }

    /**
     * A {@link SequenceSource} that serves a gap-free sequence of {@code length} bases for every
     * seqId and records each {@code release()} call as {@code "release:<seqId>"} in {@code events}.
     */
    public static SequenceSource recordingSequenceSource(List<String> events, long length) {
        return new SequenceSource() {
            @Override
            public boolean hasSequence(String seqId) {
                return true;
            }

            @Override
            public String getSequenceSlice(String seqId, long fromBase, long toBase, SequenceRangeOption option) {
                return "A".repeat((int) (toBase - fromBase + 1));
            }

            @Override
            public long getSequenceLength(String seqId, SequenceRangeOption option) {
                return length;
            }

            @Override
            public SequenceStats getSequenceStats(String seqId) {
                return new SequenceStats(length, length, 0L, 0L, Map.of('A', length));
            }

            @Override
            public List<GapRegion> getGapRegions(String seqId, SequenceRangeOption option) {
                return List.of();
            }

            @Override
            public List<GapRegion> getGapRegions(String seqId, long fromBase, long toBase, SequenceRangeOption option) {
                return List.of();
            }

            @Override
            public Set<String> knownSeqIds() {
                return Set.of();
            }

            @Override
            public Reader getSequenceSliceReader(String seqId, long fromBase, long toBase, SequenceRangeOption option) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void release(String seqId) {
                events.add("release:" + seqId);
            }
        };
    }

    /**
     * A {@link SequenceSource} serving the given sequences by seqId, gap-free. Unknown seqIds are
     * reported as absent, so a composite lookup falls through as it does for a real FASTA file.
     */
    public static SequenceSource inMemorySequenceSource(Map<String, String> sequences) {
        return new SequenceSource() {
            @Override
            public boolean hasSequence(String seqId) {
                return sequences.containsKey(seqId);
            }

            @Override
            public String getSequenceSlice(String seqId, long fromBase, long toBase, SequenceRangeOption option) {
                return sequences.get(seqId).substring((int) fromBase - 1, (int) toBase);
            }

            @Override
            public long getSequenceLength(String seqId, SequenceRangeOption option) {
                return sequences.get(seqId).length();
            }

            @Override
            public SequenceStats getSequenceStats(String seqId) {
                String sequence = sequences.get(seqId);
                Map<Character, Long> baseCount = new HashMap<>();
                for (char base : sequence.toCharArray()) {
                    baseCount.merge(base, 1L, Long::sum);
                }
                return new SequenceStats(sequence.length(), sequence.length(), 0L, 0L, baseCount);
            }

            @Override
            public List<GapRegion> getGapRegions(String seqId, SequenceRangeOption option) {
                return List.of();
            }

            @Override
            public List<GapRegion> getGapRegions(String seqId, long fromBase, long toBase, SequenceRangeOption option) {
                return List.of();
            }

            @Override
            public Set<String> knownSeqIds() {
                return sequences.keySet();
            }

            @Override
            public Reader getSequenceSliceReader(String seqId, long fromBase, long toBase, SequenceRangeOption option) {
                return new StringReader(getSequenceSlice(seqId, fromBase, toBase, option));
            }
        };
    }

    public static final String DEFAULT_ACCESSION = "1234";

    public static BufferedReader getResourceReaderWithPath(String path) throws IOException {
        FileReader reader = new FileReader(path);
        return new BufferedReader(reader);
    }

    public static BufferedReader getResourceReader(String resourceName) throws IOException {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        URL resource = classLoader.getResource(resourceName);
        if (resource != null) {
            return getResourceReaderWithPath(resource.getPath());
        }
        return null;
    }

    public static Map<String, Path> getTestFiles(String resourceName, String extension) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        URL resource = classLoader.getResource(resourceName);
        Map<String, Path> inFiles = new LinkedHashMap<>();
        if (resource != null) {
            File folder = new File(resource.getPath());
            for (File file : Objects.requireNonNull(folder.listFiles())) {
                if (file.getName().endsWith(extension)) {
                    inFiles.put(file.getName().replace(extension, ""), file.toPath());
                }
            }
        } else {
            System.out.println("Directory not found!");
        }
        return inFiles;
    }

    public static File getResourceFile(String resourceName) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        URL resource = classLoader.getResource(resourceName);
        if (resource != null) {
            return new File(resource.getPath());
        }
        return null;
    }

    public static GFF3Feature createGFF3Feature(Optional<String> featureName, Optional<String> parentFeatureName) {
        String id = featureName.map(v -> v + "_gene").orElse("_gene");
        String parent = parentFeatureName.map(v -> v + "_gene").orElse("_gene");

        GFF3Feature feature = new GFF3Feature(
                featureName,
                parentFeatureName,
                DEFAULT_ACCESSION,
                Optional.empty(),
                ".",
                featureName.get(),
                1,
                800,
                ".",
                "+",
                "");
        feature.addAttribute("ID", id);
        feature.addAttribute(GFF3Attributes.ATTRIBUTE_PARENT, parent);
        feature.addAttribute("gene", "geneX");
        return feature;
    }

    public static GFF3Feature createGFF3Feature(
            String featureName, String parentFeatureName, Map<String, List<String>> attributes) {

        GFF3Feature feature = new GFF3Feature(
                Optional.of(featureName),
                Optional.of(parentFeatureName),
                DEFAULT_ACCESSION,
                Optional.empty(),
                ".",
                featureName,
                1,
                800,
                ".",
                "+",
                "");
        feature.addAttributes(attributes);
        return feature;
    }

    public static GFF3Feature createGFF3Feature(String featureName, long start, long end) {

        return new GFF3Feature(
                Optional.of(featureName),
                Optional.empty(),
                DEFAULT_ACCESSION,
                Optional.empty(),
                ".",
                featureName,
                start,
                end,
                ".",
                "+",
                "");
    }

    public static GFF3Feature createGFF3Feature(String featureName, Map<String, List<String>> attributes) {

        GFF3Feature feature = new GFF3Feature(
                Optional.of(featureName),
                Optional.empty(),
                DEFAULT_ACCESSION,
                Optional.empty(),
                ".",
                featureName,
                1,
                800,
                ".",
                "+",
                "");

        feature.addAttributes(attributes);
        return feature;
    }

    public static GFF3Feature createComplementFeature() {
        return new GFF3Feature(
                java.util.Optional.of(OntologyTerm.CDS.name()),
                java.util.Optional.empty(),
                TestUtils.DEFAULT_ACCESSION,
                java.util.Optional.empty(),
                ".",
                OntologyTerm.CDS.name(),
                1,
                800,
                ".",
                "-",
                "");
    }

    public static GFF3Feature createGFF3Feature(
            String featureName, long start, long end, Map<String, List<String>> attributes) {

        GFF3Feature feature = new GFF3Feature(
                Optional.of(featureName),
                Optional.empty(),
                DEFAULT_ACCESSION,
                Optional.empty(),
                ".",
                featureName,
                start,
                end,
                ".",
                "+",
                "");
        feature.addAttributes(attributes);
        return feature;
    }

    public static GFF3Feature createGFF3Feature(
            String featureName, String parentFeatureName, String seqId, Map<String, List<String>> attributes) {

        GFF3Feature feature = new GFF3Feature(
                Optional.of(featureName),
                Optional.of(parentFeatureName),
                seqId,
                Optional.empty(),
                ".",
                featureName,
                1,
                800,
                ".",
                "+",
                "");

        feature.addAttributes(attributes);
        return feature;
    }

    public static GFF3Feature createGFF3FeatureWithAccession(
            String seqId, String name, Map<String, List<String>> attributes) {
        GFF3Feature feature = new GFF3Feature(
                Optional.of(name),
                Optional.empty(),
                seqId, // seqId -> controls accession()
                Optional.empty(), // version
                ".",
                name,
                1,
                100,
                ".",
                "+",
                "");

        feature.addAttributes(attributes);
        return feature;
    }

    public static GFF3Feature createGFF3Feature(
            String featureName, String seqId, long start, long end, Map<String, List<String>> attributes) {

        GFF3Feature feature = new GFF3Feature(
                Optional.of(featureName),
                Optional.empty(),
                seqId,
                Optional.empty(),
                ".",
                featureName,
                start,
                end,
                ".",
                "+",
                "");
        feature.addAttributes(attributes);
        return feature;
    }

    /**
     * Creates a ValidationContext with the real OntologyClient and injects it
     * into the target's @InjectContext fields via reflection.
     */
    public static void injectContext(Object target) {
        ValidationContext context = createTestContext();
        injectContext(target, context);
    }

    /**
     * Creates a ValidationContext with a custom OntologyClient and injects it
     * into the target's @InjectContext fields via reflection.
     */
    public static void injectContext(Object target, OntologyClient ontologyClient) {
        ValidationContext context = createTestContext(ontologyClient);
        injectContext(target, context);
    }

    public static ValidationContext createTestContext() {
        return createTestContext(OntologyClient.getInstance());
    }

    public static ValidationContext createTestContext(OntologyClient ontologyClient) {
        ValidationContext context = new ValidationContext();
        context.register(OntologyClient.class, new ContextProvider<>() {
            @Override
            public OntologyClient get(ValidationContext ctx) {
                return ontologyClient;
            }

            @Override
            public Class<OntologyClient> type() {
                return OntologyClient.class;
            }
        });
        return context;
    }

    public static void injectContext(Object target, ValidationContext context) {
        ValidationRegistry.injectContext(target, context);
    }

    /**
     * Registers a fixed value on the context under {@code type}, replacing the anonymous
     * {@link ContextProvider} subclass that tests would otherwise hand-roll for every stub.
     */
    public static <T> void registerProvider(ValidationContext context, Class<T> type, T value) {
        context.register(type, new ContextProvider<>() {
            @Override
            public T get(ValidationContext ctx) {
                return value;
            }

            @Override
            public Class<T> type() {
                return type;
            }
        });
    }

    /**
     * A {@link GFF3Annotation} with its {@code ##sequence-region} directive set, so
     * {@link GFF3Annotation#getAccession()} resolves without needing any features.
     */
    public static GFF3Annotation createGFF3Annotation(String accession, long start, long end, GFF3Feature... features) {
        GFF3Annotation annotation = new GFF3Annotation();
        annotation.setSequenceRegion(new GFF3SequenceRegion(accession, Optional.empty(), start, end));
        for (GFF3Feature feature : features) {
            annotation.addFeature(feature);
        }
        return annotation;
    }

    public static String defaultAccession() {
        return DEFAULT_ACCESSION;
    }

    public static GFF3Feature createGFF3Feature(
            String id, String parentId, String featureName, String seqId, long start, long end) {
        GFF3Feature feature = new GFF3Feature(
                id != null ? Optional.of(id) : Optional.empty(),
                parentId != null ? Optional.of(parentId) : Optional.empty(),
                seqId,
                Optional.empty(),
                ".",
                featureName,
                start,
                end,
                ".",
                "+",
                ".");
        return feature;
    }

    public static GFF3Feature createGFF3Feature(
            String id, String featureName, String seqId, long start, long end, String strand) {
        return new GFF3Feature(
                Optional.ofNullable(id),
                Optional.empty(),
                seqId,
                Optional.empty(),
                ".",
                featureName,
                start,
                end,
                ".",
                strand,
                "");
    }
}
