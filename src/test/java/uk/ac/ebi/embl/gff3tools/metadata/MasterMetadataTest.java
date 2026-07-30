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

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

class MasterMetadataTest {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
            .build();

    @Test
    void deserializesFromMasterEntryJson() throws Exception {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("metadata/master_entry.json")) {
            assertNotNull(is, "Test resource metadata/master_entry.json not found");
            MasterMetadata meta = MAPPER.readValue(is, MasterMetadata.class);

            assertEquals("GCA_000001405.29", meta.getId());
            assertEquals("CAXMMS010000001", meta.getAccession());
            assertEquals(1, meta.getSecondaryAccessions().size());
            assertEquals("CAXMMS000000000", meta.getSecondaryAccessions().get(0));
            assertEquals("Homo sapiens genome assembly", meta.getDescription());
            assertEquals("Human Genome Assembly", meta.getTitle());
            assertEquals(1, meta.getVersion());
            assertEquals("genomic DNA", meta.getMoleculeType());
            assertEquals("linear", meta.getTopology());
            assertEquals("HUM", meta.getDivision());
            assertEquals("STD", meta.getDataClass());
            assertEquals("PRJEB12345", meta.getProject());
            assertEquals("SAMEA12345", meta.getSample());
            assertEquals("9606", meta.getTaxon());
            assertEquals("Homo sapiens", meta.getScientificName());
            assertEquals("human", meta.getCommonName());
            assertNotNull(meta.getLineage());
            assertEquals(2, meta.getKeywords().size());
            assertEquals("WGS", meta.getKeywords().get(0));
            assertEquals("This is a test assembly", meta.getComment());
            assertEquals("chr1", meta.getChromosomeName());
            assertEquals("Chromosome", meta.getChromosomeType());
            assertEquals("Nucleus", meta.getChromosomeLocation());
            assertNotNull(meta.getFirstPublic());
            assertNotNull(meta.getLastUpdated());
            assertEquals("chromosome", meta.getAssemblyLevel());
            assertEquals("primary metagenome", meta.getAssemblyType());

            // Publications (DR lines)
            assertNotNull(meta.getPublications());
            assertEquals(1, meta.getPublications().size());
            assertEquals("SAMEA12345", meta.getPublications().get(0).getId());
            assertEquals("BioSample", meta.getPublications().get(0).getSource());

            // References (RN/RG/RA/RT/RL lines) — uses "number" and "group" aliases
            assertNotNull(meta.getReferences());
            assertEquals(1, meta.getReferences().size());
            assertEquals(1, meta.getReferences().get(0).getReferenceNumber());
            assertEquals("Human Genome Consortium", meta.getReferences().get(0).getConsortium());
            assertEquals("The human genome", meta.getReferences().get(0).getTitle());
            var submitterDetails = meta.getReferences().get(0).getSubmitterDetails();
            assertNotNull(submitterDetails);
            assertEquals("2024-01-15T00:00:00Z", submitterDetails.getSubmittedDate());
            assertEquals("EBI", submitterDetails.getSubmissionAccount().getCenterName());
            var refAuthors = submitterDetails.getAuthors();
            assertNotNull(refAuthors);
            assertEquals(2, refAuthors.size());
            assertEquals("Smith", refAuthors.get(0).getSurname());
            assertEquals("John", refAuthors.get(0).getFirstName());
            assertEquals("Doe", refAuthors.get(1).getSurname());
            assertEquals("Alice", refAuthors.get(1).getFirstName());
        }
    }

    @Test
    void ignoresUnknownProperties() throws Exception {
        String json = "{\"id\":\"test\",\"unknownField\":\"value\",\"platform\":\"Illumina\"}";
        MasterMetadata meta = MAPPER.readValue(json, MasterMetadata.class);
        assertEquals("test", meta.getId());
        // Should not throw
    }

    @Test
    void caseInsensitivePropertyMatching() throws Exception {
        String json = "{\"ID\":\"test\",\"DESCRIPTION\":\"desc\",\"MoleculeType\":\"DNA\"}";
        MasterMetadata meta = MAPPER.readValue(json, MasterMetadata.class);
        assertEquals("test", meta.getId());
        assertEquals("desc", meta.getDescription());
        assertEquals("DNA", meta.getMoleculeType());
    }

    @Test
    void deserializesNestedSubmitterDetailsReferenceSchema() throws Exception {
        String json =
                """
                {
                  "id": "test",
                  "references": [
                    {
                      "referenceNumber": 1,
                      "submitterDetails": {
                        "submittedDate": "2025-04-03T13:30:22.000+00:00",
                        "submissionAccount": {
                          "centerName": "BIOLOGY CENTRE CAS, INSTITUTE OF HYDROBIOLOGY",
                          "laboratoryName": "Laboratory of Microbial Ecology and Evolution & Laboratory of Microbial Cultivation and Ecogenomics",
                          "address": "Na Sadkach 7, Ceske Budejovice",
                          "country": "Czech Republic"
                        },
                        "authors": [
                          {"firstName": "Clafy", "surname": "Fernandes"},
                          {"firstName": "Michaela", "middleName": "M.", "surname": "Salcher"}
                        ]
                      }
                    }
                  ]
                }
                """;
        MasterMetadata meta = MAPPER.readValue(json, MasterMetadata.class);
        assertNotNull(meta.getReferences());
        assertEquals(1, meta.getReferences().size());
        var reference = meta.getReferences().get(0);
        assertNotNull(reference.getSubmitterDetails());
        assertEquals(
                "2025-04-03T13:30:22.000+00:00", reference.getSubmitterDetails().getSubmittedDate());
        assertEquals(
                "BIOLOGY CENTRE CAS, INSTITUTE OF HYDROBIOLOGY",
                reference.getSubmitterDetails().getSubmissionAccount().getCenterName());
        assertEquals(2, reference.getSubmitterDetails().getAuthors().size());
        assertEquals(
                "Fernandes", reference.getSubmitterDetails().getAuthors().get(0).getSurname());
        assertEquals("M.", reference.getSubmitterDetails().getAuthors().get(1).getMiddleName());
    }

    @Test
    void nullFieldsRemainNull() throws Exception {
        String json = "{\"id\":\"minimal\"}";
        MasterMetadata meta = MAPPER.readValue(json, MasterMetadata.class);
        assertEquals("minimal", meta.getId());
        assertNull(meta.getDescription());
        assertNull(meta.getMoleculeType());
        assertNull(meta.getTopology());
        assertNull(meta.getKeywords());
        assertNull(meta.getPublications());
        assertNull(meta.getReferences());
    }
}
