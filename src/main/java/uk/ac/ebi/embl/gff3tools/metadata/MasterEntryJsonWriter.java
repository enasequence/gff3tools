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
package uk.ac.ebi.embl.gff3tools.metadata;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import uk.ac.ebi.embl.api.entry.Entry;
import uk.ac.ebi.embl.api.entry.XRef;

/**
 * Builds the master-entry JSON (see docs/reference-data-v2/master-entry.schema.json) for a parsed
 * EMBL flat file entry and writes it to disk.
 *
 * <p>Starts from {@link EmblEntryMetadataSource} (identity, description, classification, taxonomy,
 * keywords, project) and adds what that source leaves out: references, DT dates and release
 * numbers, sequence length and DR cross-references. Fields the flat file does not carry (assembly
 * level, chromosome, search fields, ...) are left out of the JSON rather than guessed.
 */
public final class MasterEntryJsonWriter {

    private static final DateTimeFormatter EMBL_DATE = DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH);

    private static final ObjectWriter WRITER = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_EMPTY)
            .writerWithDefaultPrettyPrinter();

    private MasterEntryJsonWriter() {}

    public static MasterMetadata fromEntry(Entry entry) {
        MasterMetadata meta = new EmblEntryMetadataSource(entry).getMetadata();

        meta.setFirstPublic(emblDate(entry.getFirstPublic()));
        meta.setFirstPublicRelease(entry.getFirstPublicRelease());
        meta.setLastUpdated(emblDate(entry.getLastUpdated()));
        meta.setLastUpdatedRelease(entry.getLastUpdatedRelease());

        // ID-line length: the sequence itself is not read (ignoreSequence), so entry.getSequence()
        // reports 0 and the count in the ID line is the only length available.
        if (entry.getIdLineSequenceLength() > 0) {
            meta.setSequenceLength(entry.getIdLineSequenceLength());
        }

        mapCrossReferences(entry, meta);
        meta.setReferences(EntryReferenceMapper.map(entry.getReferences()));
        return meta;
    }

    /**
     * The inverse of the DR handling in GFF3Mapper: BioSample and MD5 have their own fields, ENA
     * links without a secondary accession are run accessions, "ENA; ...; SET" links are rebuilt
     * from the accession and wgsSet on the way back, and everything else is a publication.
     */
    private static void mapCrossReferences(Entry entry, MasterMetadata meta) {
        List<String> runAccessions = new ArrayList<>();
        List<CrossReference> publications = new ArrayList<>();
        for (XRef xref : entry.getXRefs()) {
            String db = xref.getDatabase();
            String id = xref.getPrimaryAccession();
            if (db == null || id == null) continue;
            if ("BioSample".equalsIgnoreCase(db)) {
                meta.setSample(id);
            } else if ("MD5".equalsIgnoreCase(db)) {
                meta.setMd5(id);
            } else if ("ENA".equalsIgnoreCase(db)) {
                if (xref.getSecondaryAccession() == null) {
                    runAccessions.add(id);
                }
            } else {
                CrossReference publication = new CrossReference();
                publication.setSource(db);
                publication.setPrimaryId(id);
                publication.setSecondaryId(xref.getSecondaryAccession());
                publications.add(publication);
            }
        }
        meta.setRunAccessions(runAccessions);
        meta.setPublications(publications);
    }

    /** Writes the JSON to {@code path}, replacing it atomically so a failure never leaves a partial file. */
    public static void write(MasterMetadata metadata, Path path) throws IOException {
        Path temp = Files.createTempFile(path.toAbsolutePath().getParent(), "master-", ".json.tmp");
        try {
            WRITER.writeValue(temp.toFile(), metadata);
            try {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static String emblDate(Date date) {
        return date == null
                ? null
                : date.toInstant()
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate()
                        .format(EMBL_DATE)
                        .toUpperCase(Locale.ENGLISH);
    }
}
