package uk.ac.ebi.embl.gff3tools.fix;

import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;

public interface AnnotationFix {
    public GFF3Annotation fix(GFF3Annotation feature);
}