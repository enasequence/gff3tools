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
package uk.ac.ebi.embl.converter.cli;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.embl.api.entry.Entry;
import uk.ac.ebi.embl.api.validation.helper.FlatFileComparator;
import uk.ac.ebi.embl.api.validation.helper.FlatFileComparatorException;
import uk.ac.ebi.embl.api.validation.helper.FlatFileComparatorOptions;
import uk.ac.ebi.embl.flatfile.reader.ReaderOptions;
import uk.ac.ebi.embl.flatfile.reader.embl.EmblEntryReader;
import uk.ac.ebi.embl.flatfile.writer.embl.EmblEntryWriter;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * This class is not a part of converter, this is added here for testing purpose only.
 * We must remove this once the conversion testing is over.
 * */
public class FeatureComparator {

    private static final Logger LOG = LoggerFactory.getLogger(FeatureComparator.class);

    public static void main(String[] args) {
        try {

            compare(args[0], args[1]);

        } catch (FlatFileComparatorException e) {
            LOG.error(e.getMessage());
            System.exit(1);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void compare(String expectedFile, String actualFile) throws FlatFileComparatorException, IOException {
        removeSourceFeatureFromExpected(expectedFile);

        FlatFileComparator flatfileComparator = getFeatureComparator();

        if (!flatfileComparator.compare(expectedFile, actualFile)) {
            throw new FlatFileComparatorException("File comparison failed:  \n" + expectedFile + "\n" + actualFile);
        }
        LOG.info("\n\nFeatures are identical for files: \n" + expectedFile + "\n" + actualFile);
    }

    private static FlatFileComparator getFeatureComparator() {
        FeatureComparatorOption options = new FeatureComparatorOption();
        // Ignore the below FT lines
        options.setIgnoreLine("FT   source"); // This has to be done as converter adds source featire
        options.setIgnoreLine("FT   region");
        options.setIgnoreLine("FT                   /circular_RNA=true");
        /*options.setIgnoreLine("FT                   /organism");
        options.setIgnoreLine("FT                   /plasmid");
        options.setIgnoreLine("FT                   /isolate");
        options.setIgnoreLine("FT                   /mol_type");

        options.setIgnoreLine("FT                   /circular_RNA=true");

        // Added from feature table
        options.setIgnoreLine("FT                   /altitude");
        options.setIgnoreLine("FT                   /bio_material");
        options.setIgnoreLine("FT                   /cell_line");
        options.setIgnoreLine("FT                   /cell_type");
        options.setIgnoreLine("FT                   /chromosome");
        options.setIgnoreLine("FT                   /clone");
        options.setIgnoreLine("FT                   /collected_by");
        options.setIgnoreLine("FT                   /collection_date");
        options.setIgnoreLine("FT                   /cultivar");
        options.setIgnoreLine("FT                   /culture_collection");
        options.setIgnoreLine("FT                   /dev_stage");
        options.setIgnoreLine("FT                   /ecotype");
        options.setIgnoreLine("FT                   /environmental_sample");
        options.setIgnoreLine("FT                   /focus");
        options.setIgnoreLine("FT                   /geo_loc_name");
        options.setIgnoreLine("FT                   /germline");
        options.setIgnoreLine("FT                   /haplogroup");
        options.setIgnoreLine("FT                   /haplotype");
        options.setIgnoreLine("FT                   /host");
        options.setIgnoreLine("FT                   /isolate");
        options.setIgnoreLine("FT                   /isolation_source");
        options.setIgnoreLine("FT                   /lab_host");
        options.setIgnoreLine("FT                   /lat_lon");
        options.setIgnoreLine("FT                   /macronuclear");
        options.setIgnoreLine("FT                   /mating_type");
        options.setIgnoreLine("FT                   /metagenome_source");
        options.setIgnoreLine("FT                   /note");
        options.setIgnoreLine("FT                   /PCR_primers");
        options.setIgnoreLine("FT                   /plasmid");
        options.setIgnoreLine("FT                   /proviral");
        options.setIgnoreLine("FT                   /rearranged");
        options.setIgnoreLine("FT                   /segment");
        options.setIgnoreLine("FT                   /serotype");
        options.setIgnoreLine("FT                   /serovar");
        options.setIgnoreLine("FT                   /sex");
        options.setIgnoreLine("FT                   /specimen_voucher");
        options.setIgnoreLine("FT                   /strain");
        options.setIgnoreLine("FT                   /submitter_seqid");
        options.setIgnoreLine("FT                   /sub_species");
        options.setIgnoreLine("FT                   /tissue_type");
        options.setIgnoreLine("FT                   /type_material");
        options.setIgnoreLine("FT                   /variety");
        options.setIgnoreLine("FT                   /country");*/
        return new FlatFileComparator(options);
    }

    public static void removeSourceFeatureFromExpected(String file) throws IOException {

        String fileWithoutSource = file+"_no_source";
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
            Files.move(Paths.get(fileWithoutSource),Paths.get(file), StandardCopyOption.REPLACE_EXISTING);
        }

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
