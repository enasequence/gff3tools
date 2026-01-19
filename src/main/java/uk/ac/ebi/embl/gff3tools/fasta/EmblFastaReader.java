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
package uk.ac.ebi.embl.gff3tools.fasta;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import uk.ac.ebi.embl.gff3tools.exception.FastaHeaderParserException;
import uk.ac.ebi.embl.gff3tools.fasta.headerutils.FastaHeader;

public class EmblFastaReader {

    HashMap<String, String> accessionIdToSubmissionId;
    HashMap<String, String> submissionIdToFastaReaderId;
    HashMap<String, FastaHeader> submissionIdToFastaHeader;
    // HashMap<String, FastaEntry> submissionIdToFastaEntry;

    EmblFastaReader(File fastaFile) throws FastaHeaderParserException {
        // todo
    }

    // --------------- setters & getters ---------------------------

    public boolean setAccessionIdMapping(String accessionId, String submissionId) throws FastaHeaderParserException {
        return true;
    }

    public Collection<String> getSubmissionIds() throws FastaHeaderParserException {
        throw new UnknownError("Not implemented yet");
    }

    public FastaHeader getFastaHeaderBySubmissionId(String submissionId) throws FastaHeaderParserException {
        throw new UnknownError("Not implemented yet");
    }

    public FastaHeader getFastaHeaderByAccessionId(String accessionId) throws FastaHeaderParserException {
        throw new UnknownError("Not implemented yet");
    }

    // TODO add sequence getters

    // ------------------- helper functions ------------------------------

    private void readAll() {}
}
