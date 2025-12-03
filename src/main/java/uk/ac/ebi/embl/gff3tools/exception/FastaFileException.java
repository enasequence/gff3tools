package uk.ac.ebi.embl.gff3tools.exception;

public class FastaFileException extends Exception {

    public FastaFileException() {}
    public FastaFileException(String message) { super(message); }
    public FastaFileException(Throwable cause) { super(cause); }
    public FastaFileException(String message, Throwable cause) { super(message, cause); }
}

