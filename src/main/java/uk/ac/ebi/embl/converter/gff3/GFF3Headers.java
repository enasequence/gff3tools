package uk.ac.ebi.embl.converter.gff3;

import java.io.IOException;
import java.io.Writer;

public class GFF3Headers implements IGFF3Feature {
    public String version;
    public String accession;
    public long start;
    public long end;

    public GFF3Headers(String version, String accession, long start, long end) {
        this.version = version;
        this.accession = accession;
        this.start = start;
        this.end = end;
    }

    @Override
    public void writeGFF3String(Writer writer) throws IOException {
        writer.write("##gff-version %s\n##sequence-region %s %d %d\n"
                .formatted(version, accession, start, end));
    }
}
