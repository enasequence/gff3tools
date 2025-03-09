package uk.ac.ebi.embl.converter.gff3;

import lombok.*;

import java.util.Map;

@Getter
@Setter
@RequiredArgsConstructor
public class GFF3Feature {
    private final String accession;
    private final String source;
    private final String name;
    private final long  start;
    private final long end;
    private final String score;
    private final String strand;
    private final String phase;
    private final Map<String, String> attributes;

}
