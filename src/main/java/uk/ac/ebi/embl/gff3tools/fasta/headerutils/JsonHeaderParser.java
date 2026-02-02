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
import uk.ac.ebi.embl.gff3tools.exception.FastaHeaderParserException;

public class JsonHeaderParser {
    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
            .build();
    private static final int MAX_HEADER_LENGTH = 4096;

    public ParsedHeader parse(String headerLine) throws FastaHeaderParserException {

        validateHeaderLine(headerLine);

        String rest = headerLine.substring(1);
        int pipe = rest.indexOf('|');

        // parse id
        String id = (pipe >= 0 ? rest.substring(0, pipe) : rest).trim();
        if (Objects.equals(id, "")) {
            throw new FastaHeaderParserException("FASTA header should contain the id, but no id was provided.");
        }

        // parse header
        FastaHeader header = new FastaHeader();
        if (pipe >= 0) {
            header = parseHeaderJson(rest.substring(pipe + 1).trim());
        }

        return new ParsedHeader(id, header);
    }

    private static FastaHeader parseHeaderJson(String raw) throws FastaHeaderParserException {
        if (raw == null || raw.isBlank()) {
            throw new FastaHeaderParserException("FASTA header contains a '|', but no JSON object was provided.");
        }

        String normalised = normaliseRawJsonString(raw);

        try {
            FastaHeader header = MAPPER.readValue(normalised, FastaHeader.class);
            return header;

        } catch (JsonProcessingException e) {
            throw new FastaHeaderParserException(
                    "Malformed FASTA header JSON: " + normalised + " due to " + e.getMessage(), e);
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

    private static void validateHeaderLine(String headerLine) throws FastaHeaderParserException {
        if (!headerLine.startsWith(">")) {
            throw new FastaHeaderParserException(
                    "FASTA header contains no '>', which it should be at the start of the header line. FASTA header is "
                            + headerLine);
        }

        if (headerLine.length() > MAX_HEADER_LENGTH) {
            throw new FastaHeaderParserException(
                    "FASTA header should contain a maximum of 4096 characters according to the specification. FASTA header is "
                            + headerLine);
        }

        if (!headerLine.contains("|")) {
            throw new FastaHeaderParserException(
                    "FASTA header contains no '|', which it should to separate the id and the json. FASTA header is "
                            + headerLine);
        }
    }
}
