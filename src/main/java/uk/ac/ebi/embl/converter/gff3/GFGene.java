package uk.ac.ebi.embl.converter.gff3;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GFGene {
    private String geneName;
    private long start;
    private long end;
}
