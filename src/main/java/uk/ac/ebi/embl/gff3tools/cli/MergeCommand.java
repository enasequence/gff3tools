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
package uk.ac.ebi.embl.gff3tools.cli;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import uk.ac.ebi.embl.gff3tools.exception.CLIException;
import uk.ac.ebi.embl.gff3tools.merge.Gff3Merger;
import uk.ac.ebi.embl.gff3tools.merge.LocalFileMergeSource;

@CommandLine.Command(
        name = "merge",
        mixinStandardHelpOptions = true,
        description = "Merges two or more GFF3 files into a single document. "
                + "Annotation sections are concatenated in order; "
                + "FASTA sections are combined under a single ##FASTA directive.")
@Slf4j
public class MergeCommand implements Runnable {

    @CommandLine.Option(
            names = {"--output", "-o"},
            description = "Output GFF3 file path, or '-' for stdout.",
            required = true)
    public Path outputPath;

    @CommandLine.Option(
            names = {"--input-dir", "-d"},
            description = "Directory containing GFF3 files to merge. "
                    + "All *.gff3 files are picked up in alphabetical order; "
                    + "the output file is excluded if it lives in the same directory. "
                    + "Mutually exclusive with explicit file paths.")
    public Path inputDir;

    @CommandLine.Parameters(
            paramLabel = "<input-file>",
            arity = "0..*",
            description = "Two or more GFF3 input files to merge, in order. " + "Mutually exclusive with --input-dir.")
    public List<Path> inputPaths;

    @Override
    public void run() {
        List<Path> paths;
        try {
            paths = resolvePaths();
        } catch (CLIException | IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }

        List<LocalFileMergeSource> sources = new ArrayList<>();
        try {
            for (Path input : paths) {
                if (!Files.isRegularFile(input)) {
                    throw new CLIException("Input file not found: " + input);
                }
                sources.add(new LocalFileMergeSource(input));
            }
            if (inputDir != null) {
                sources.sort(Comparator.comparing(LocalFileMergeSource::getObjectId));
            }

            Gff3Merger merger = new Gff3Merger();

            if (isStdioSentinel(outputPath)) {
                suppressInfoLogs();
                merger.merge(sources, System.out);
                System.out.flush();
            } else {
                mergeToFile(merger, sources, outputPath);
            }

            log.info("Merged {} files into {}", paths.size(), isStdioSentinel(outputPath) ? "stdout" : outputPath);
        } catch (CLIException e) {
            throw new RuntimeException(e.getMessage(), e);
        } catch (IOException e) {
            throw new RuntimeException("Merge failed: " + e.getMessage(), e);
        }
    }

    private List<Path> resolvePaths() throws CLIException, IOException {
        boolean hasDir = inputDir != null;
        boolean hasPaths = inputPaths != null && !inputPaths.isEmpty();

        if (hasDir && hasPaths) {
            throw new CLIException("--input-dir and explicit file paths are mutually exclusive");
        }
        if (!hasDir && !hasPaths) {
            throw new CLIException("Provide either --input-dir or at least two explicit input files");
        }
        if (hasDir) {
            return collectFromDir(inputDir, outputPath);
        }
        if (inputPaths.size() < 2) {
            throw new CLIException(
                    "At least two input files are required, but only " + inputPaths.size() + " were provided");
        }
        return inputPaths;
    }

    private static List<Path> collectFromDir(Path dir, Path outputPath) throws CLIException, IOException {
        if (!Files.isDirectory(dir)) {
            throw new CLIException("--input-dir is not a directory: " + dir);
        }
        Path resolvedOutput = outputPath != null ? outputPath.toAbsolutePath().normalize() : null;
        List<Path> paths;
        try (var stream = Files.list(dir)) {
            paths = stream.filter(p -> p.getFileName().toString().endsWith(".gff3"))
                    .filter(p -> resolvedOutput == null
                            || !p.toAbsolutePath().normalize().equals(resolvedOutput))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
        }
        if (paths.size() < 2) {
            throw new CLIException(
                    "--input-dir " + dir + " contains fewer than 2 .gff3 files (found " + paths.size() + ")");
        }
        return paths;
    }

    private void mergeToFile(Gff3Merger merger, List<LocalFileMergeSource> sources, Path outputPath)
            throws IOException {
        Path tempFile = Files.createTempFile("gff3tools-merge-", ".tmp");
        try {
            try (OutputStream out = Files.newOutputStream(tempFile)) {
                merger.merge(sources, out);
            }
            try {
                Files.move(tempFile, outputPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tempFile, outputPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException deleteEx) {
                log.warn("Failed to delete temp file: {}", tempFile);
            }
            throw e;
        }
    }

    private static boolean isStdioSentinel(Path path) {
        String s = path.toString();
        return s.isEmpty() || s.equals("-");
    }

    private static void suppressInfoLogs() {
        LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
        ctx.getLogger(Logger.ROOT_LOGGER_NAME).setLevel(Level.WARN);
    }
}
