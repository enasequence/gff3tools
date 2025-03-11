package uk.ac.ebi.embl.converter.gff3;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;

public class GFF3File implements IGFF3Feature {
    ArrayList<IGFF3Feature> features;

    public GFF3File() {
        features = new ArrayList<>();
    }

    public void addFeature(IGFF3Feature feature) {
        features.add(feature);
    }

    @Override
    public void writeGFF3String(Writer writer) throws IOException {
        for (IGFF3Feature feature : features) {
            feature.writeGFF3String(writer);
            writer.write('\n');
        }
    }
}