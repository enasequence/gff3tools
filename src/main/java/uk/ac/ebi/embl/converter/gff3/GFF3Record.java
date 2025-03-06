package uk.ac.ebi.embl.converter.gff3;

import io.vavr.Tuple2;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class GFF3Record implements IGFF3Feature{
    public String seqid;
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public Optional<String> source;
    public String type;
    public long start;
    public long end;
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public Optional<Double> score;
    public GFF3Strand strand;
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public Optional<GFF3Phase> phase;
    public LinkedHashMap<String, List<String>> attributes;

    public GFF3Record(String seqid,
                      @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
                      Optional<String> source,
                      String type,
                      long start,
                      long end,
                      @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
                      Optional<Double> score,
                      GFF3Strand strand,
                      @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
                      Optional<GFF3Phase> phase
    ) {
        this.seqid = seqid;
        this.source = source;
        this.type = type;
        this.start = start;
        this.end = end;
        this.score = score;
        this.strand = strand;
        this.phase = phase;
        this.attributes = new LinkedHashMap<>();
    }

    public void addAttribute(String attribute, String value) {
        if (!attributes.containsKey(attribute)) {
            attributes.put(attribute, new ArrayList<>());
        }
        attributes.get(attribute).add(value);
    }

    public List<String> getAttributeValues(String attribute) {
        if (!attributes.containsKey(attribute)) {
            return new ArrayList<>();
        }
        return attributes.get(attribute);
    }

    public void removeAttribute(String attribute) {
        attributes.remove(attribute);
    }

    @Override
    public void writeGFF3String(Writer writer) throws IOException {
        writer.write(seqid);
        writer.write('\t' + source.orElse("."));
        writer.write('\t' + type);
        writer.write("\t%d".formatted(start));
        writer.write("\t%d".formatted(end));
        writer.write('\t' + score.map(String::valueOf).orElse("."));
        writer.write('\t');
        strand.writeGFF3String(writer);
        writer.write('\t');
        if (phase.isPresent()) {
            phase.get().writeGFF3String(writer);
        }
        else {
            writer.write(".");
        }
        String attribute_string = attributes.entrySet().stream()
                .sorted()
                .map(e -> e.getKey() + "=" + e.getValue().stream().sorted().collect(Collectors.joining(",")))
                .collect(Collectors.joining(";"));
        writer.write('\t' + attribute_string);
    }
}
