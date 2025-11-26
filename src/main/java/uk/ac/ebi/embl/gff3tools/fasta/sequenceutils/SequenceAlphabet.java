package uk.ac.ebi.embl.gff3tools.fasta.sequenceutils;

public final class SequenceAlphabet {
    private final boolean[] allowed = new boolean[128];
    public SequenceAlphabet(String chars) {
        for (char c: chars.toCharArray()) if (c<128) allowed[c]=true;
        allowed['>']=false;
    }
    public boolean isAllowed(byte b){
        int i=b&0xFF;
        return i<128 && allowed[i];
    }
    public static SequenceAlphabet defaultNucleotideAlphabet() {
        return new SequenceAlphabet("ACGTURYSWKMBDHVNacgturyswkmbdhvn-.*");
    }
}
