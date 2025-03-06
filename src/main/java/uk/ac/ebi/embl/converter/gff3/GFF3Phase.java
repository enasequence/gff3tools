package uk.ac.ebi.embl.converter.gff3;

import java.io.IOException;
import java.io.Writer;

public enum GFF3Phase implements IGFF3Feature {
    First,
    Second,
    Third;

    @Override
    public void writeGFF3String(Writer writer) throws IOException {
        String value = switch (this) {
            case First -> "\t%d".formatted(0);
            case Second -> "\t%d".formatted(1);
            case Third -> "\t%d".formatted(2);
        };
        writer.write(value);
    }
}
