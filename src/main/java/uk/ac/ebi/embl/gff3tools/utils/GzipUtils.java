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
package uk.ac.ebi.embl.gff3tools.utils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.GZIPInputStream;
import uk.ac.ebi.embl.gff3tools.exception.NonExistingFile;
import uk.ac.ebi.embl.gff3tools.exception.ReadException;

/** Small helpers for gzip detection and decompression. */
public final class GzipUtils {

    private static final int GZIP_MAGIC_BYTE1 = 0x1f;
    private static final int GZIP_MAGIC_BYTE2 = 0x8b;

    private GzipUtils() {}

    /** Returns {@code true} if the file starts with the gzip magic bytes. */
    public static boolean isGzipped(Path path) throws NonExistingFile, ReadException {
        try (InputStream peekStream = Files.newInputStream(path)) {
            int byte1 = peekStream.read();
            int byte2 = peekStream.read();
            return byte1 == GZIP_MAGIC_BYTE1 && byte2 == GZIP_MAGIC_BYTE2;
        } catch (NoSuchFileException e) {
            throw new NonExistingFile("The file does not exist: " + path, e);
        } catch (IOException e) {
            throw new ReadException("Error checking file format: " + path, e);
        }
    }

    /**
     * Decompresses a gzip-compressed file to a temporary file.
     *
     * <p>The temporary file is registered with {@link File#deleteOnExit()} as a backstop so the
     * JVM removes it on normal exit even if the caller's cleanup path is not reached.
     */
    public static Path decompressToTempFile(Path source, String prefix, String suffix) throws ReadException {
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile(prefix, suffix);
            tempFile.toFile().deleteOnExit();
            try (InputStream gzipIn = new GZIPInputStream(Files.newInputStream(source))) {
                Files.copy(gzipIn, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }
            return tempFile;
        } catch (IOException e) {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                    // Best-effort cleanup; the original exception is what matters.
                }
            }
            throw new ReadException("Failed to decompress gzipped file: " + source, e);
        }
    }
}
