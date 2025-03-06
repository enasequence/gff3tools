package uk.ac.ebi.embl.converter.gff3;

import java.io.IOException;
import java.io.Writer;

public enum GFF3Strand implements IGFF3Feature {
    Positive,
    Negative;

    @Override
    public void writeGFF3String(Writer writer) throws IOException {
        String value = switch (this) {
            case Positive -> "+";
            case Negative -> "-";
        };
        writer.write(value);
    }
}
