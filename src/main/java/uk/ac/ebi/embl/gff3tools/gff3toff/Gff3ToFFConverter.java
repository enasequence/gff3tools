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
package uk.ac.ebi.embl.gff3tools.gff3toff;

import java.io.*;
import java.nio.file.Path;
import java.util.List;
import uk.ac.ebi.embl.flatfile.writer.embl.EmblEntryWriter;
import uk.ac.ebi.embl.gff3tools.*;
import uk.ac.ebi.embl.gff3tools.exception.*;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.reader.GFF3FileReader;
import uk.ac.ebi.embl.gff3tools.validation.*;

public class Gff3ToFFConverter implements Converter {

    ValidationEngine validationEngine;
    Path gff3Path;

    public Gff3ToFFConverter(ValidationEngine validationEngine, Path gff3Path) {
        this.validationEngine = validationEngine;
        this.gff3Path = gff3Path;
    }

    public void convert(BufferedReader reader, BufferedWriter writer)
            throws ReadException, WriteException, ValidationException {

        try (GFF3FileReader gff3Reader = new GFF3FileReader(validationEngine, reader, gff3Path)) {

            gff3Reader.readHeader();
            gff3Reader.read(annotation -> writeEntry(new GFF3Mapper(gff3Reader), annotation, writer));

            // TODO: Decide how to expose parsingErrors to the user of this converter.// TODO: Decide how to expose
            // parsingErrors to the user of this converter.
            List<ValidationException> parsingErrors = validationEngine.getParsingErrors();

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Writes an EmblEntry to the provided BufferedWriter.
     *
     * @param mapper The GFF3Mapper instance used to convert GFF3Annotation to EmblEntry.
     * @param annotation The GFF3Annotation to be written.
     * @param writer The BufferedWriter to write the EmblEntry to.
     * @throws WriteException if an error occurs during writing.
     */
    private void writeEntry(GFF3Mapper mapper, GFF3Annotation annotation, BufferedWriter writer)
            throws WriteException, ValidationException {
        if (annotation != null) {
            EmblEntryWriter entryWriter = new EmblEntryWriter(mapper.mapGFF3ToEntry(annotation));
            entryWriter.setShowAcStartLine(false);
            try {
                entryWriter.write(writer);
            } catch (IOException e) {
                throw new WriteException(e);
            }
        }
    }
}
