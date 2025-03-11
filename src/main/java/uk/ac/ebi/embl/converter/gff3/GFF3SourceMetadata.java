
package uk.ac.ebi.embl.converter.gff3;

import java.io.IOException;
import java.io.Writer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class GFF3SourceMetadata implements IGFF3Feature{
    Map<String, String> attributes;

    public GFF3SourceMetadata() {
        this.attributes = new LinkedHashMap<>();
    }

    public void addAttribute(String key, String value) {
        this.attributes.put(key, value);
    }

    @Override
    public void writeGFF3String(Writer writer) throws IOException {
        if (attributes.containsKey("organism")) {
            writer.write("##species %s\n".formatted(attributes.get("organism")));
        }
        writer.write('\n');
    }
}