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
import java.util.*;
import uk.ac.ebi.embl.fastareader.*;
import uk.ac.ebi.embl.fastareader.exception.FastaFileException;
import uk.ac.ebi.embl.fastareader.sequenceutils.SequenceAlphabet;
import uk.ac.ebi.embl.gff3tools.exception.FastaHeaderParserException;
import uk.ac.ebi.embl.gff3tools.fasta.headerutils.FastaHeader;
import uk.ac.ebi.embl.gff3tools.fasta.headerutils.JsonHeaderParser;

public class JsonHeaderFastaReader implements AutoCloseable {

    private FastaReader fastaReader;
    private final JsonHeaderParser headerParser = new JsonHeaderParser();

    // accession-submission id mapping
    private final HashMap<String, String> accessionIdToSubmissionId = new HashMap<>();
    private final HashMap<String, String> submissionIdToAccessionId = new HashMap<>();
    // id from reading
    private final List<String> orderedSubmissionIds = new ArrayList<>();
    private final HashMap<String, FastaHeader> submissionIdToFastaHeader = new HashMap<>();
    private final HashMap<String, FastaEntry> submissionIdToFastaEntry = new HashMap<>();

    public JsonHeaderFastaReader(File fastaFile) throws FastaHeaderParserException, FastaFileException, IOException {
        fastaReader = new FastaReader(fastaFile, SequenceAlphabet.defaultNucleotideAlphabet());
        parseData();
    }

    public JsonHeaderFastaReader(File fastaFile, List<String> accessionIds)
            throws FastaHeaderParserException, FastaFileException, IOException {
        fastaReader = new FastaReader(fastaFile, SequenceAlphabet.defaultNucleotideAlphabet());
        parseData();
        setAccessionIds(accessionIds);
    }

    // --------------- interface ---------------------------

    public void setAccessionIds(List<String> orderedAccessionIds) throws FastaFileException {
        clearAccessionSubmissionMapping();
        if (orderedSubmissionIds.size() != orderedAccessionIds.size()) {
            throw new FastaFileException(
                    "Number of entries in the actual file does not match number of provided accession IDs");
        }
        for (int i = 0; i < orderedSubmissionIds.size(); i++) {
            var submissionId = orderedSubmissionIds.get(i);
            var accessionId = orderedAccessionIds.get(i);
            accessionIdToSubmissionId.put(accessionId, submissionId);
            submissionIdToAccessionId.put(submissionId, accessionId);
        }
    }

    public String getSubmissionIdByAccessionId(String accessionId) {
        return accessionIdToSubmissionId.get(accessionId);
    }

    public String getAccessionIdBySubmissionId(String submissionId) {
        return submissionIdToAccessionId.get(submissionId);
    }

    public FastaHeader getFastaHeaderBySubmissionId(String submissionId) {
        return submissionIdToFastaHeader.get(submissionId);
    }

    public FastaHeader getFastaHeaderByAccessionId(String accessionId) {
        var submissionId = accessionIdToSubmissionId.get(accessionId);
        if (submissionId == null) return null;
        return getFastaHeaderBySubmissionId(submissionId);
    }

    public FastaEntry getFastaEntryBySubmissionId(String submissionId) {
        return submissionIdToFastaEntry.get(submissionId);
    }

    public FastaEntry getFastaEntryByAccessionId(String accessionId) {
        var submissionId = accessionIdToSubmissionId.get(accessionId);
        if (submissionId == null) return null;
        return getFastaEntryBySubmissionId(submissionId);
    }

    public String getSequenceSliceByAccessionId(
            String accessionId, long fromBase, long toBase, SequenceRangeOption option) throws FastaFileException {
        var submissionId = accessionIdToSubmissionId.get(accessionId);
        if (submissionId == null) {
            throw new FastaFileException("No entry found for accessionId: " + accessionId);
        }
        return getSequenceSliceBySubmissionId(submissionId, fromBase, toBase, option);
    }

    public String getSequenceSliceBySubmissionId(
            String submissionId, long fromBase, long toBase, SequenceRangeOption option) throws FastaFileException {
        var entry = submissionIdToFastaEntry.get(submissionId);
        if (entry == null) {
            throw new FastaFileException("No entry found for submissionId: " + submissionId);
        }
        return fastaReader.getSequenceSliceString(entry.getFastaReaderId(), fromBase, toBase, option);
    }

    public Reader getSequenceSliceReaderByAccessionId(
            String accessionId, long fromBase, long toBase, SequenceRangeOption option) throws FastaFileException {
        var submissionId = accessionIdToSubmissionId.get(accessionId);
        if (submissionId == null) {
            throw new FastaFileException("No entry found for accessionId: " + accessionId);
        }
        return getSequenceSliceReaderBySubmissionId(submissionId, fromBase, toBase, option);
    }

    public Reader getSequenceSliceReaderBySubmissionId(
            String submissionId, long fromBase, long toBase, SequenceRangeOption option) throws FastaFileException {
        var entry = submissionIdToFastaEntry.get(submissionId);
        if (entry == null) {
            throw new FastaFileException("No entry found for submissionId: " + submissionId);
        }
        return fastaReader.getSequenceSliceReader(entry.getFastaReaderId(), fromBase, toBase, option);
    }

    // ------------------ close file ------------------

    @Override
    public void close() throws Exception {
        clearData();
        if (fastaReader != null) {
            fastaReader.close();
        }
    }

    private void parseData() throws FastaHeaderParserException, FastaFileException {
        var entries = fastaReader.getFastaEntries();
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.get(i);
            // parse headers
            var parsedHeader = headerParser.parse(entry.headerLine);
            var submissionId = parsedHeader.getId();
            // check for duplicate submission ID
            if (submissionIdToFastaEntry.containsKey(submissionId)) {
                throw new FastaFileException("Duplicate submission ID detected: " + submissionId);
            }
            // for easier access later
            orderedSubmissionIds.add(submissionId);
            submissionIdToFastaHeader.put(submissionId, parsedHeader.getHeader());
            submissionIdToFastaEntry.put(submissionId, entry);
        }
    }

    private void clearData() {
        orderedSubmissionIds.clear();
        submissionIdToFastaHeader.clear();
        submissionIdToFastaEntry.clear();
        clearAccessionSubmissionMapping();
    }

    private void clearAccessionSubmissionMapping() {
        submissionIdToAccessionId.clear();
        accessionIdToSubmissionId.clear();
    }
}
