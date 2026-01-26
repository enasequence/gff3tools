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
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.List;
import uk.ac.ebi.embl.fastareader.*;
import uk.ac.ebi.embl.fastareader.exception.FastaFileException;
import uk.ac.ebi.embl.fastareader.sequenceutils.SequenceAlphabet;
import uk.ac.ebi.embl.gff3tools.exception.FastaHeaderParserException;
import uk.ac.ebi.embl.gff3tools.fasta.headerutils.FastaHeader;
import uk.ac.ebi.embl.gff3tools.fasta.headerutils.JsonHeaderParser;

public class JsonHeaderFastaReader implements AutoCloseable {

    private FastaReader fastaReader;
    private JsonHeaderParser headerParser = new JsonHeaderParser();

    HashMap<String, String> submissionIdToAccessionId = new HashMap<>();
    HashMap<String, FastaHeader> accessionIdToFastaHeader = new HashMap<>();
    HashMap<String, FastaEntry> accessionIdToFastaEntry = new HashMap<>();

    public JsonHeaderFastaReader(File fastaFile, List<String> accessionIds)
            throws FastaHeaderParserException, FastaFileException, IOException {
        fastaReader = new FastaReader(fastaFile, SequenceAlphabet.defaultNucleotideAlphabet());
        parseData(accessionIds);
    }

    // --------------- interface ---------------------------

    public String getAccessionIdBySubmissionId(String submissionId)
            throws FastaHeaderParserException, FastaFileException {
        return submissionIdToAccessionId.getOrDefault(submissionId, null);
    }

    public FastaHeader getFastaHeaderByAccessionId(String accessionId) {
        return accessionIdToFastaHeader.getOrDefault(accessionId, null);
    }

    public FastaEntry getFastaEntryByAccessionId(String accessionId) {
        return accessionIdToFastaEntry.getOrDefault(accessionId, null);
    }

    public String getSequenceSlice(String accessionId, long fromBase, long toBase, SequenceRangeOption option)
            throws FastaFileException {
        var entry = accessionIdToFastaEntry.getOrDefault(accessionId, null);
        if (entry == null) {
            throw new FastaFileException("No entry found for accessionId: " + accessionId);
        }
        return fastaReader.getSequenceSliceString(entry.getFastaReaderId(), fromBase, toBase, option);
    }

    public Reader getSequenceSliceReader(String accessionId, long fromBase, long toBase, SequenceRangeOption option)
            throws FastaFileException {
        var entry = accessionIdToFastaEntry.getOrDefault(accessionId, null);
        if (entry == null) {
            throw new FastaFileException("No entry found for accessionId: " + accessionId);
        }
        return fastaReader.getSequenceSliceReader(entry.getFastaReaderId(), fromBase, toBase, option);
    }

    // ------------------ close and open new file ------------------

    public void openNewFile(File fastaFile, List<String> accessionIds)
            throws FastaHeaderParserException, FastaFileException, IOException {
        clearData();
        fastaReader.openNewFile(fastaFile);
        parseData(accessionIds);
    }

    @Override
    public void close() throws Exception {
        clearData();
        if (fastaReader != null) {
            fastaReader.close();
        }
    }

    private void parseData(List<String> accessionIds) throws FastaHeaderParserException, FastaFileException {
        List<FastaEntry> entries = fastaReader.getFastaEntries();
        if (entries.size() != accessionIds.size()) {
            throw new FastaFileException(
                    "Number of entries in the actual file does not match number of provided accession IDs");
        }
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.get(i);
            var accessionId = accessionIds.get(i);
            // parse headers
            var parsedHeader = headerParser.parse(entry.headerLine);
            accessionIdToFastaHeader.put(accessionId, parsedHeader.getHeader());
            // for easier access later
            accessionIdToFastaEntry.put(accessionId, entry);
            submissionIdToAccessionId.put(parsedHeader.getId(), accessionId);
        }
    }

    private void clearData() {
        accessionIdToFastaHeader.clear();
        accessionIdToFastaEntry.clear();
        submissionIdToAccessionId.clear();
    }
}
