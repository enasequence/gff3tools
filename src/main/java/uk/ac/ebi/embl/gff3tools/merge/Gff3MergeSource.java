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
package uk.ac.ebi.embl.gff3tools.merge;

import java.io.IOException;
import java.io.InputStream;

/**
 * A single GFF3 document that participates in a merge operation.
 *
 * <p>Implementations are responsible for opening and providing the raw bytes of their document.
 * Two paths exist:
 *
 * <ul>
 *   <li><b>Online (GridFS)</b>: {@code GridFsMergeSource} in webin-gff3-service uses a stored
 *       {@code fastaOffset} from MongoDB to split annotation and FASTA sections in one pass.
 *   <li><b>Offline (local file)</b>: {@code LocalFileMergeSource} uses
 *       {@link uk.ac.ebi.embl.gff3tools.gff3.reader.GFF3TranslationReader} backward scan.
 * </ul>
 */
public interface Gff3MergeSource {

    /** The object ID (filename without extension) of this GFF3 document. */
    String getObjectId();

    /**
     * Opens a sequential byte stream from the beginning of the GFF3 document. The stream starts with
     * {@code ##gff-version}. The caller is responsible for closing the returned stream.
     */
    InputStream openStream() throws IOException;

    /**
     * Returns the byte offset of the {@code ##FASTA} line within this document, or {@code -1} if the
     * document has no FASTA section. For online sources this is retrieved from stored metadata; for
     * offline sources it is found by backward scan.
     */
    long getFastaOffset() throws IOException;
}
