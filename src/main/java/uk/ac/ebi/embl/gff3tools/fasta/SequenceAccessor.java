package uk.ac.ebi.embl.gff3tools.fasta;

import java.io.File;

public class SequenceAccessor {
    private final File file;
    private int startLine; // inclusive
    private int endLine;   // inclusive

    public SequenceAccessor(File file, int startLine, int endLine) {
        this.file = file;
        this.startLine = startLine;
        this.endLine = endLine;
    }

    public File getFile() {
        return file;
    }

    public int getStartLine() {
        return startLine;
    }

    public void setStartLine(int startLine) {
        this.startLine = startLine;
    }

    public int getEndLine() {
        return endLine;
    }

    public void setEndLine(int endLine) {
        this.endLine = endLine;
    }

    /** Placeholder for later: return approximate length from stored lines (ignores line breaks). */
    public long lengthApprox() {
        if (endLine < startLine) return 0;
        return (long) (endLine - startLine + 1); // lines count, not bases (for now)
    }
}

