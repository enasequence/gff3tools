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
package uk.ac.ebi.embl.gff3tools.fasta.headerutils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.util.*;
import uk.ac.ebi.embl.gff3tools.exception.FastaFileException;

public class JsonHeaderParser {
    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
            .build();
    ;

    public ParsedHeader parse(String headerLine) throws FastaFileException {

        //char limit according to the spec
        if (headerLine.length() > 4096) {
            throw new FastaFileException("FASTA header should contain a maximum of 4096 characters according to the specification.");
        }

        String rest = headerLine.substring(1);
        int pipe = rest.indexOf('|');

        // parse id
        String idPart = (pipe >= 0 ? rest.substring(0, pipe) : rest).trim();
        String id = idPart.isEmpty() ? "" : idPart.split("\\s+")[0];
        if (Objects.equals(id, "")) {
            throw new FastaFileException("FASTA header should contain the id, but no id was provided.");
        }

        // parse header
        FastaHeader header = new FastaHeader();
        if (pipe >= 0) {
            header = parseHeaderJson(rest.substring(pipe + 1).trim());
        }

        return new ParsedHeader(id, header);
    }

    private static FastaHeader parseHeaderJson(String raw) throws FastaFileException {
        if (raw == null || raw.isBlank()) {
            throw new FastaFileException("FASTA header contains a '|', but no JSON object was provided.");
        }

        String normalised = normaliseRawJsonString(raw);

        try {
            FastaHeader header = MAPPER.readValue(normalised, FastaHeader.class);

            List<String> missing = new ArrayList<>();
            if (header.getDescription() == null) missing.add("description");
            if (header.getMoleculeType() == null) missing.add("molecule_type");
            if (header.getTopology() == null) missing.add("topology");

            if (!missing.isEmpty()) {
                throw new FastaFileException("FASTA header JSON is missing required fields: " + missing);
            }

            return header;

        } catch (JsonProcessingException e) {
            throw new FastaFileException("Malformed FASTA header JSON: " + normalised, e);
        }
    }

    private static String normaliseRawJsonString(String raw) {
        return raw.replace('\u201C', '"')
                .replace('\u201D', '"')
                .replace('\u2018', '\'')
                .replace('\u2019', '\'')
                .replace('\u00A0', ' ')
                .trim();
    }
}
