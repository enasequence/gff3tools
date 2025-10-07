package uk.ac.ebi.embl.gff3tools.utils;

public enum OntologyTerm {
    FEATURE("SO:0000110");

    public final String ID;

    OntologyTerm(String id) {
        this.ID = id;
    }
}
