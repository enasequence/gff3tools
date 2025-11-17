package uk.ac.ebi.embl.gff3tools.fasta;

import java.io.File;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Pattern;

public class FASTAFileReader {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // normalize curly quotes / NBSP etc.
    private static final Pattern CURLY_DOUBLE = Pattern.compile("[\u201C\u201D]");
    private static final Pattern CURLY_SINGLE = Pattern.compile("[\u2018\u2019]");
    private static final Pattern NBSP = Pattern.compile("\u00A0");

    public List<FASTAFile> readFile(File file) {
        List<FASTAFile> out = new ArrayList<>();

        try (BufferedReader br = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            String line;
            long lineNo = -1;

            FASTAFile current = null;
            int currentHeaderLine = -1; // for clarity
            int currentSeqStartLine = -1;

            while ((line = br.readLine()) != null) {
                lineNo++;

                if (line.isEmpty()) continue;

                if (line.charAt(0) == '>') {
                    // finalize previous record (sequence ends on the line before this header)
                    if (current != null && current.getSequenceAccessor() != null) {
                        current.getSequenceAccessor().setEndLine((int) (lineNo - 1));
                    }

                    // parse new header
                    int pipeIdx = line.indexOf('|');
                    if (pipeIdx < 0) {
                        // No JSON? We'll still capture an ID and create an empty header.
                        String id = extractAccession(line.substring(1));
                        FastaHeader header = new FastaHeader();
                        header.setDescription(null);
                        header.setMoleculeType(null);
                        header.setTopology(null);
                        header.setChromosomeType(Optional.empty());
                        header.setChromosomeLocation(Optional.empty());
                        header.setChromosomeName(Optional.empty());

                        current = new FASTAFile();
                        current.setId(id);
                        current.setHeader(header);
                        currentHeaderLine = (int) lineNo;
                        currentSeqStartLine = (int) lineNo + 1;
                        current.setSequenceAccessor(new SequenceAccessor(file, currentSeqStartLine, -1));
                        out.add(current);
                        continue;
                    }

                    String idPart = line.substring(1, pipeIdx);
                    String jsonPart = line.substring(pipeIdx + 1);

                    String id = extractAccession(idPart);
                    FastaHeader header = parseHeaderJson(jsonPart);

                    current = new FASTAFile();
                    current.setId(id);
                    current.setHeader(header);
                    currentHeaderLine = (int) lineNo;
                    currentSeqStartLine = currentHeaderLine + 1;

                    SequenceAccessor accessor = new SequenceAccessor(file, currentSeqStartLine, -1);
                    current.setSequenceAccessor(accessor);
                    out.add(current);
                    continue;
                }

                // Any non-header line before we've seen a header is “NONSENSE”; ignore.
                // Sequence lines are just skipped here; we only track their line numbers.
            }

            // finalize last record at EOF
            if (current != null && current.getSequenceAccessor() != null) {
                current.getSequenceAccessor().setEndLine((int) lineNo);
            }

        } catch (IOException e) {
            throw new RuntimeException("Failed to read FASTA file: " + file, e);
        }

        return out;
    }

    private static String extractAccession(String betweenGtAndPipe) {
        // Trim, then grab the FIRST token — “first accession number” as requested.
        String trimmed = betweenGtAndPipe.trim();
        int space = trimmed.indexOf(' ');
        return (space > 0) ? trimmed.substring(0, space) : trimmed;
    }

    private static FastaHeader parseHeaderJson(String rawJson) {
        String normalized = normalizeJson(rawJson);

        String description = null;
        String moleculeType = null;
        String topologyStr = null;
        String chrType = null, chrLoc = null, chrName = null;

        try {
            JsonNode node = MAPPER.readTree(normalized);

            // make a normalized key->value map (trim keys, lower-case, collapse [_ - space])
            Map<String, String> norm = new HashMap<>();
            Iterator<Map.Entry<String, JsonNode>> it = node.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                String key = e.getKey() == null ? "" : e.getKey();
                key = key.trim().toLowerCase(Locale.ROOT).replaceAll("[\\s_-]+", "");
                String val = e.getValue().isNull() ? null : e.getValue().asText();
                norm.put(key, val);
            }

            description   = norm.get("description");
            moleculeType  = firstNonNull(norm.get("moleculetype"), norm.get("moleculetype"), norm.get("moleculetype")); // defensive, yes it's the same key after normalization
            if (moleculeType == null) moleculeType = norm.get("moleculetype"); // for safety

            topologyStr   = norm.get("topology");
            chrType       = firstNonNull(norm.get("chromosometype"), norm.get("chromosometyp"));
            chrLoc        = norm.get("chromosomelocation");
            chrName       = norm.get("chromosomename");

        } catch (Exception ignore) {
            // If the JSON is totally mangled, we leave fields null; better than exploding.
        }

        FastaHeader header = new FastaHeader();
        header.setDescription(description);
        header.setMoleculeType(moleculeType);
        header.setTopology(parseTopology(topologyStr));

        header.setChromosomeType(Optional.ofNullable(emptyToNull(chrType)));
        header.setChromosomeLocation(Optional.ofNullable(emptyToNull(chrLoc)));
        header.setChromosomeName(Optional.ofNullable(emptyToNull(chrName)));

        return header;
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isEmpty()) ? null : s;
    }

    private static String firstNonNull(String... vals) {
        for (String v : vals) if (v != null) return v;
        return null;
    }

    private static Topology parseTopology(String s) {
        if (s == null) return null;
        String t = s.trim().toUpperCase(Locale.ROOT);
        switch (t) {
            case "LINEAR": return Topology.LINEAR;
            case "CIRCULAR": return Topology.CIRCULAR;
            default: return null;
        }
    }

    private static String normalizeJson(String s) {
        if (s == null) return null;
        String out = s.trim();

        // replace curly quotes with straight quotes
        out = CURLY_DOUBLE.matcher(out).replaceAll("\"");
        out = CURLY_SINGLE.matcher(out).replaceAll("'");

        // remove NBSP
        out = NBSP.matcher(out).replaceAll(" ");

        // A tiny mercy: if header accidentally missed the opening brace, try to salvage
        if (!out.startsWith("{") && out.contains("{")) {
            out = out.substring(out.indexOf('{'));
        }

        return out;
    }
}
