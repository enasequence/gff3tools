package uk.ac.ebi.embl.converter.gff3;

import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record GFF3File(
        GFF3Headers headers,
        GFF3SourceMetadata metadata,
        Map<String, List<GFF3Feature>> geneMap
) implements IGFF3Feature {
    @Override
    public void writeGFF3String(Writer writer) throws IOException {
        this.headers.writeGFF3String(writer);
        if (metadata != null) {
            this.metadata.writeGFF3String(writer);
        }
        writer.write('\n');
        for (String geneName : geneMap.keySet()) {
            for (GFF3Feature feature : geneMap.get(geneName)) {
                writer.write(feature.accession());
                writer.write('\t' + feature.source());
                writer.write('\t' + feature.name());
                writer.write("\t%d".formatted(feature.start()));
                writer.write("\t%d".formatted(feature.end()));
                writer.write('\t' + feature.score());
                writer.write('\t' + feature.strand().toString());
                writer.write('\t' + feature.phase());
                writer.write('\t' + feature.attributes().entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())  // Sort by key
                        .map(entry -> entry.getKey() + "=" + entry.getValue())  // Format k=v
                        .collect(Collectors.joining(";", "", ";")));
                writer.write("\n");
            }
        }
    }
}