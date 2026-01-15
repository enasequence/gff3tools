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

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.embl.api.entry.Entry;
import uk.ac.ebi.embl.api.validation.helper.FlatFileComparator;
import uk.ac.ebi.embl.api.validation.helper.FlatFileComparatorException;
import uk.ac.ebi.embl.api.validation.helper.FlatFileComparatorOptions;
import uk.ac.ebi.embl.flatfile.reader.ReaderOptions;
import uk.ac.ebi.embl.flatfile.reader.embl.EmblEntryReader;
import uk.ac.ebi.embl.flatfile.writer.embl.EmblEntryWriter;

/**
 * This class is not a part of converter, this is added here for testing purpose only.
 * We must remove this once the conversion testing is over.
 * */
public class FeatureComparator {

    private static final Logger LOG = LoggerFactory.getLogger(FeatureComparator.class);
    private static List<String> ignoreLines;
    public static void main(String[] args) {
        try {

            if (args.length == 3) {
                // reads the ignored lines from the passed file
                ignoreLines = Files.readAllLines(Path.of(args[2]));
            }

            compare(args[0], args[1], ignoreLines);

        } catch (FlatFileComparatorException e) {
            LOG.error(e.getMessage());
            System.exit(1);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void compare(String expectedFile, String actualFile, List<String> ignoreLines) throws FlatFileComparatorException, IOException {

        // A copy of the expected file(remove source feature, source qualifiers and sequence) for Comparision.
        String noSourceFile = createNoSourceFeatureFile(expectedFile);

        try {
            FlatFileComparator flatfileComparator = getFeatureComparator(ignoreLines);

            if (!flatfileComparator.compare(noSourceFile, actualFile)) {
                throw new FlatFileComparatorException("File comparison failed:  \n" + noSourceFile + "\n" + actualFile);
            }
            LOG.info("\n\nFeatures are identical for files: \n" + noSourceFile + "\n" + actualFile);
        } finally {
            LOG.info("Deleting file: " + noSourceFile);
            Files.deleteIfExists(Path.of(noSourceFile));
        }
    }

    private static FlatFileComparator getFeatureComparator(List<String> ignoreLines) throws IOException {

        FeatureComparatorOption options = new FeatureComparatorOption();
        // Ignore the below FT lines from the actual file
        options.setIgnoreLine("FT   source");
        options.setIgnoreLine("FT   region");
        options.setIgnoreLine("FT                   /circular_RNA");

        // Add the ignore lines from the command line
        ignoreLines.forEach(line -> options.setIgnoreLine(line));

        return new FlatFileComparator(options);
    }

    // Create a copy of the expected file, then remove the source feature, source qualifiers, and sequence
    public static String createNoSourceFeatureFile(String file) throws IOException {

        String fileWithoutSource = file + "_no_source";
        try (BufferedReader reader = new BufferedReader(new FileReader(file));
                BufferedWriter writer = new BufferedWriter(new FileWriter(fileWithoutSource))) {
            EmblEntryReader entryReader =
                    new EmblEntryReader(reader, EmblEntryReader.Format.EMBL_FORMAT, "embl_reader", getReaderOptions());
            while (entryReader.read() != null && entryReader.isEntry()) {
                Entry entry = entryReader.getEntry();
                entry.removeFeature(entry.getPrimarySourceFeature());

                EmblEntryWriter entryWriter = new EmblEntryWriter(entry);
                entryWriter.setShowAcStartLine(false);
                entryWriter.write(writer);
            }
        }
        return fileWithoutSource;
    }

    private static ReaderOptions getReaderOptions() {
        ReaderOptions readerOptions = new ReaderOptions();
        readerOptions.setIgnoreSequence(true);
        return readerOptions;
    }
}

class FeatureComparatorOption extends FlatFileComparatorOptions {
    @Override
    public boolean isIgnoreLine(String line) {
        // Ignore non FT and selected FT lines
        return !line.startsWith("FT") || super.isIgnoreLine(line);
    }
}
