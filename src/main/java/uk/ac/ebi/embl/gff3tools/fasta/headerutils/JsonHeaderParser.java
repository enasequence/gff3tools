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
import uk.ac.ebi.embl.gff3tools.fasta.Topology;

public class JsonHeaderParser {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public ParsedHeader parse(String headerLine) throws IOException {
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

    private static void fillFromJson(String raw, FastaHeader h) throws IOException {
        if (raw == null || raw.isEmpty()) return;

        // Normalize curly quotes / NBSPs but keep the final JSON we actually tried to parse
        String normalized = raw.replace('\u201C', '"')
                .replace('\u201D', '"')
                .replace('\u2018', '\'')
                .replace('\u2019', '\'')
                .replace('\u00A0', ' ')
                .trim();
        try {
            JsonNode node = MAPPER.readTree(normalized);
            Map<String, String> m = new HashMap<>();
            node.fields().forEachRemaining(e -> {
                String k = e.getKey() == null ? "" : e.getKey();
                k = k.trim().toLowerCase(Locale.ROOT).replaceAll("[\\s_-]+", "");
                String v = e.getValue().isNull() ? null : e.getValue().asText();
                m.put(k, v);
            });
            h.setDescription(m.get("description"));
            h.setMoleculeType(m.get("moleculetype"));
            h.setTopology(parseTopology(m.get("topology")));
            if (m.containsKey("chromosometype"))
                h.setChromosomeType(Optional.ofNullable(emptyToNull(m.get("chromosometype"))));
            if (m.containsKey("chromosomelocation"))
                h.setChromosomeLocation(Optional.ofNullable(emptyToNull(m.get("chromosomelocation"))));
            if (m.containsKey("chromosomename"))
                h.setChromosomeName(Optional.ofNullable(emptyToNull(m.get("chromosomename"))));
        } catch (IOException e) {
            // explode, and include the JSON we tried to parse
            throw new IOException("Malformed FASTA header JSON: " + normalized, e);
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
