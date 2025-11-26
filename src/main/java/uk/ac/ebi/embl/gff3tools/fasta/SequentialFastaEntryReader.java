package uk.ac.ebi.embl.gff3tools.fasta;

import uk.ac.ebi.embl.gff3tools.exception.FastaReadException;
import uk.ac.ebi.embl.gff3tools.fasta.sequenceutils.SequenceAlphabet;

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
            //find the start & end bytes of the sequence of the current fasta entry
            ScanResult sr = findSequenceLimits();

            FastaEntry e = new FastaEntry();
            e.setId(ph.getId());
            e.setHeader(ph.getHeader());
            e.setFastaStart(headerPos.getAsLong());
            e.setSequenceStart(sr.firstBase);
            e.setSequenceEnd(sr.lastBase);

            this.current = e;
            return true;

        } catch (IOException io) {
            throw new FastaReadException("I/O while reading FASTA", io);
        }
    }

    // ---- private helpers (the only code allowed to touch channel position) ----

    private OptionalLong goToNextFastaEntry() throws IOException { //TODO modify so that the > has to be the first character in line
        long currentPosition = channel.position(), originalPosition = currentPosition;
        if (currentPosition >= fileSize) return OptionalLong.empty();

        ByteBuffer buf = ByteBuffer.allocateDirect(BUF_SIZE); //read the next chunk of the file
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
                        channel.position(potentialFastaStart); //put the channel position on the new greater than
                        return OptionalLong.of(potentialFastaStart);
                    }
                }
            }
            currentPosition += numberOfBytesRead;
        }
        channel.position(originalPosition); //if we didn't find new fasta entry start, go back to original position
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

    private String readAsciiLine() throws IOException {
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

            if (lfIndex >= 0) { //if there is an "\n", read it and position the channel at the end of index
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
}
