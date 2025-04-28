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
package uk.ac.ebi.embl.converter.gff3toff;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.embl.converter.cli.Params;
import uk.ac.ebi.embl.converter.fftogff3.FFtoGFF3ConversionError;
import uk.ac.ebi.embl.converter.gff3.reader.GFF3FileReader;

public class Gff3ToFFConverter {

    private static final Logger LOG = LoggerFactory.getLogger(Gff3ToFFConverter.class);

    public void convert(Params params) throws FFtoGFF3ConversionError {
        Path filePath = params.inFile.toPath();
        try (BufferedReader bufferedReader = Files.newBufferedReader(filePath);
                StringWriter ffWriter = new StringWriter()) {
            GFF3FileReader gff3Reader = new GFF3FileReader(bufferedReader);
            FFEntryFactory ffEntryFactory = new FFEntryFactory();
            EmblFlatFile emblFlatFile = ffEntryFactory.from(gff3Reader);
            emblFlatFile.writeFFString(ffWriter);
            Files.write(params.outFile.toPath(), ffWriter.toString().getBytes());
            LOG.info("Embl flat file is written in: {}", params.outFile.toPath());
        } catch (IOException e) {
            throw new FFtoGFF3ConversionError("Error reading file " + filePath, e);
        }
    }
}
