package uk.ac.ebi.embl.converter.gff3;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

public class GFF3Feature implements IGFF3Feature {

    List<List<String>> rows;

    public GFF3Feature(List<List<String>> rows) {
        this.rows = rows;
    }

    @Override
    public void writeGFF3String(Writer writer) throws IOException {
        for( List<String> row : rows){
            writer.write(String.join("\t", row));
            writer.write("\n");
        };
    }
}
