package uk.ac.ebi.embl.gff3tools.exception;

public class FastaAccessionAssignmentException extends Exception {

    public FastaAccessionAssignmentException() {}

    public FastaAccessionAssignmentException(String message) {
        super(message);
    }

    public FastaAccessionAssignmentException(Throwable cause) {
        super(cause);
    }

    public FastaAccessionAssignmentException(String message, Throwable cause) {
        super(message, cause);
    }
}

