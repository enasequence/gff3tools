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
import java.util.List;
import uk.ac.ebi.embl.converter.*;
import uk.ac.ebi.embl.converter.exception.*;
import uk.ac.ebi.embl.gff3tools.Converter;
import uk.ac.ebi.embl.gff3tools.exception.ReadException;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.exception.WriteException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.reader.GFF3FileReader;
import uk.ac.ebi.embl.converter.validation.*;
import uk.ac.ebi.embl.flatfile.writer.embl.EmblEntryWriter;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngine;

public class Gff3ToFFConverter implements Converter {

    ValidationEngine validationEngine;

    public Gff3ToFFConverter(ValidationEngine validationEngine) {
        this.validationEngine = validationEngine;
    }

    public void convert(BufferedReader reader, BufferedWriter writer)
            throws ReadException, WriteException, ValidationException {

        try (GFF3FileReader gff3Reader = new GFF3FileReader(validationEngine, reader)) {

            GFF3Mapper mapper = new GFF3Mapper();
            gff3Reader.readHeader();
            GFF3Annotation previousAnnotation = null;
            GFF3Annotation currentAnnotation;
            // The GFF3 reader returns an annotation every time it encounters a '###' directive or when the accession
            // number of a feature changes.
            // Since an EMBL Flat File (FF) cannot have multiple entries with the same accession, all annotations
            // pertaining to the same accession
            // must be merged into a single EmblEntry before being written to the output.
            while ((currentAnnotation = gff3Reader.readAnnotation()) != null) {
                // Merge the annotations if the accession is the same
                if (isSameAnnotation(previousAnnotation, currentAnnotation)) {
                    previousAnnotation.merge(currentAnnotation);
                } else {
                    // The accession is different, so write the previous annotation to EMBL
                    if (previousAnnotation != null) writeEntry(mapper, previousAnnotation, writer);
                    previousAnnotation = currentAnnotation;
                }
            }
            // After the loop, write the last accumulated annotation.
            writeEntry(mapper, previousAnnotation, writer);

            // Construct the GFF3File with the header, all annotations, and parsing errors.
            // The parsingErrors are accumulated in the validationEngine.
            List<ValidationException> parsingErrors = validationEngine.getParsingErrors();

        } catch (IOException e) {
            throw new ReadException(e);
        }

        // TODO: Decide how to expose parsingErrors to the user of this converter.
        // For now, they are simply collected in the validationEngine.
    }

    private boolean isSameAnnotation(GFF3Annotation previousAnnotation, GFF3Annotation currentAnnotation) {
        return previousAnnotation != null && currentAnnotation.getAccession().equals(previousAnnotation.getAccession());
    }

    /**
     * Writes an EmblEntry to the provided BufferedWriter.
     *
     * @param mapper The GFF3Mapper instance used to convert GFF3Annotation to EmblEntry.
     * @param annotation The GFF3Annotation to be written.
     * @param writer The BufferedWriter to write the EmblEntry to.
     * @throws WriteException if an error occurs during writing.
     */
    private void writeEntry(GFF3Mapper mapper, GFF3Annotation annotation, BufferedWriter writer) throws WriteException {
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
