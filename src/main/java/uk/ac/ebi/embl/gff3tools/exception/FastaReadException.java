package uk.ac.ebi.embl.gff3tools.exception;

public class FastaReadException extends Exception {

    private final String fastaFilePath;
    private final String line; // can be null

    /**
     * Creates a new FastaReadingException with a message, file path, line content and cause.
     *
     * @param message       description of the error
     * @param fastaFilePath path to the FASTA file being read
     * @param line          the line that caused the error (can be null)
     * @param cause         the underlying cause (can be null)
     */
    public FastaReadException(String message,
                                 String fastaFilePath,
                                 String line,
                                 Throwable cause) {
        super(message, cause);
        this.fastaFilePath = fastaFilePath;
        this.line = line;
    }

    /**
     * Creates a new FastaReadingException with a message, file path and line content.
     *
     * @param message       description of the error
     * @param fastaFilePath path to the FASTA file being read
     * @param line          the line that caused the error (can be null)
     */
    public FastaReadException(String message,
                                 String fastaFilePath,
                                 String line) {
        super(message);
        this.fastaFilePath = fastaFilePath;
        this.line = line;
    }

    public String getFastaFilePath() {
        return fastaFilePath;
    }

    /**
     * @return the problematic line content, or null if not available
     */
    public String getLine() {
        return line;
    }

    @Override
    public String toString() {
        return "FastaReadingException{" +
                "message='" + getMessage() + '\'' +
                ", fastaFilePath='" + fastaFilePath + '\'' +
                ", line=" + (line == null ? "null" : "'" + line + "'") +
                '}';
    }
}

