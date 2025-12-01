package uk.ac.ebi.embl.gff3tools.fasta.sequenceutils;

public final class SequenceAlphabet {
    private final boolean[] allowed = new boolean[128];
    public SequenceAlphabet(String chars) {
        for (char c: chars.toCharArray()) if (c<128) allowed[c]=true;
        allowed['>']=false;
    }

    /** Fast ASCII check for is it an allowed char. */
    public boolean isAllowed(byte b){
        int i=b&0xFF;
        return i<128 && allowed[i];
    }

    /** Fast ASCII check for 'N' or 'n' without decoding. */
    public boolean isNBase(byte b) {
        return ((b | 0x20) == 'n');
    }


    public static SequenceAlphabet defaultNucleotideAlphabet() {
        return new SequenceAlphabet("ACGTURYSWKMBDHVNacgturyswkmbdhvn-.*");
    }
}
