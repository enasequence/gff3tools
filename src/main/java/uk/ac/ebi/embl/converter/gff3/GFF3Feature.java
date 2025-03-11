package uk.ac.ebi.embl.converter.gff3;

import java.util.Map;

public record GFF3Feature(
        String accession,
        String source,
        String name,
        long start,
        long end,
        String score,
        String strand,
        String phase,
        Map<String, String> attributes
) {}
