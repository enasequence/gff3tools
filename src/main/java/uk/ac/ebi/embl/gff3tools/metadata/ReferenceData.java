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

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.io.IOException;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Represents a literature reference from MasterEntry JSON.
 * Maps to RN/RG/RA/RT/RL/RX lines in EMBL output.
 *
 * <p>v2 records carry a {@code type} discriminator and one type-specific block named
 * {@code lower(type)} (see docs/2610141424_reference_data_v2.md). v1 records have no
 * {@code type} and keep their submission data in {@link #submitterDetails}.
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReferenceData {

    @JsonAlias("number")
    private Integer referenceNumber;

    private String referencePosition;
    private String referenceComment;
    private String title;

    @JsonAlias("group")
    private String consortium;

    /** v1 submission data; superseded by {@code type=SUBMISSION} + {@link #submission}. */
    private SubmitterDetails submitterDetails;

    /** v2 discriminator: SUBMISSION, UNPUBLISHED, ARTICLE, BOOK, THESIS, PATENT or ELECTRONIC. */
    private String type;

    /**
     * v2 structured authors (RA); preferred over {@code submitterDetails.authors}. v1 producers
     * emitted {@code authors} as a free-text string; that is tolerated and ignored (empty list).
     */
    @JsonDeserialize(using = AuthorsDeserializer.class)
    private List<AuthorData> authors;

    /** v2 cross-references (RX), e.g. DOI / PUBMED / AGRICOLA. */
    private List<Xref> xrefs;

    private Submission submission;
    private Article article;
    private Book book;
    private Thesis thesis;
    private Patent patent;
    private Electronic electronic;
    private Unpublished unpublished;

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Xref {
        private String source;
        private String primaryId;
        private String secondaryId;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Submission {
        private String submittedDate;
        private String submitterAddress;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Article {
        private String journal;
        private String volume;
        private String issue;
        private String firstPage;
        private String lastPage;
        private Integer year;

        /** True for in-press articles (ERA PUBTYPE 7); carried for lossless round-trips, not rendered. */
        private boolean accepted;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Book {
        private String bookTitle;
        private String publisher;
        private String firstPage;
        private String lastPage;
        private Integer year;
        private List<AuthorData> editors;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Thesis {
        private String institute;
        private Integer year;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Patent {
        private String patentOffice;
        private String patentNumber;
        private String patentType;
        private Integer sequenceNumber;
        private String day;
        private List<String> applicants;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Electronic {
        private String text;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Unpublished {
        private String journalBlock;
    }

    static class AuthorsDeserializer extends JsonDeserializer<List<AuthorData>> {
        @Override
        public List<AuthorData> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonNode node = p.readValueAsTree();
            if (node == null || !node.isArray()) {
                return List.of();
            }
            return p.getCodec().readValue(node.traverse(p.getCodec()), new TypeReference<List<AuthorData>>() {});
        }
    }
}
