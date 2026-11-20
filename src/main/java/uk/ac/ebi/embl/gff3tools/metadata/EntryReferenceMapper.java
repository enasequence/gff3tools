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

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.embl.api.entry.XRef;
import uk.ac.ebi.embl.api.entry.location.LocalRange;
import uk.ac.ebi.embl.api.entry.reference.Article;
import uk.ac.ebi.embl.api.entry.reference.Book;
import uk.ac.ebi.embl.api.entry.reference.ElectronicReference;
import uk.ac.ebi.embl.api.entry.reference.Patent;
import uk.ac.ebi.embl.api.entry.reference.Person;
import uk.ac.ebi.embl.api.entry.reference.Publication;
import uk.ac.ebi.embl.api.entry.reference.Reference;
import uk.ac.ebi.embl.api.entry.reference.Submission;
import uk.ac.ebi.embl.api.entry.reference.Thesis;
import uk.ac.ebi.embl.api.entry.reference.Unpublished;

/**
 * Maps the references of a parsed EMBL flat file ({@link Reference}, RN/RP/RG/RA/RT/RL/RX lines)
 * to master-entry {@link ReferenceData} records (v2, with a {@code type} discriminator and a
 * type-specific block). This is the inverse of {@code ReferenceMapper}.
 *
 * <p>Authors keep the flat file's "Surname Initials." split: the initials land in {@code firstName}
 * and {@code middleName} stays empty, since the flat file does not separate them.
 */
public final class EntryReferenceMapper {

    private static final Logger LOGGER = LoggerFactory.getLogger(EntryReferenceMapper.class);

    private EntryReferenceMapper() {}

    public static List<ReferenceData> map(List<Reference> references) {
        List<ReferenceData> result = new ArrayList<>();
        if (references == null) {
            return result;
        }
        for (Reference reference : references) {
            if (reference != null && reference.getPublication() != null) {
                result.add(map(reference));
            }
        }
        return result;
    }

    public static ReferenceData map(Reference reference) {
        Publication publication = reference.getPublication();
        ReferenceData data = new ReferenceData();
        data.setReferenceNumber(reference.getReferenceNumber());
        data.setReferencePosition(referencePosition(reference));
        data.setReferenceComment(reference.getComment());
        data.setTitle(publication.getTitle());
        data.setConsortium(publication.getConsortium());
        data.setAuthors(persons(publication.getAuthors()));
        data.setXrefs(xrefs(publication.getXRefs()));

        if (publication instanceof Article article) {
            data.setType("ARTICLE");
            ReferenceData.Article block = new ReferenceData.Article();
            block.setJournal(article.getJournal());
            block.setVolume(article.getVolume());
            block.setIssue(article.getIssue());
            block.setFirstPage(article.getFirstPage());
            block.setLastPage(article.getLastPage());
            block.setYear(year(article.getYear()));
            data.setArticle(block);
        } else if (publication instanceof Book book) {
            data.setType("BOOK");
            ReferenceData.Book block = new ReferenceData.Book();
            block.setBookTitle(book.getBookTitle());
            block.setPublisher(book.getPublisher());
            block.setFirstPage(book.getFirstPage());
            block.setLastPage(book.getLastPage());
            block.setYear(year(book.getYear()));
            block.setEditors(persons(book.getEditors()));
            data.setBook(block);
        } else if (publication instanceof Thesis thesis) {
            data.setType("THESIS");
            ReferenceData.Thesis block = new ReferenceData.Thesis();
            block.setInstitute(stripTrailingPeriod(thesis.getInstitute()));
            block.setYear(year(thesis.getYear()));
            data.setThesis(block);
        } else if (publication instanceof Patent patent) {
            data.setType("PATENT");
            ReferenceData.Patent block = new ReferenceData.Patent();
            block.setPatentOffice(patent.getPatentOffice());
            block.setPatentNumber(patent.getPatentNumber());
            block.setPatentType(patent.getPatentType());
            block.setSequenceNumber(patent.getSequenceNumber());
            block.setDay(isoDate(patent.getDay()));
            block.setApplicants(
                    patent.getApplicants() == null || patent.getApplicants().isEmpty()
                            ? null
                            : new ArrayList<>(patent.getApplicants()));
            data.setPatent(block);
        } else if (publication instanceof Submission submission) {
            data.setType("SUBMISSION");
            ReferenceData.ReferenceSubmission block = new ReferenceData.ReferenceSubmission();
            block.setSubmittedDate(isoDate(submission.getDay()));
            block.setSubmitterAddress(submission.getSubmitterAddress());
            data.setSubmission(block);
        } else if (publication instanceof ElectronicReference electronic) {
            data.setType("ELECTRONIC");
            ReferenceData.Electronic block = new ReferenceData.Electronic();
            block.setText(electronic.getText());
            data.setElectronic(block);
        } else if (publication instanceof Unpublished unpublished) {
            data.setType("UNPUBLISHED");
            if (unpublished.getJournalBlock() != null) {
                ReferenceData.Unpublished block = new ReferenceData.Unpublished();
                block.setJournalBlock(unpublished.getJournalBlock());
                data.setUnpublished(block);
            }
        } else {
            LOGGER.warn(
                    "Reference {} has unsupported publication type {}; mapping as unpublished",
                    reference.getReferenceNumber(),
                    publication.getClass().getSimpleName());
            data.setType("UNPUBLISHED");
        }
        return data;
    }

    /** RP ranges as "1-100" or "1-100,200-300"; null when the reference has none. */
    private static String referencePosition(Reference reference) {
        if (reference.getLocations() == null
                || reference.getLocations().getLocations().isEmpty()) {
            return null;
        }
        return reference.getLocations().getLocations().stream()
                .map((LocalRange range) -> range.getBeginPosition() + "-" + range.getEndPosition())
                .collect(Collectors.joining(","));
    }

    private static List<AuthorData> persons(List<Person> people) {
        if (people == null || people.isEmpty()) {
            return null;
        }
        List<AuthorData> authors = new ArrayList<>();
        for (Person person : people) {
            if (person == null) continue;
            AuthorData author = new AuthorData();
            author.setSurname(person.getSurname());
            author.setFirstName(person.getFirstName());
            authors.add(author);
        }
        return authors.isEmpty() ? null : authors;
    }

    private static List<ReferenceData.Xref> xrefs(List<XRef> xrefs) {
        if (xrefs == null || xrefs.isEmpty()) {
            return null;
        }
        List<ReferenceData.Xref> result = new ArrayList<>();
        for (XRef xref : xrefs) {
            ReferenceData.Xref out = new ReferenceData.Xref();
            out.setSource(xref.getDatabase());
            out.setPrimaryId(xref.getPrimaryAccession());
            out.setSecondaryId(xref.getSecondaryAccession());
            result.add(out);
        }
        return result;
    }

    // The flat file's RL line ends in a full stop that the writer adds back, so keeping it would double it.
    private static String stripTrailingPeriod(String text) {
        return text != null && text.endsWith(".") ? text.substring(0, text.length() - 1) : text;
    }

    private static Integer year(Date date) {
        return date == null ? null : toLocalDate(date).getYear();
    }

    private static String isoDate(Date date) {
        return date == null ? null : toLocalDate(date).toString();
    }

    // The reader builds Dates at start-of-day in the JVM default zone, so read them back the same way.
    private static LocalDate toLocalDate(Date date) {
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }
}
