package uk.ac.ebi.embl.converter.gff3;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Optional;

public class GFF3Gene implements IGFF3Feature {
    public String ID;
    public GFF3Record record;
    public ArrayList<IGFF3Feature> children;

    public GFF3Gene(String id, GFF3Record record) {
        this.ID = id;
        record.addAttribute("ID", id);
        this.record = record;
        this.children = new ArrayList<>();
    }

    public void addChild(IGFF3Feature child) {
        this.children.add(child);
    }

    public void writeGFF3String(Writer writer) throws IOException {
        record.writeGFF3String(writer);
        for (IGFF3Feature child : children) {
            writer.write('\n');
            child.writeGFF3String(writer);
        }
    }
}
