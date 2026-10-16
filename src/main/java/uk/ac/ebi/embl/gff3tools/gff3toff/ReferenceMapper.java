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

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.embl.api.entry.XRef;
import uk.ac.ebi.embl.api.entry.reference.*;
import uk.ac.ebi.embl.gff3tools.metadata.AuthorData;
import uk.ac.ebi.embl.gff3tools.metadata.ReferenceData;
import uk.ac.ebi.embl.gff3tools.metadata.SubmissionAccount;
import uk.ac.ebi.embl.gff3tools.metadata.SubmitterDetails;

/**
 * Maps a master-entry {@link ReferenceData} (v1 or v2, see
 * docs/2610141424_reference_data_v2.md) to an EMBL {@link Reference}.
 *
 * <p>Public so that other consumers of master.json (e.g. ena-sequence-persistence) share
 * this one implementation instead of keeping a copy.
 *
 * <p>Dispatches on the v2 {@code type}. Records without a {@code type} keep the v1
 * behaviour: a Submission when nested submitter details contain a valid submitted date,
 * otherwise Unpublished. An unknown {@code type}, or a typed record missing its block,
 * logs a warning and is mapped as Unpublished. RP (locations) is the caller's concern.
 */
public final class ReferenceMapper {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReferenceMapper.class);
    private static final ReferenceFactory referenceFactory = new ReferenceFactory();

    private ReferenceMapper() {}

    public static Reference map(ReferenceData refData) {
        Publication publication = createPublication(refData);

        if (refData.getTitle() != null) {
            publication.setTitle(refData.getTitle());
        }

        if (refData.getConsortium() != null) {
            publication.setConsortium(refData.getConsortium());
        }

        List<AuthorData> authors = refData.getAuthors();
        if (authors == null) {
            SubmitterDetails submitterDetails = refData.getSubmitterDetails();
            authors = submitterDetails == null ? null : submitterDetails.getAuthors();
        }
        addPersons(authors, publication::addAuthor);

        if (refData.getXrefs() != null) {
            for (ReferenceData.Xref xref : refData.getXrefs()) {
                if (xref == null || xref.getSource() == null || xref.getPrimaryId() == null) continue;
                publication.addXRef(new XRef(xref.getSource(), xref.getPrimaryId(), xref.getSecondaryId()));
            }
        }

        Reference reference = referenceFactory.createReference(publication, refData.getReferenceNumber());

        if (refData.getReferenceComment() != null) {
            reference.setComment(refData.getReferenceComment());
        }
        return reference;
    }

    private static void addPersons(List<AuthorData> people, java.util.function.Consumer<Person> sink) {
        if (people == null) return;
        for (AuthorData person : people) {
            if (person == null) continue;
            String surname = person.getSurname() == null
                    ? ""
                    : person.getSurname().trim().replaceAll("\\s+", " ");
            String initials = toInitials(person.getFirstName()) + toInitials(person.getMiddleName());
            if (surname.isEmpty() && initials.isEmpty()) continue;
            sink.accept(referenceFactory.createPerson(
                    surname.isEmpty() ? null : surname, initials.isEmpty() ? null : initials));
        }
    }

    private static Publication createPublication(ReferenceData refData) {
        String type = refData.getType();
        if (type == null || type.isBlank()) {
            // v1: submission when submitterDetails carry a valid date, else unpublished
            Publication legacy = createSubmissionPublication(refData);
            return legacy != null ? legacy : referenceFactory.createUnpublished();
        }
        switch (type.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "SUBMISSION":
                return createV2Submission(refData);
            case "ARTICLE":
                return createArticle(refData);
            case "BOOK":
                return createBook(refData);
            case "THESIS":
                return createThesis(refData);
            case "PATENT":
                return createPatent(refData);
            case "ELECTRONIC":
                return createElectronic(refData);
            case "UNPUBLISHED":
                return createUnpublished(refData);
            default:
                LOGGER.warn("Unknown reference type '{}'; mapping as unpublished", type);
                return referenceFactory.createUnpublished();
        }
    }

    private static Publication createV2Submission(ReferenceData refData) {
        ReferenceData.Submission block = refData.getSubmission();
        if (block == null) {
            // tolerate v1-shaped submitter details under an explicit SUBMISSION type
            Publication legacy = createSubmissionPublication(refData);
            return legacy != null ? legacy : referenceFactory.createUnpublished();
        }
        Date day = block.getSubmittedDate() == null ? null : parseDate(block.getSubmittedDate());
        if (day == null) {
            LOGGER.warn("Submission reference has no valid submittedDate; mapping as unpublished");
            return referenceFactory.createUnpublished();
        }
        Submission submission = referenceFactory.createSubmission();
        submission.setDay(day);
        if (block.getSubmitterAddress() != null && !block.getSubmitterAddress().isBlank()) {
            submission.setSubmitterAddress(block.getSubmitterAddress());
        }
        return submission;
    }

    private static Publication createArticle(ReferenceData refData) {
        ReferenceData.Article block = refData.getArticle();
        if (block == null) return missingBlock(refData);
        Article article = referenceFactory.createArticle();
        article.setJournal(block.getJournal());
        article.setVolume(block.getVolume());
        article.setIssue(block.getIssue());
        article.setFirstPage(block.getFirstPage());
        article.setLastPage(block.getLastPage());
        article.setYear(yearToDate(block.getYear()));
        return article;
    }

    private static Publication createBook(ReferenceData refData) {
        ReferenceData.Book block = refData.getBook();
        if (block == null) return missingBlock(refData);
        Book book = referenceFactory.createBook();
        book.setBookTitle(block.getBookTitle());
        book.setPublisher(block.getPublisher());
        book.setFirstPage(block.getFirstPage());
        book.setLastPage(block.getLastPage());
        book.setYear(yearToDate(block.getYear()));
        addPersons(block.getEditors(), book::addEditor);
        return book;
    }

    private static Publication createThesis(ReferenceData refData) {
        ReferenceData.Thesis block = refData.getThesis();
        if (block == null) return missingBlock(refData);
        Thesis thesis = referenceFactory.createThesis();
        thesis.setInstitute(block.getInstitute());
        thesis.setYear(yearToDate(block.getYear()));
        return thesis;
    }

    private static Publication createPatent(ReferenceData refData) {
        ReferenceData.Patent block = refData.getPatent();
        if (block == null) return missingBlock(refData);
        Patent patent = referenceFactory.createPatent();
        patent.setPatentOffice(block.getPatentOffice());
        patent.setPatentNumber(block.getPatentNumber());
        patent.setPatentType(block.getPatentType());
        patent.setSequenceNumber(block.getSequenceNumber());
        if (block.getDay() != null) {
            patent.setDay(parseDate(block.getDay()));
        }
        if (block.getApplicants() != null) {
            block.getApplicants().stream().filter(Objects::nonNull).forEach(patent::addApplicant);
        }
        return patent;
    }

    private static Publication createElectronic(ReferenceData refData) {
        ReferenceData.Electronic block = refData.getElectronic();
        if (block == null) return missingBlock(refData);
        ElectronicReference electronic = referenceFactory.createElectronicReference();
        electronic.setText(block.getText());
        return electronic;
    }

    private static Publication createUnpublished(ReferenceData refData) {
        Unpublished unpublished = referenceFactory.createUnpublished();
        if (refData.getUnpublished() != null) {
            unpublished.setJournalBlock(refData.getUnpublished().getJournalBlock());
        }
        return unpublished;
    }

    private static Publication missingBlock(ReferenceData refData) {
        LOGGER.warn(
                "Reference type '{}' has no '{}' block; mapping as unpublished",
                refData.getType(),
                refData.getType().trim().toLowerCase(java.util.Locale.ROOT));
        return referenceFactory.createUnpublished();
    }

    private static Date yearToDate(Integer year) {
        if (year == null) return null;
        return Date.from(
                LocalDate.of(year, 1, 1).atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private static Submission createSubmissionPublication(ReferenceData refData) {
        SubmitterDetails submitterDetails = refData.getSubmitterDetails();
        // Submission RL lines require a valid submitted date. When submitter details are
        // absent or the date is missing/unparseable, fall back to an unpublished reference
        // instead of emitting a malformed EMBL submission location.
        if (submitterDetails == null || submitterDetails.getSubmittedDate() == null) {
            return null;
        }
        Date parsedDate = parseDate(submitterDetails.getSubmittedDate());
        if (parsedDate == null) {
            LOGGER.warn(
                    "Unable to map submission reference location due to invalid submittedDate '{}'",
                    submitterDetails.getSubmittedDate());
            return null;
        }
        Submission submission = referenceFactory.createSubmission();
        submission.setDay(parsedDate);
        String address = buildSubmitterAddress(submitterDetails.getSubmissionAccount());
        if (address != null && !address.isBlank()) {
            submission.setSubmitterAddress(address);
        }
        return submission;
    }

    private static String buildSubmitterAddress(SubmissionAccount account) {
        if (account == null) {
            return null;
        }
        String institutionLine = Stream.of(account.getCenterName(), account.getLaboratoryName())
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .reduce((left, right) -> left + ", " + right)
                .orElse(null);

        String addressLine = Stream.of(account.getAddress(), account.getCountry())
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .reduce((left, right) -> left + ", " + right)
                .orElse(null);

        if (institutionLine == null) {
            return addressLine;
        }
        if (addressLine == null) {
            return institutionLine;
        }
        // Join with a comma so the EMBL RL writer wraps on commas across the
        // whole address (rather than treating institution and address as two
        // separate blocks). The writer collapses any embedded newline to a
        // single space anyway, so a `\n` separator would yield the institution
        // and address running together with no separator.
        return institutionLine + ", " + addressLine;
    }

    /**
     * Reduce a name component (firstName or middleName) to compact EMBL initials,
     * each letter followed by a period, no separators between letters. Accepts:
     * full name ("Eleanor" → "E."), single initial with or without period
     * ("E." / "E" → "E."), and multiple initials separated by spaces and/or
     * periods ("E. P." / "E P" / "E.P." → "E.P."). Returns "" for null/blank.
     */
    public static String toInitials(String component) {
        if (component == null) return "";
        StringBuilder sb = new StringBuilder();
        for (String token : component.trim().split("\\s+")) {
            for (String sub : token.split("\\.")) {
                if (sub.isEmpty()) continue;
                sb.append(Character.toUpperCase(sub.charAt(0))).append('.');
            }
        }
        return sb.toString();
    }

    private static final DateTimeFormatter EMBL_DATE_FORMATTER = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("dd-MMM-yyyy")
            .toFormatter(Locale.ENGLISH);

    /**
     * Parses a date string into a {@link Date} whose calendar day (in the JVM
     * default zone) matches the UTC calendar day of the input. The downstream
     * EMBL writer reformats the {@link Date} back through {@link ZoneId#systemDefault()},
     * so anchoring start-of-day in the same zone keeps the rendered DT/Submitted
     * line stable across runners.
     *
     * <p>Supports: ISO-8601 offset/zoned datetimes, ISO-8601 instants ("...Z"),
     * ISO-8601 local datetimes / dates (assumed UTC), and EMBL "dd-MMM-yyyy".
     */
    public static Date parseDate(String dateStr) {
        LocalDate utcDate = parseToUtcLocalDate(dateStr);
        if (utcDate == null) {
            LOGGER.warn("Unable to parse date '{}'; skipping date mapping", dateStr);
            return null;
        }
        return Date.from(utcDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private static LocalDate parseToUtcLocalDate(String dateStr) {
        // ISO-8601 with offset (e.g. "2025-04-03T01:00:00+12:00", "...Z")
        try {
            return OffsetDateTime.parse(dateStr)
                    .toInstant()
                    .atZone(ZoneOffset.UTC)
                    .toLocalDate();
        } catch (DateTimeParseException ignored) {
            // not an offset datetime; try other formats
        }

        // ISO-8601 local datetime (no offset) — interpret as UTC
        try {
            return DateTimeFormatter.ISO_LOCAL_DATE_TIME.parse(dateStr, LocalDate::from);
        } catch (DateTimeParseException ignored) {
            // not a local datetime; try other formats
        }

        // ISO-8601 local date
        try {
            return LocalDate.parse(dateStr);
        } catch (DateTimeParseException ignored) {
            // not an ISO local date
        }

        // EMBL "dd-MMM-yyyy"
        try {
            return LocalDate.parse(dateStr, EMBL_DATE_FORMATTER);
        } catch (DateTimeParseException ignored) {
            // not EMBL date format
        }
        return null;
    }
}
