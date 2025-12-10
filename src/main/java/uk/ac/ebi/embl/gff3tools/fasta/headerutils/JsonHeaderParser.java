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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.*;
import uk.ac.ebi.embl.gff3tools.exception.FastaFileException;
import uk.ac.ebi.embl.gff3tools.fasta.Topology;

public class JsonHeaderParser {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public ParsedHeader parse(String headerLine) throws FastaFileException {
        String rest = headerLine.substring(1); // headerLine starts with '>'
        int pipe = rest.indexOf('|');
        String idPart = (pipe >= 0 ? rest.substring(0, pipe) : rest).trim();
        String id = idPart.isEmpty() ? "" : idPart.split("\\s+")[0];

        FastaHeader h = new FastaHeader();
        h.setChromosomeType(Optional.empty());
        h.setChromosomeLocation(Optional.empty());
        h.setChromosomeName(Optional.empty());

        if (pipe >= 0) {
            fillFromJson(rest.substring(pipe + 1).trim(), h); // may throw IOException
        }
        return new ParsedHeader(id, h);
    }

    private static void fillFromJson(String raw, FastaHeader h) throws FastaFileException {
        if (raw == null || raw.isEmpty()) {
            throw new FastaFileException("FASTA header contains a '|', but no JSON object was provided. "
                    + "Expected something like: >id { \"description\": \"...\", \"moleculeType\": \"DNA\", ... }");
        }

        // Normalize curly quotes / NBSPs
        String normalized = raw.replace('\u201C', '"')
                .replace('\u201D', '"')
                .replace('\u2018', '\'')
                .replace('\u2019', '\'')
                .replace('\u00A0', ' ')
                .trim();

        JsonNode node;
        try {
            node = MAPPER.readTree(normalized);
            if (node == null || !node.isObject()) {
                throw new FastaFileException(
                        "FASTA header JSON did not parse into an object. " + "Received: " + normalized);
            }
        } catch (IOException e) {
            throw new FastaFileException("Malformed FASTA header JSON. Failed to parse: " + normalized, e);
        }

        // Extract fields
        Map<String, String> m = new HashMap<>();
        node.fields().forEachRemaining(e -> {
            String key = (e.getKey() == null ? "" : e.getKey())
                    .trim()
                    .toLowerCase(Locale.ROOT)
                    .replaceAll("[\\s_-]+", "");
            String val = e.getValue().isNull() ? null : e.getValue().asText();
            m.put(key, val);
        });

        // Assign values
        h.setDescription(m.get("description"));
        h.setMoleculeType(m.get("moleculetype"));
        h.setTopology(parseTopology(m.get("topology")));

        if (m.containsKey("chromosometype"))
            h.setChromosomeType(Optional.ofNullable(emptyToNull(m.get("chromosometype"))));
        if (m.containsKey("chromosomelocation"))
            h.setChromosomeLocation(Optional.ofNullable(emptyToNull(m.get("chromosomelocation"))));
        if (m.containsKey("chromosomename"))
            h.setChromosomeName(Optional.ofNullable(emptyToNull(m.get("chromosomename"))));

        // 🔍 Validate required fields
        List<String> missing = new ArrayList<>();

        if (h.description == null) missing.add("description");

        if (h.moleculeType == null) missing.add("moleculeType");

        if (h.topology == null) missing.add("topology (must be 'LINEAR' or 'CIRCULAR')");

        if (!missing.isEmpty()) {
            throw new FastaFileException(
                    "FASTA header JSON is missing required fields: " + missing + ". Parsed JSON was: " + normalized);
        }
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isEmpty()) ? null : s;
    }

    private static Topology parseTopology(String s) {
        if (s == null) return null;
        switch (s.trim().toUpperCase(Locale.ROOT)) {
            case "LINEAR":
                return Topology.LINEAR;
            case "CIRCULAR":
                return Topology.CIRCULAR;
            default:
                return null;
        }
    }
}
