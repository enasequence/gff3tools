package uk.ac.ebi.embl.gff3tools.fix;

import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;

public interface FeatureFix {
     public GFF3Feature fix(GFF3Feature feature);
}
