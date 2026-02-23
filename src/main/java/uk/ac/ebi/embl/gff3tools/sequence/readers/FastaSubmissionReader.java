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
package uk.ac.ebi.embl.gff3tools.sequence.readers;

import java.io.File;
import java.io.Reader;
import java.util.*;
import uk.ac.ebi.embl.fastareader.FastaEntry;
import uk.ac.ebi.embl.fastareader.FastaReader;
import uk.ac.ebi.embl.fastareader.SequenceRangeOption;
import uk.ac.ebi.embl.fastareader.exception.FastaFileException;
import uk.ac.ebi.embl.fastareader.sequenceutils.SequenceAlphabet;
import uk.ac.ebi.embl.gff3tools.exception.FastaHeaderParserException;
import uk.ac.ebi.embl.gff3tools.sequence.IdType;
import uk.ac.ebi.embl.gff3tools.sequence.SequenceStats;
import uk.ac.ebi.embl.gff3tools.sequence.readers.headerutils.FastaHeader;
import uk.ac.ebi.embl.gff3tools.sequence.readers.headerutils.JsonHeaderParser;

public final class FastaSubmissionReader implements SubmissionSequenceReader {

    private FastaReader fastaReader;
    private final JsonHeaderParser headerParser = new JsonHeaderParser();

    // accession-submission id mapping
    private final HashMap<String, String> accessionIdToSubmissionId = new HashMap<>();
    private final HashMap<String, String> submissionIdToAccessionId = new HashMap<>();
    // id from reading
    private final List<String> orderedSubmissionIds = new ArrayList<>();
    private final HashMap<String, FastaHeader> submissionIdToFastaHeader = new HashMap<>();
    private final HashMap<String, FastaEntry> submissionIdToFastaEntry = new HashMap<>();

    public FastaSubmissionReader(File fastaFile) throws Exception {
        fastaReader = new FastaReader(fastaFile, SequenceAlphabet.defaultNucleotideAlphabet());
        parseData();
    }

    @Override
    public SubmissionType submissionType() {
        return SubmissionType.FASTA;
    }

    @Override
    public List<String> getOrderedIds(IdType idType) {
        return Collections.unmodifiableList(orderedSubmissionIds);
    }

    @Override
    public void setAccessionIds(List<String> orderedAccessionIds) throws Exception {
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

    @Override
    public Optional<FastaHeader> getHeader(IdType idType, String id) {
        var resolvedId = resolveId(idType, id);
        return Optional.ofNullable(submissionIdToFastaHeader.get(resolvedId));
    }

    @Override
    public SequenceStats getStats(IdType idType, String id) {
        var resolvedId = resolveId(idType, id);
        var entry = submissionIdToFastaEntry.get(resolvedId);
        if (entry == null) throw new IllegalArgumentException("No record for " + idType + ":" + id);

        return new SequenceStats(
                entry.totalBases,
                entry.totalBasesWithoutNBases,
                entry.leadingNsCount,
                entry.trailingNsCount,
                entry.baseCount);
    }

    @Override
    public String getSequenceSlice(IdType idType, String id, long fromBase, long toBase, SequenceRangeOption option)
            throws Exception {
        var resolvedId = resolveId(idType, id);
        var entry = submissionIdToFastaEntry.get(resolvedId);
        if (entry == null) {
            throw new FastaFileException("No entry found for submissionId: " + resolvedId);
        }
        return fastaReader.getSequenceSliceString(entry.getFastaReaderId(), fromBase, toBase, option);
    }

    @Override
    public Reader getSequenceSliceReader(
            IdType idType, String id, long fromBase, long toBase, SequenceRangeOption option) throws Exception {
        var resolvedId = resolveId(idType, id);
        var entry = submissionIdToFastaEntry.get(resolvedId);
        if (entry == null) {
            throw new FastaFileException("No entry found for submissionId: " + resolvedId);
        }
        return fastaReader.getSequenceSliceReader(entry.getFastaReaderId(), fromBase, toBase, option);
    }

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

    public String getSubmissionIdByAccessionId(String accessionId) {
        return accessionIdToSubmissionId.get(accessionId);
    }

    public String getAccessionIdBySubmissionId(String submissionId) {
        return submissionIdToAccessionId.get(submissionId);
    }

    // --------------------------------------- helper methods ---------------------------------------

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

    private String resolveId(IdType type, String id) {
        switch (type) {
            case ACCESSION_ID:
                return Objects.requireNonNull(
                        accessionIdToSubmissionId.get(id), () -> "No entry found for accessionId: " + id);
            case SUBMISSION_ID:
                return id;
            default:
                throw new IllegalStateException("Unknown IdType: " + type);
        }
    }
}
