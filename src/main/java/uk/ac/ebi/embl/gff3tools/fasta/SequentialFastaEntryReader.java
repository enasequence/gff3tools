package uk.ac.ebi.embl.gff3tools.fasta;

import uk.ac.ebi.embl.gff3tools.exception.FastaReadException;
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
import java.util.*;

public class SequentialFastaEntryReader implements AutoCloseable {

    // -------- constants
    private static final int SCAN_BUF_SIZE   = 64 * 1024;
    private static final int COUNT_BUF_SIZE  = 8  * 1024;
    private static final byte GT = (byte) '>';
    private static final byte LF = (byte) '\n';

    // -------- file + config
    private final FileChannel channel;
    private final long fileSize;
    private final JsonHeaderParser headerParser;
    private final SequenceAlphabet alphabet;

    // -------- state/result
    private final Map<String, SequenceIndex> indexById = new LinkedHashMap<>();

    // -------- ctor
    public SequentialFastaEntryReader(File file) throws IOException {
        this(file, new JsonHeaderParser(), SequenceAlphabet.defaultNucleotideAlphabet());
    }

    public SequentialFastaEntryReader(File file, JsonHeaderParser parser, SequenceAlphabet alphabet)
            throws FileNotFoundException, IOException {
        Objects.requireNonNull(file, "Input FASTA file is null");
        if (!file.exists()) throw new FileNotFoundException(file.getAbsolutePath());
        if (file.isDirectory()) throw new FileNotFoundException("Directory: " + file.getAbsolutePath());
        if (!file.canRead()) throw new IllegalArgumentException("No read permission: " + file.getAbsolutePath());

        this.headerParser = Objects.requireNonNull(parser, "parser");
        this.alphabet = Objects.requireNonNull(alphabet, "alphabet");

        this.channel = new FileInputStream(file).getChannel(); //exception will bubble up if fails
        this.fileSize = channel.size();
    }

    // -------- lifecycle of the read
    @Override public void close() throws IOException { channel.close(); }
    public boolean readingFile() { return channel.isOpen(); }

    // -------- main iteration

    /** Reads the next FASTA entry, if there is none it returns an empty object*/
    public Optional<FastaEntry> readNext(long from) throws FastaReadException {
        try {
            OptionalLong headerPosition = seekToNextHeader(from);
            if (headerPosition.isEmpty()) return Optional.empty(); // no next FASTA entry detected
            channel.position(headerPosition.getAsLong()); //otherwise, position at start of new fasta

            // parse header & build sequence index
            String headerLine = readAsciiLineFromCurrentPosition(); // scan first line, parser
            if (headerLine == null) throw new FastaReadException("Header is malformed");
            ParsedHeader ph = headerParser.parse(headerLine);
            SequenceIndex idx = buildSequenceIndex();

            // produce current entry
            FastaEntry newEntry = new FastaEntry();
            newEntry.setId(ph.getId());
            newEntry.setHeader(ph.getHeader());
            newEntry.setFastaStartByte(headerPosition.getAsLong());
            newEntry.setSequenceIndex(idx);

            return Optional.of(newEntry);

        } catch (IOException io) {
            long position;
            try{
                position= channel.position();
            } catch (IOException e) {
                position = -1;
            }
            throw new FastaReadException("I/O while reading FASTA at byte " + position + ": " + io.getMessage(), io);
        }
    }

    // =====================================================================
    // =                          SCANNING (to next header)                                 =
    // =====================================================================

    /** Finds next header ('>') that starts a line (at file start or after LF). */
    private OptionalLong seekToNextHeader(long from) throws IOException {
        if (from >= fileSize) return OptionalLong.empty();

        ByteBuffer buf = ByteBuffer.allocateDirect(SCAN_BUF_SIZE);

        while (from < fileSize) {
            buf.clear();
            int want = (int) Math.min(buf.capacity(), fileSize - from);
            buf.limit(want);
            int n = channel.read(buf, from);
            if (n <= 0) break;
            buf.flip();

            while (buf.hasRemaining()) {
                long positionToCheck = from + buf.position();
                byte b = buf.get();
                if (peekIfFastaHeaderStart(b, positionToCheck)) {
                    return OptionalLong.of(positionToCheck);
                }
            }
            from += n;
        }
        return OptionalLong.empty();
    }

    /** Reads until end of one ASCII line from current channel.position() ( '\n' terminated or EOF).
     * Advances channel past end of line or to EOF */
    private String readAsciiLineFromCurrentPosition() throws IOException {
        long scanPos = channel.position();
        if (scanPos >= fileSize) return null;

        StringBuilder sb = new StringBuilder(256);
        ByteBuffer buf = ByteBuffer.allocateDirect(SCAN_BUF_SIZE);

        while (scanPos < fileSize) {

            // fill buffer from disk
            buf.clear();
            int want = (int) Math.min(buf.capacity(), fileSize - scanPos);
            buf.limit(want);
            int n = channel.read(buf, scanPos);
            if (n <= 0) break;

            buf.flip();

            // find end of line in the bytes we already have
            int lfIndex = indexOf(buf, LF);
            if (lfIndex >= 0)
                {// if end of line found, append bytes up to (not including) LF
                appendAscii(sb, buf, lfIndex);
                long nextLineStart = scanPos + lfIndex + 1; // consume LF
                channel.position(nextLineStart);
                return sb.toString();
            } else {
                // no LF in this chunk; append all bytes and continue
                appendAscii(sb, buf, buf.remaining());
                scanPos += n;
            }
        }

        // read up to EOF
        channel.position(fileSize);
        return sb.toString();
    }

    /** gets index of a target byte character in a byte buffer, returns -1 if not found **/
    private static int indexOf(ByteBuffer buf, byte target) {
        for (int i = 0; i < buf.remaining(); i++) {
            if (buf.get(buf.position() + i) == target) return i;
        }
        return -1;
    }

    /** Append exactly len bytes from buf to sb as US-ASCII, advancing buf.position() by len. */
    private static void appendAscii(StringBuilder sb, ByteBuffer buf, int len) {
        byte[] chunk = new byte[len];
        buf.get(chunk);
        sb.append(new String(chunk, java.nio.charset.StandardCharsets.US_ASCII));
    }

    // =============================================================================
    // =                      SEQUENCE INDEX SCAN & BUILD                          =
    // =============================================================================

    /** Builds the per-line index for the sequence starting at current position (right after header line). */
    private SequenceIndex buildSequenceIndex() throws IOException {
        ScanState s = initScanState(channel.position());
        ByteBuffer buf = newScanBuffer();

        while (s.pos<fileSize) {
            int n = fillBuffer(buf, s.pos);
            if (n <= 0) break;
            if (processBuffer(buf, s)) break; // true => we hit next header; stop scanning
            s.pos += n;
        }

        finalizeOpenLineIfAny(s);
        // leave channel at next header (or EOF)
        channel.position(s.nextHdr);

        long startN = 0, endN = 0;
        if (!s.lines.isEmpty()) {
            LineEntry first = s.lines.get(0);
            LineEntry last  = s.lines.get(s.lines.size() - 1);
            startN = countLeadingNsInLine(first);
            endN   = countTrailingNsInLine(last);
        }

        return new SequenceIndex(s.firstBaseByte, startN, s.lastBaseByte, endN, s.lines);
    }

// ---------------------------------------------------------------------
//                          scan helpers
// ---------------------------------------------------------------------

    /** All mutable scanning state for one sequence. */
    private static final class ScanState {
        long pos;                    // absolute file position we’re scanning from
        long firstBaseByte = -1;     // byte of first allowed base seen
        long lastBaseByte  = -1;     // byte of last  allowed base seen
        long nextHdr;                // byte of next '>' that starts a line (or fileSize)

        long lineFirstByte = -1;     // byte of first base in current line
        long lineLastByte  = -1;     // byte of last  base in current line
        long basesSoFar = 0;         // total bases committed to s.lines
        long basesInLine = 0;        // bases accumulated in current line (not yet committed)

        final java.util.ArrayList<LineEntry> lines = new java.util.ArrayList<>(256);
        ScanState(long startPos, long fileSize) { this.pos = startPos; this.nextHdr = fileSize; }
    }

    private ScanState initScanState(long startPos) {
        return new ScanState(startPos, fileSize);
    }

    private ByteBuffer newScanBuffer() {
        return ByteBuffer.allocateDirect(SCAN_BUF_SIZE);
    }

    private int fillBuffer(ByteBuffer buf, long at) throws IOException {
        buf.clear();
        int want = (int) Math.min(buf.capacity(), fileSize - at);
        buf.limit(want);
        return channel.read(buf, at);
    }

    /**
     * Process a filled buffer. Returns true if scanning should stop (we found the next header),
     * false to keep scanning.
     */
    private boolean processBuffer(ByteBuffer buf, ScanState s) throws IOException {
        buf.flip();
        while (buf.hasRemaining()) {
            // capture index BEFORE get(), so abs == s.pos + idx
            int idx = buf.position();
            byte b = buf.get();
            long abs = s.pos + idx;

            if (peekIfFastaHeaderStart(b, abs)) {
                s.nextHdr = abs;
                commitOpenLineIfAny(s);
                return true; // done scanning current entry
            }

            if (isNewline(b)) {
                commitOpenLineIfAny(s);
                continue;
            }

            if (isAllowedBase(b)) {
                observeBaseByte(abs, s);
                continue;
            }

            // else: ignore unexpected bytes (spec: sequence lines contain bases + '\n')
        }
        return false;
    }

    // ---------------------------------------------------------------------
    //                    state mutation helpers (lines)
    // ---------------------------------------------------------------------

    /** We saw a base at absolute byte 'abs'. Update current line + first/last markers. */
    private void observeBaseByte(long abs, ScanState s) {
        if (s.lineFirstByte < 0) s.lineFirstByte = abs;
        s.lineLastByte = abs;
        s.basesInLine++;

        if (s.firstBaseByte < 0) s.firstBaseByte = abs;
        s.lastBaseByte = abs;
    }

    /** If current line has bases, convert it into a LineEntry and reset line accumulators. */
    private void commitOpenLineIfAny(ScanState s) {
        if (s.basesInLine <= 0) return;

        long baseStart = s.basesSoFar + 1;
        long baseEnd   = s.basesSoFar + s.basesInLine;
        long byteStart = s.lineFirstByte;
        long byteEndEx = s.lineLastByte + 1; // half-open

        s.lines.add(new LineEntry(baseStart, baseEnd, byteStart, byteEndEx));

        s.basesSoFar += s.basesInLine;
        s.basesInLine = 0;
        s.lineFirstByte = -1;
        s.lineLastByte  = -1;
    }

    /** If EOF hit with an unterminated line, commit it. */
    private void finalizeOpenLineIfAny(ScanState s) {
        commitOpenLineIfAny(s);
    }


    private static void addLine(List<LineEntry> lines,
                                long basesSoFar,
                                long basesInCurrentLine,
                                long firstByte, long lastByte) {
        long baseStart = basesSoFar + 1;
        long baseEnd   = basesSoFar + basesInCurrentLine;
        // byteEndExclusive is lastByte + 1 (ASCII 1 byte/base)
        lines.add(new LineEntry(baseStart, baseEnd, firstByte, lastByte + 1));
    }

    // =====================================================================
    // =                    EDGE 'N' COUNT HELPERS                          =
    // =====================================================================

    /** Count leading 'N'/'n' in the given line’s byte range. */
    private long countLeadingNsInLine(LineEntry line) throws IOException {
        long remaining = line.lengthBytes();
        long offset = line.byteStart;
        long count = 0;

        ByteBuffer buf = ByteBuffer.allocateDirect(COUNT_BUF_SIZE);

        while (remaining > 0) {
            buf.clear();
            int want = (int) Math.min(buf.capacity(), remaining);
            buf.limit(want);
            int n = channel.read(buf, offset);
            if (n <= 0) break;
            buf.flip();

            for (int i = 0; i < n; i++) {
                byte b = buf.get();
                if (isNBase(b)) {
                    count++;
                } else {
                    return count;
                }
            }
            remaining -= n;
            offset += n;
        }
        return count;
    }

    /** Count trailing 'N'/'n' in the given line’s byte range (scan forward, track tail run). */
    private long countTrailingNsInLine(LineEntry line) throws IOException {
        long remaining = line.lengthBytes();
        long offset = line.byteStart;
        long trailingRun = 0;

        ByteBuffer buf = ByteBuffer.allocateDirect(COUNT_BUF_SIZE);

        while (remaining > 0) {
            buf.clear();
            int want = (int) Math.min(buf.capacity(), remaining);
            buf.limit(want);
            int n = channel.read(buf, offset);
            if (n <= 0) break;
            buf.flip();

            for (int i = 0; i < n; i++) {
                byte b = buf.get();
                if (isNBase(b)) {
                    trailingRun++; // extend current tail run
                } else {
                    trailingRun = 0; // reset tail run on any non-N
                }
            }
            remaining -= n;
            offset += n;
        }
        return trailingRun;
    }

    // ---------------------------------------------------------------------
    //                    helper peek byte functions
    // ---------------------------------------------------------------------


    /** True if absolute position is at file start OR previous byte is '\n'. */
    private boolean peekIfLineStart(long absPos) throws IOException {
        if (absPos == 0) return true;
        if (absPos > fileSize) return false;
        return peekByte(absPos - 1) == LF;
    }

    /** Absolute peek (does not change channel.position()). Returns 0 if OOB. */
    private byte peekByte(long absPos) throws IOException {
        if (absPos < 0 || absPos >= fileSize) return 0;
        ByteBuffer one = ByteBuffer.allocate(1);
        int n = channel.read(one, absPos);
        return (n == 1) ? one.get(0) : 0;
    }

    private boolean peekIfFastaHeaderStart(byte b, long abs) throws IOException {
        return b == GT && peekIfLineStart(abs);
    }

    private boolean isNewline(byte b) {
        return b == LF; // we already normalize CRLF earlier; lines are LF-terminated in scanning
    }

    private boolean isAllowedBase(byte b) {
        return alphabet.isAllowed(b);
    }

    private boolean isNBase(byte b) {
        return alphabet.isNBase(b);
    }
}
