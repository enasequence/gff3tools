
package uk.ac.ebi.embl.converter.gff3;

import java.io.IOException;
import java.io.Writer;
import java.util.LinkedHashMap;
import java.util.Map;

public record GFF3SourceMetadata(String species) implements IGFF3Feature{

    @Override
    public void writeGFF3String(Writer writer) throws IOException {
        if (species != null) {
            writer.write("##species %s\n".formatted(species));
        }
    }
}