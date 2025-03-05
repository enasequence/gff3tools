
package uk.ac.ebi.embl.converter.gff3;

import java.io.IOException;
import java.io.Writer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class GFF3SourceAttributesRecord implements IGFF3Feature{
    Map<String, String> attributes;

    public GFF3SourceAttributesRecord() {
        this.attributes = new LinkedHashMap<>();
    }

    public void addAttribute(String key, String value) {
        this.attributes.put(key, value);
    }

    @Override
    public void writeGFF3String(Writer writer) throws IOException {
        String attrs = attributes.entrySet().stream()
                .map(att -> "%s=%s".formatted(att.getKey(), att.getValue()))
                .sorted()
                .collect(Collectors.joining(";"));
        writer.write("# BACKPORT\tsource\t%s".formatted(attrs));
    }
}