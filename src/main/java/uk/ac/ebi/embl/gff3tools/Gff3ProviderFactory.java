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

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import uk.ac.ebi.embl.api.entry.Entry;
import uk.ac.ebi.embl.flatfile.reader.ReaderOptions;
import uk.ac.ebi.embl.flatfile.reader.embl.EmblEntryReader;
import uk.ac.ebi.embl.gff3tools.exception.CLIException;
import uk.ac.ebi.embl.gff3tools.exception.ExitException;
import uk.ac.ebi.embl.gff3tools.exception.NonExistingFile;
import uk.ac.ebi.embl.gff3tools.exception.ReadException;
import uk.ac.ebi.embl.gff3tools.metadata.EmblEntryMetadataSource;
import uk.ac.ebi.embl.gff3tools.metadata.MasterEntryJsonMetadataSource;
import uk.ac.ebi.embl.gff3tools.metadata.MasterMetadata;
import uk.ac.ebi.embl.gff3tools.metadata.MasterMetadataProvider;
import uk.ac.ebi.embl.gff3tools.metadata.MasterMetadataSource;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.CliFastaHeaderSource;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.FastaHeaderProvider;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.FileFastaHeaderSource;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.utils.FastaHeader;
import uk.ac.ebi.embl.gff3tools.validation.provider.CompositeSequenceProvider;
import uk.ac.ebi.embl.gff3tools.validation.provider.FileSequenceSource;

/**
 * Factory for building the context providers used by the GFF3 validation and conversion pipeline.
 *
 * <p>Extracted from {@code AbstractCommand} so library consumers (e.g., webin-gff3-stages) can
 * build providers without inheriting from the CLI command hierarchy.
 */
public final class Gff3ProviderFactory {

    private Gff3ProviderFactory() {}

    /**
     * Builds a {@link MasterMetadataProvider} from an optional master-entry file path.
     * Supports {@code .json} (MasterEntry JSON) and {@code .embl}/{@code .ff} (EMBL flatfile).
     * Returns an empty (no-source) provider when {@code masterEntryPath} is {@code null}.
     */
    public static MasterMetadataProvider buildMetadataProvider(Path masterEntryPath) throws ExitException {
        MasterMetadataProvider provider = new MasterMetadataProvider();
        if (masterEntryPath != null) {
            provider.addSource(parseMasterEntrySource(masterEntryPath));
        }
        return provider;
    }

    /**
     * Parses a master-entry file into the appropriate {@link MasterMetadataSource} by extension.
     * <ul>
     *   <li>{@code .json} → {@link MasterEntryJsonMetadataSource}</li>
     *   <li>{@code .embl} / {@code .ff} / {@code .dat}  → {@link EmblEntryMetadataSource}</li>
     *   <li> .dat file is produced by sequence processing pipeline
     * </ul>
     */
    public static MasterMetadataSource parseMasterEntrySource(Path path) throws ExitException {
        String ext = getFileExtension(path);
        return switch (ext) {
            case "json" -> parseMasterEntryJson(path);
            case "embl", "ff", "dat" -> parseMasterEntryEmbl(path);
            default ->
                throw new CLIException("Unrecognized --master-entry file extension '." + ext
                        + "'. Supported: .json (MasterEntry JSON), .embl/.ff (EMBL flatfile).");
        };
    }

    // ── FASTA header ──────────────────────────────────────────────────────────

    /**
     * Builds a {@link FastaHeaderProvider} from FASTA-embedded headers and an optional global JSON
     * header file. Embedded headers (from the sequence files) take priority over the global header.
     *
     * <p>Pass the same {@code sources} list used for {@link #buildCompositeProvider(List)} so each
     * FASTA file is opened only once.
     */
    public static FastaHeaderProvider buildHeaderProvider(List<FileSequenceSource> sources, Path fastaHeaderPath)
            throws ExitException {
        FastaHeaderProvider headerProvider = new FastaHeaderProvider();

        for (FileSequenceSource fss : sources) {
            Map<String, FastaHeader> headerMap = fss.getSeqIdToHeader();
            if (!headerMap.isEmpty()) {
                headerProvider.addSource(new FileFastaHeaderSource(headerMap));
            }
        }

        if (fastaHeaderPath != null) {
            headerProvider.addSource(new CliFastaHeaderSource(parseFastaHeaderJson(fastaHeaderPath)));
        }

        return headerProvider;
    }

    // ── Sequence ──────────────────────────────────────────────────────────────

    /**
     * Builds a {@link CompositeSequenceProvider} from pre-built {@link FileSequenceSource} instances.
     */
    public static CompositeSequenceProvider buildCompositeProvider(List<FileSequenceSource> sources) {
        CompositeSequenceProvider compositeProvider = new CompositeSequenceProvider();
        for (FileSequenceSource source : sources) {
            compositeProvider.addSource(source);
        }
        return compositeProvider;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private static String getFileExtension(Path path) {
        String name = path.getFileName().toString();
        if (name.endsWith(".gz")) {
            name = name.substring(0, name.length() - 3);
        }
        int dot = name.lastIndexOf('.');
        return (dot > 0 && dot < name.length() - 1) ? name.substring(dot + 1).toLowerCase() : "";
    }

    private static MasterEntryJsonMetadataSource parseMasterEntryJson(Path path) throws ExitException {
        if (!Files.exists(path)) {
            throw new NonExistingFile("The --master-entry file does not exist: " + path, null);
        }
        try {
            ObjectMapper mapper = JsonMapper.builder()
                    .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
                    .build();
            MasterMetadata meta = mapper.readValue(path.toFile(), MasterMetadata.class);
            return new MasterEntryJsonMetadataSource(meta);
        } catch (NoSuchFileException e) {
            throw new NonExistingFile("The --master-entry file does not exist: " + path, e);
        } catch (IOException e) {
            throw new ReadException(
                    "Failed to read --master-entry JSON file '%s': %s".formatted(path, e.getMessage()), e);
        }
    }

    private static EmblEntryMetadataSource parseMasterEntryEmbl(Path path) throws ExitException {
        if (!Files.exists(path)) {
            throw new NonExistingFile("The --master-entry file does not exist: " + path, null);
        }
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            ReaderOptions readerOptions = new ReaderOptions();
            readerOptions.setIgnoreSequence(true);
            EmblEntryReader entryReader =
                    new EmblEntryReader(reader, EmblEntryReader.Format.EMBL_FORMAT, "master_reader", readerOptions);
            Entry masterEntry = null;
            while (entryReader.read() != null && entryReader.isEntry()) {
                masterEntry = entryReader.getEntry();
            }
            if (masterEntry == null) {
                throw new CLIException("No entry found in --master-entry EMBL file: " + path);
            }
            return new EmblEntryMetadataSource(masterEntry);
        } catch (IOException e) {
            throw new ReadException(
                    "Failed to read --master-entry EMBL file '%s': %s".formatted(path, e.getMessage()), e);
        }
    }

    private static FastaHeader parseFastaHeaderJson(Path path) throws ExitException {
        if (!Files.exists(path)) {
            throw new NonExistingFile("The --fasta-header file does not exist: " + path, null);
        }
        try {
            ObjectMapper mapper = JsonMapper.builder()
                    .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
                    .build();
            return mapper.readValue(path.toFile(), FastaHeader.class);
        } catch (NoSuchFileException e) {
            throw new NonExistingFile("The --fasta-header file does not exist: " + path, e);
        } catch (IOException e) {
            throw new ReadException(
                    "Failed to read --fasta-header JSON file '%s': %s".formatted(path, e.getMessage()), e);
        }
    }
}
