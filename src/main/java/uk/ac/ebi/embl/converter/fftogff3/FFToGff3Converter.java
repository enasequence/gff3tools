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
package uk.ac.ebi.embl.converter.fftogff3;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import uk.ac.ebi.embl.api.entry.Entry;
import uk.ac.ebi.embl.converter.Converter;
import uk.ac.ebi.embl.converter.exception.*;
import uk.ac.ebi.embl.converter.gff3.GFF3File;
import uk.ac.ebi.embl.flatfile.reader.ReaderOptions;
import uk.ac.ebi.embl.flatfile.reader.embl.EmblEntryReader;

public class FFToGff3Converter implements Converter {

    // MasterFile will be used when converting reduced flatfile tto GFF3
    Path masterFilePath = null;

    public FFToGff3Converter(Path masterFilePath) {
        this.masterFilePath = masterFilePath;
    }

    public void convert(BufferedReader reader, BufferedWriter writer)
            throws ReadException, WriteException, ValidationException {
        EmblEntryReader entryReader =
                new EmblEntryReader(reader, EmblEntryReader.Format.EMBL_FORMAT, "embl_reader", getReaderOptions());

        GFF3FileFactory fftogff3 = new GFF3FileFactory();
        GFF3File file = fftogff3.from(entryReader, getMasterEntry(masterFilePath));
        file.writeGFF3String(writer);
    }

    private ReaderOptions getReaderOptions() {
        ReaderOptions readerOptions = new ReaderOptions();
        readerOptions.setIgnoreSequence(true);
        return readerOptions;
    }

    private Entry getMasterEntry(Path masterFilePath) throws ReadException {
        if(masterFilePath==null) {
            return null;
        }
        try (BufferedReader inputReader = Files.newBufferedReader(masterFilePath)) {
            Entry masterEntry = null;
            EmblEntryReader entryReader = new EmblEntryReader(
                    inputReader, EmblEntryReader.Format.EMBL_FORMAT, "embl_reader", getReaderOptions());
            while (entryReader.read() != null && entryReader.isEntry()) {
                masterEntry = entryReader.getEntry();
            }
            return masterEntry;
        } catch (IOException e) {
            throw new ReadException("Error opening master file: " + masterFilePath, e);
        }
    }
}
