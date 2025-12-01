package uk.ac.ebi.embl.gff3tools.fasta;

import uk.ac.ebi.embl.gff3tools.exception.FastaReadException;
import uk.ac.ebi.embl.gff3tools.fasta.sequenceutils.ByteSpan;
import uk.ac.ebi.embl.gff3tools.fasta.sequenceutils.LineEntry;
import uk.ac.ebi.embl.gff3tools.fasta.sequenceutils.SequenceAlphabet;
import uk.ac.ebi.embl.gff3tools.fasta.sequenceutils.SequenceIndex;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.OptionalLong;

public class SequentialFastaEntryReader implements AutoCloseable {

    private static final int BUF_SIZE = 64 * 1024;
    private static final byte GT = (byte) '>';
    private static final byte LF = (byte) '\n';

    private final FileChannel channel;
    private final long fileSize; //size of file in bytes

    private final JsonHeaderParser headerParser;
    private final SequenceAlphabet alphabet;
    private FastaEntry current;

    public SequentialFastaEntryReader(File file) throws FileNotFoundException {
        this(file, new JsonHeaderParser(), SequenceAlphabet.defaultNucleotideAlphabet());
    }

    public SequentialFastaEntryReader(File file, JsonHeaderParser parser, SequenceAlphabet alphabet) throws FileNotFoundException {
        if (file == null) throw new IllegalStateException("Input FASTA file is null");
        if (!file.exists()) throw new FileNotFoundException(file.getAbsolutePath());
        if (file.isDirectory()) throw new FileNotFoundException("Directory: " + file.getAbsolutePath());
        if (!file.canRead()) throw new IllegalArgumentException("No read permission: " + file.getAbsolutePath());

        this.headerParser = parser;
        this.alphabet = alphabet;

        try {
            this.channel = new FileInputStream(file).getChannel();
            this.fileSize = channel.size();
        } catch (IOException e) {
            throw new RuntimeException("Open channel failed", e);
        }
    }

    public void close() throws IOException { channel.close(); }
    public boolean readingFile() { return channel.isOpen(); }
    public FastaEntry getCurrentEntry() { return current; }



    public boolean readNext() throws FastaReadException {
        try {
            long startPos = channel.position();
            OptionalLong headerPos = goToNextFastaEntry();
            if (headerPos.isPresent() || !peekIsGT(startPos)) return false;

            //parse id & header json
            String headerLine = readAsciiLine();
            if (headerLine == null) return false;
            ParsedHeader ph = headerParser.parse(headerLine);
            //find information about the current positions and line structure of the sequence
            SequenceIndex idx = buildSequenceIndex();

            FastaEntry e = new FastaEntry();
            e.setId(ph.getId());
            e.setHeader(ph.getHeader());
            e.setFastaStart(headerPos.getAsLong());
            e.setSequenceStart(idx.firstBaseByte);
            e.setSequenceEnd(idx.lastBaseByte);
            e.setSequenceIndex(idx);

            this.current = e;
            return true;

        } catch (IOException io) {
            throw new FastaReadException("I/O while reading FASTA", io);
        }
    }

    // ---- private helpers (the only code allowed to touch channel position) ----

    /** finds the first next '>' after the current fasta entry and puts the channel reader position there.
     * If there is no later fasta entry, returns empty and leaves the channel reader position where it was.
     * **/
    private OptionalLong goToNextFastaEntry() throws IOException {
        long currentPosition = channel.position(), originalPosition = currentPosition;
        if (currentPosition >= fileSize) return OptionalLong.empty();

        //read the file content from current position (which should be somewhere in the current fasta entry or at the beginning of file) chunk by chunk
        ByteBuffer buf = ByteBuffer.allocateDirect(BUF_SIZE);
        while (currentPosition < fileSize) {
            buf.clear();
            int minToRead = (int) Math.min(buf.capacity(), fileSize - currentPosition);
            buf.limit(minToRead);

            int numberOfBytesRead = channel.read(buf, currentPosition);
            if (numberOfBytesRead <= 0) break; //no bytes were read, probably paranoid

            buf.flip();
            while (buf.hasRemaining()) {
                if (buf.get() == GT) {
                    long potentialFastaStart = currentPosition + buf.position() - 1;
                    if(peekIsEndOfLine(potentialFastaStart - 1)) {
                        // found new fasta entry start
                        channel.position(potentialFastaStart);
                        return OptionalLong.of(potentialFastaStart);
                    }
                }
            }
            currentPosition += numberOfBytesRead;
        }
        //found no fasta starting after the current channel reader position
        channel.position(originalPosition);
        return OptionalLong.empty();
    }

    /* Just checks if the character at the given position equals '>' char, does not move the channel.position() */
    private boolean peekIsEndOfLine(long position) throws IOException {
        if (position >= fileSize) return false;
        ByteBuffer one = ByteBuffer.allocate(1);
        int n = channel.read(one, position);
        return n == 1 && one.get(0) == LF;
    }

    /* Just checks if the character at the given position equals '>' char, does not move the channel.position() */
    private boolean peekIsGT(long position) throws IOException {
        if (position >= fileSize) return false;
        ByteBuffer one = ByteBuffer.allocate(1);
        int n = channel.read(one, position);
        return n == 1 && one.get(0) == GT;
    }

    /* returns entire next line from the current reader position or the maximum buffer size if the line is too large to safely process (unlikely).
    * Places current channel reader position at the first next unread character. (end of line or )
    * */ //TODO return boolean to see whether the line was read completely
    private String readAsciiLine() throws IOException { //todo unfuck
        if (channel.position() >= fileSize) return null;

        StringBuilder sb = new StringBuilder(256);
        ByteBuffer buf = ByteBuffer.allocateDirect(BUF_SIZE);
        long currentPosition = channel.position();

        while (currentPosition < fileSize) {
            buf.clear();
            int toRead = (int) Math.min(buf.capacity(), fileSize - currentPosition);
            buf.limit(toRead);

            int numberOfBytesRead = channel.read(buf, currentPosition);
            if (numberOfBytesRead <= 0) break;

            buf.flip();

            //find end of line
            int lfIndex = -1;
            for (int i = 0; i < buf.remaining(); i++) {
                if (buf.get(buf.position() + i) == LF) { lfIndex = i; break; }
            }

            if (lfIndex >= 0) { //if there is an "\n", read it and position the channel at the end of line
                byte[] chunk = new byte[lfIndex];
                buf.get(chunk);
                sb.append(new String(chunk, StandardCharsets.US_ASCII));
                buf.get(); // consume LF
                channel.position(currentPosition + lfIndex + 1); //skip separator
                int len = sb.length();
                if (len>0 && sb.charAt(len-1)=='\r') sb.setLength(len-1);
                return sb.toString();
            } else { //otherwise read entire chunk, but this is unlikely to happen as the buffer should be large enough
                byte[] chunk = new byte[buf.remaining()];
                buf.get(chunk);
                sb.append(new String(chunk, StandardCharsets.US_ASCII));
                currentPosition += numberOfBytesRead;
            }
        }

        channel.position(fileSize);
        return sb.toString();
    }

    private static final class ScanResult {
        final long firstBase, lastBase, nextHeader;
        ScanResult(long f, long l, long n){ firstBase=f; lastBase=l; nextHeader=n; }
    }

    private ScanResult findSequenceLimits() throws IOException {
        long currentPosition = channel.position();
        long first = -1, last = -1, nextHdr = fileSize;

        ByteBuffer buf = ByteBuffer.allocateDirect(BUF_SIZE);

        outer:
        while (currentPosition < fileSize) {
            buf.clear();
            int toRead = (int) Math.min(buf.capacity(), fileSize - currentPosition);
            buf.limit(toRead);
            int n = channel.read(buf, currentPosition);
            if (n<=0) break;
            buf.flip();
            while (buf.hasRemaining()) {
                byte b = buf.get();
                long abs = currentPosition + buf.position() - 1;
                if (b == GT) { nextHdr = abs; break outer; }
                if (alphabet.isAllowed(b)) {
                    if (first < 0) first = abs;
                    last = abs;
                }
            }
            currentPosition += n;
        }
        channel.position(nextHdr);
        return new ScanResult(first, last, nextHdr);
    }

    private SequenceIndex buildSequenceIndex() throws IOException {
        long pos = channel.position();
        long firstBaseByte = -1, lastBaseByte = -1, nextHdr = fileSize;

        long currentLineFirstByte = -1;      // byte of first base in current line
        long currentLineLastByte  = -1;      // byte of last base in current line
        long basesSoFar = 0;                 // total bases committed to 'lines'
        long basesInCurrentLine = 0;

        java.util.ArrayList<LineEntry> lines = new java.util.ArrayList<>();

        ByteBuffer buf = ByteBuffer.allocateDirect(BUF_SIZE);

        outer:
        while (pos < fileSize) {
            buf.clear();
            int toRead = (int) Math.min(buf.capacity(), fileSize - pos);
            buf.limit(toRead);
            int n = channel.read(buf, pos);
            if (n <= 0) break;
            buf.flip();
            while (buf.hasRemaining()) {
                byte b = buf.get();
                long abs = pos + buf.position() - 1;

                if (b == GT) { // next header begins
                    nextHdr = abs;
                    // finalize the current line if it has bases
                    if (basesInCurrentLine > 0) {
                        long baseStart = basesSoFar + 1;
                        long baseEnd   = basesSoFar + basesInCurrentLine;
                        lines.add(new LineEntry(baseStart, baseEnd,
                                currentLineFirstByte, currentLineLastByte + 1)); // end exclusive
                        basesSoFar += basesInCurrentLine;
                    }
                    break outer;
                }

                if (b == '\n') {
                    if (basesInCurrentLine > 0) {
                        long baseStart = basesSoFar + 1;
                        long baseEnd   = basesSoFar + basesInCurrentLine;
                        lines.add(new LineEntry(baseStart, baseEnd,
                                currentLineFirstByte, currentLineLastByte + 1));
                        basesSoFar += basesInCurrentLine;
                        basesInCurrentLine = 0;
                        currentLineFirstByte = -1;
                        currentLineLastByte = -1;
                    }
                    continue; // newline consumed
                }

                if (alphabet.isAllowed(b)) {
                    if (currentLineFirstByte < 0) currentLineFirstByte = abs;
                    currentLineLastByte = abs;
                    basesInCurrentLine++;

                    if (firstBaseByte < 0) firstBaseByte = abs;
                    lastBaseByte = abs;
                    continue;
                }

                // Non-allowed, non-newline byte: ignore (you said the lines only contain bases + '\n')
            }
            pos += n;
        }

        // EOF: finalize any unterminated line with bases
        if (basesInCurrentLine > 0) {
            long baseStart = basesSoFar + 1;
            long baseEnd   = basesSoFar + basesInCurrentLine;
            lines.add(new LineEntry(baseStart, baseEnd,
                    currentLineFirstByte, currentLineLastByte + 1));
            basesSoFar += basesInCurrentLine;
        }

        channel.position(nextHdr);
        return new SequenceIndex(firstBaseByte, lastBaseByte, 1, 1, lines); //todo fix
    }

}
