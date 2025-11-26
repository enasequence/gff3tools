package uk.ac.ebi.embl.gff3tools.exception;

public class FastaReadException extends Exception {

    public FastaReadException() {}
    public FastaReadException(String message) { super(message); }
    public FastaReadException(Throwable cause) { super(cause); }
    public FastaReadException(String message, Throwable cause) { super(message, cause); }
}

