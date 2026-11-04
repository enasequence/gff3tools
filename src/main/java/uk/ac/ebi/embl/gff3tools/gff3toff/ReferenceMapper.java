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
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.embl.api.entry.XRef;
import uk.ac.ebi.embl.api.entry.location.LocalRange;
import uk.ac.ebi.embl.api.entry.location.LocationFactory;
import uk.ac.ebi.embl.api.entry.location.Order;
import uk.ac.ebi.embl.api.entry.reference.*;
import uk.ac.ebi.embl.gff3tools.metadata.AuthorData;
import uk.ac.ebi.embl.gff3tools.metadata.ReferenceData;
import uk.ac.ebi.embl.gff3tools.metadata.SubmissionAccount;
import uk.ac.ebi.embl.gff3tools.metadata.SubmitterDetails;

/**
 * Maps a master-entry {@link ReferenceData} (v1 or v2, see
 * docs/2610141424_reference_data_v2.md) to an EMBL {@link Reference}.
 *
 * <p>Public so that other consumers of master.json share this one implementation instead of
 * keeping a copy.
 *
 * <p>Dispatches on the v2 {@code type}. Records without a {@code type} keep the v1
 * behaviour: a Submission when nested submitter details contain a valid submitted date,
 * otherwise Unpublished. An unknown {@code type}, or a typed record missing its block,
 * logs a warning and is mapped as Unpublished, as is a typed record whose block lacks the
 * fields its RL line needs. On v2 records {@code referencePosition} ("1-100" or "1-100,200-300")
 * becomes the RP locations; v1 records get none. Callers may override them (GFF3Mapper does for
 * WGS contigs).
 */
public final class ReferenceMapper {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReferenceMapper.class);
    private static final ReferenceFactory referenceFactory = new ReferenceFactory();
    private static final LocationFactory locationFactory = new LocationFactory();
    private static final Pattern RANGE = Pattern.compile("\\s*(\\d+)\\s*-\\s*(\\d+)\\s*");
    /** RX databases the EMBL flat file defines; others are rendered with a WARN. */
    private static final Set<String> RX_SOURCES = Set.of("DOI", "PUBMED", "AGRICOLA");

    private ReferenceMapper() {}

    public static Reference map(ReferenceData refData) {
        Publication publication = createPublication(refData);

        if (refData.getTitle() != null) {
            publication.setTitle(refData.getTitle());
        }

        if (refData.getConsortium() != null) {
            publication.setConsortium(refData.getConsortium());
        }

        // v1 records take their authors from submitterDetails only; a top-level authors list is
        // ignored, as before v2. v2 records never fall back: an empty authors[] means "no RA"
        // (e.g. a consortium-only submitter).
        List<AuthorData> authors;
        if (isBlank(refData.getType())) {
            SubmitterDetails submitterDetails = refData.getSubmitterDetails();
            authors = submitterDetails == null ? null : submitterDetails.getAuthors();
        } else {
            authors = refData.getAuthors();
        }
        addPersons(authors, publication::addAuthor);

        addXrefs(refData.getXrefs(), publication);

        Reference reference = referenceFactory.createReference(publication, refData.getReferenceNumber());

        if (refData.getReferenceComment() != null) {
            reference.setComment(refData.getReferenceComment());
        }

        // RP from referencePosition is new in v2; v1 records never rendered it
        if (!isBlank(refData.getType())) {
            Order<LocalRange> locations = parseReferencePosition(refData.getReferencePosition());
            if (locations != null) {
                reference.setLocations(locations);
            }
        }
        return reference;
    }

    private static void addXrefs(List<ReferenceData.Xref> xrefs, Publication publication) {
        if (xrefs == null) return;
        for (ReferenceData.Xref xref : xrefs) {
            if (xref == null || isBlank(xref.getSource()) || isBlank(xref.getPrimaryId())) continue;
            String source = xref.getSource().trim().toUpperCase(Locale.ROOT);
            if (!RX_SOURCES.contains(source)) {
                LOGGER.warn("Reference xref source '{}' is not DOI, PUBMED or AGRICOLA; rendering it anyway", source);
            }
            publication.addXRef(new XRef(source, xref.getPrimaryId().trim(), xref.getSecondaryId()));
        }
    }

    /** Parses "1-100" or "1-100,200-300" into RP locations; null (with a WARN) when it is not that shape. */
    private static Order<LocalRange> parseReferencePosition(String position) {
        if (isBlank(position)) {
            return null;
        }
        Order<LocalRange> locations = new Order<>();
        for (String part : position.split(",")) {
            Matcher m = RANGE.matcher(part);
            if (!m.matches()) {
                LOGGER.warn("Unparseable referencePosition '{}'; RP line omitted", position);
                return null;
            }
            long begin;
            long end;
            try {
                begin = Long.parseLong(m.group(1));
                end = Long.parseLong(m.group(2));
            } catch (NumberFormatException e) {
                // the regex only admits digits, so this is a number too large for a long
                LOGGER.warn("Out-of-range referencePosition '{}'; RP line omitted", position);
                return null;
            }
            if (begin < 1 || end < begin) {
                LOGGER.warn("Invalid referencePosition '{}'; RP line omitted", position);
                return null;
            }
            locations.addLocation(locationFactory.createLocalRange(begin, end));
        }
        return locations;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static void addPersons(List<AuthorData> people, Consumer<Person> sink) {
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
            Submission legacy = buildSubmission(null, refData.getSubmitterDetails());
            return legacy != null ? legacy : referenceFactory.createUnpublished();
        }
        switch (type.trim().toUpperCase(Locale.ROOT)) {
            case "SUBMISSION":
                return createReferenceSubmission(refData);
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

    private static Publication createReferenceSubmission(ReferenceData refData) {
        Submission submission = buildSubmission(refData.getSubmission(), refData.getSubmitterDetails());
        if (submission == null) {
            LOGGER.warn("Submission reference has no valid submittedDate in its submission block or "
                    + "submitterDetails; mapping as unpublished");
            return referenceFactory.createUnpublished();
        }
        return submission;
    }

    /**
     * Merges field by field: the submission block (v2, may be null) wins, submitterDetails (v1)
     * fills the gaps. Null when neither has a valid submittedDate.
     */
    private static Submission buildSubmission(
            ReferenceData.ReferenceSubmission block, SubmitterDetails submitterDetails) {
        Date day = block == null || block.getSubmittedDate() == null ? null : parseDate(block.getSubmittedDate());
        if (day == null && submitterDetails != null && submitterDetails.getSubmittedDate() != null) {
            day = parseDate(submitterDetails.getSubmittedDate());
        }
        if (day == null) {
            return null;
        }
        Submission submission = referenceFactory.createSubmission();
        submission.setDay(day);
        String address = block == null || isBlank(block.getSubmitterAddress())
                ? (submitterDetails == null ? null : buildSubmitterAddress(submitterDetails.getSubmissionAccount()))
                : block.getSubmitterAddress();
        if (!isBlank(address)) {
            submission.setSubmitterAddress(address);
        }
        return submission;
    }

    private static Publication createArticle(ReferenceData refData) {
        ReferenceData.Article block = refData.getArticle();
        if (block == null) return missingBlock(refData);
        // without a year the writer drops "(year)" and the article reads back without its year
        if (isBlank(block.getJournal()) || !isValidYear(block.getYear())) {
            LOGGER.warn("Reference type '{}' needs journal and year; mapping as unpublished", refData.getType());
            return referenceFactory.createUnpublished();
        }
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
        // A book RL line needs editors, title, publisher and year (sequencetools RL.16/RL.19/RL.20);
        // without editors the writer emits a bare "(in)" and without a year it drops "(year)", and
        // either way EmblBookMatcher no longer matches (the line reads back as an article). Pages
        // may be absent: the writer renders them as 0-0.
        if (isBlank(block.getBookTitle()) || isBlank(block.getPublisher()) || !isValidYear(block.getYear())) {
            LOGGER.warn(
                    "Reference type '{}' needs bookTitle, publisher and year; mapping as unpublished",
                    refData.getType());
            return referenceFactory.createUnpublished();
        }
        Book book = referenceFactory.createBook();
        book.setBookTitle(block.getBookTitle());
        book.setPublisher(block.getPublisher());
        book.setFirstPage(block.getFirstPage());
        book.setLastPage(block.getLastPage());
        book.setYear(yearToDate(block.getYear()));
        addPersons(block.getEditors(), book::addEditor);
        if (book.getEditors().isEmpty()) {
            LOGGER.warn("Reference type '{}' has no named editor; mapping as unpublished", refData.getType());
            return referenceFactory.createUnpublished();
        }
        return book;
    }

    private static Publication createThesis(ReferenceData refData) {
        ReferenceData.Thesis block = refData.getThesis();
        if (block == null) return missingBlock(refData);
        // without a year the writer emits "Thesis ()", which reads back as a thesis without a year
        if (isBlank(block.getInstitute()) || !isValidYear(block.getYear())) {
            LOGGER.warn("Reference type '{}' needs institute and year; mapping as unpublished", refData.getType());
            return referenceFactory.createUnpublished();
        }
        Thesis thesis = referenceFactory.createThesis();
        thesis.setInstitute(block.getInstitute());
        thesis.setYear(yearToDate(block.getYear()));
        return thesis;
    }

    private static Publication createPatent(ReferenceData refData) {
        ReferenceData.Patent block = refData.getPatent();
        if (block == null) return missingBlock(refData);
        Date day = block.getDay() == null ? null : parseDate(block.getDay());
        String number =
                block.getPatentNumber() == null ? null : block.getPatentNumber().trim();
        String patentType =
                block.getPatentType() == null ? null : block.getPatentType().trim();
        // office, number and type must match EmblPatentMatcher's character classes
        if (block.getPatentOffice() == null
                || !block.getPatentOffice().trim().matches("[A-Za-z]{2}")
                || number == null
                || !number.matches("[A-Za-z0-9]+")
                || patentType == null
                || !patentType.matches("[A-Za-z0-9]+")
                || block.getSequenceNumber() == null
                || day == null) {
            LOGGER.warn(
                    "Reference type '{}' needs patentOffice (2 letters), patentNumber and patentType "
                            + "(letters and digits), sequenceNumber and a valid day; mapping as unpublished",
                    refData.getType());
            return referenceFactory.createUnpublished();
        }
        Patent patent = referenceFactory.createPatent();
        patent.setPatentOffice(block.getPatentOffice().trim().toUpperCase(Locale.ROOT));
        patent.setPatentNumber(number);
        patent.setPatentType(patentType);
        patent.setSequenceNumber(block.getSequenceNumber());
        patent.setDay(day);
        if (block.getApplicants() != null) {
            block.getApplicants().stream().filter(a -> !isBlank(a)).forEach(patent::addApplicant);
        }
        return patent;
    }

    private static Publication createElectronic(ReferenceData refData) {
        ReferenceData.Electronic block = refData.getElectronic();
        if (block == null) return missingBlock(refData);
        if (isBlank(block.getText())) {
            LOGGER.warn("Reference type '{}' has no text; mapping as unpublished", refData.getType());
            return referenceFactory.createUnpublished();
        }
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
                refData.getType().trim().toLowerCase(Locale.ROOT));
        return referenceFactory.createUnpublished();
    }

    /** A four-digit year; anything else is treated as missing. */
    private static boolean isValidYear(Integer year) {
        return year != null && year >= 1000 && year <= 9999;
    }

    private static Date yearToDate(int year) {
        return Date.from(
                LocalDate.of(year, 1, 1).atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    /**
     * Submitter address from the account, following the legacy rule (sequencetools
     * ReferenceUtils.getAddressFromSubmissionAccount): a broker account gives "broker, address,
     * country", any other "center, laboratory, address, country"; blank parts are skipped.
     */
    private static String buildSubmitterAddress(SubmissionAccount account) {
        if (account == null) {
            return null;
        }
        Stream<String> parts = isBlank(account.getBrokerName())
                ? Stream.of(
                        account.getCenterName(),
                        account.getLaboratoryName(),
                        account.getAddress(),
                        account.getCountry())
                : Stream.of(account.getBrokerName(), account.getAddress(), account.getCountry());
        // Join with a comma so the EMBL RL writer wraps on commas across the
        // whole address (rather than treating institution and address as two
        // separate blocks). The writer collapses any embedded newline to a
        // single space anyway, so a `\n` separator would yield the institution
        // and address running together with no separator.
        return parts.filter(Objects::nonNull)
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .reduce((left, right) -> left + ", " + right)
                .orElse(null);
    }

    /**
     * Reduce a name component (firstName or middleName) to compact EMBL initials,
     * each letter followed by a period, no separators between letters. Accepts:
     * full name ("Eleanor" → "E."), single initial with or without period
     * ("E." / "E" → "E."), and multiple initials separated by spaces and/or
     * periods ("E. P." / "E P" / "E.P." → "E.P."). Returns "" for null/blank.
     */
    static String toInitials(String component) {
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
     * ISO-8601 local datetimes / dates (assumed UTC), and EMBL "dd-MMM-yyyy". Null (with a
     * WARN) when the input is null or none of those.
     */
    static Date parseDate(String dateStr) {
        LocalDate utcDate = dateStr == null ? null : parseToUtcLocalDate(dateStr);
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
