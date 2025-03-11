package uk.ac.ebi.embl.converter.gff3;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class GFF3File implements IGFF3Feature {
    GFF3Headers headers;
    GFF3SourceMetadata metadata;
    Map<String, List<GFF3Feature>> geneMap;

    public GFF3File(GFF3Headers headers, GFF3SourceMetadata metadata, Map<String, List<GFF3Feature>> geneMap) {
        this.metadata = metadata;
        this.headers = headers;
        this.geneMap = geneMap;
    }

    @Override
    public void writeGFF3String(Writer writer) throws IOException {
        this.headers.writeGFF3String(writer);
        if(metadata != null) {
            this.metadata.writeGFF3String(writer);
        }
        for( String geneName : geneMap.keySet()){
            for (GFF3Feature feature : geneMap.get(geneName)) {
                writer.write(feature.getAccession());
                writer.write('\t' + feature.getSource());
                writer.write('\t' + feature.getName());
                writer.write("\t%d".formatted(feature.getStart()));
                writer.write("\t%d".formatted(feature.getEnd()));
                writer.write('\t' + feature.getScore());
                writer.write('\t' + feature.getStrand().toString());
                writer.write('\t' + feature.getPhase());
                writer.write('\t'+ feature.getAttributes().entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())  // Sort by key
                        .map(entry -> entry.getKey() + "=" + entry.getValue())  // Format k=v
                        .collect(Collectors.joining(";", "", ";")));
                writer.write("\n");
            }
        }
    }
}