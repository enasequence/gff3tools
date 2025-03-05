package uk.ac.ebi.embl.converter.gff3;

import java.io.IOException;
import java.io.Writer;

public interface IGFF3Feature {
    void writeGFF3String(Writer writer) throws IOException;
}
