package uk.ac.ebi.embl.gff3tools.fasta;

import uk.ac.ebi.embl.gff3tools.exception.FastaFileException;
import uk.ac.ebi.embl.gff3tools.fasta.headerutils.JsonHeaderParser;
import uk.ac.ebi.embl.gff3tools.fasta.headerutils.ParsedHeader;
import uk.ac.ebi.embl.gff3tools.fasta.sequenceutils.SequenceAlphabet;
import uk.ac.ebi.embl.gff3tools.fasta.sequenceutils.SequenceIndexBuilder;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.*;

public class SequentialFastaFileReader implements AutoCloseable {

    private static final int BUFFER_SIZE = 64 * 1024;
    private static final byte GT = (byte) '>';
    private static final byte LF = (byte) '\n';
    private static final byte CR = (byte) '\r';

    private final FileChannel channel;
    private final long fileSize;
    private final JsonHeaderParser headerParser;
    private final SequenceAlphabet alphabet;

    public SequentialFastaFileReader(File file) throws IOException {
        this(file, new JsonHeaderParser(), SequenceAlphabet.defaultNucleotideAlphabet());
    }

    public SequentialFastaFileReader(File file, JsonHeaderParser parser, SequenceAlphabet alphabet) throws IOException {
        Objects.requireNonNull(file, "Input FASTA file is null");
        if (!file.exists()) throw new FileNotFoundException(file.getAbsolutePath());
        if (file.isDirectory()) throw new FileNotFoundException("Directory: " + file.getAbsolutePath());
        if (!file.canRead()) throw new IllegalArgumentException("No read permission: " + file.getAbsolutePath());
        this.headerParser = Objects.requireNonNull(parser, "parser");
        this.alphabet = Objects.requireNonNull(alphabet, "alphabet");
        this.channel = new FileInputStream(file).getChannel();
        this.fileSize = channel.size();
    }

    @Override public void close() throws IOException { channel.close(); }
    public boolean readingFile() { return channel.isOpen(); }

    public List<FastaEntryInternal> readAll() throws FastaFileException, IOException {
        long position = 0;
        List<FastaEntryInternal> entries = new ArrayList<>();
        while (true){
            var entry = readNext(position);
            if (entry.isEmpty()) break;
            entries.add(entry.get());
            position = channel.position();
        }
        return entries;
    }

    /** Reads the next FASTA entry starting at or after 'from'. */
    private Optional<FastaEntryInternal> readNext(long from) throws FastaFileException {
        try {
            OptionalLong headerPosOpt = seekToNextHeader(from);
            if (headerPosOpt.isEmpty()) return Optional.empty();

            long headerPos = headerPosOpt.getAsLong();
            channel.position(headerPos);

            String headerLine = readAsciiLineFromCurrentPosition();
            if (headerLine == null) throw new FastaFileException("Header is malformed at byte " + headerPos);
            ParsedHeader ph = headerParser.parse(headerLine);

            long sequenceStartPos = channel.position(); // first byte after header line is the sequence position
            SequenceIndexBuilder sib = new SequenceIndexBuilder(channel, fileSize, alphabet);
            SequenceIndexBuilder.Result res = sib.buildFrom(sequenceStartPos);

            // Move reader cursor to the sequence start position
            channel.position(sequenceStartPos);

            FastaEntryInternal e = new FastaEntryInternal();
            e.setSubmissionId(ph.getId());
            e.setHeader(ph.getHeader());
            e.setFastaStartByte(headerPos);
            e.setSequenceIndex(res.index);

            return Optional.of(e);
        } catch (IOException io) {
            long pos = safePos();
            throw new FastaFileException("I/O while reading FASTA at byte " + pos + ": " + io.getMessage(), io);
        }
    }

    /** Read ASCII bytes from [byteStart, byteEndExclusive) skipping LF/CR; does not change channel.position(). */
    public String readAsciiWithoutNewlines(long byteStart, long byteEndExclusive) throws IOException {
        if (byteStart < 0 || byteEndExclusive < byteStart || byteEndExclusive > fileSize) {
            throw new IllegalArgumentException("Bad byte window: " + byteStart + ".." + byteEndExclusive);
        }
        long remain = byteEndExclusive - byteStart;
        long off = byteStart;

        // pre-size builder with a sane cap (skip newlines, so content <= remain)
        int expect = (int) Math.min(remain, 1_000_000L);
        StringBuilder sb = new StringBuilder(expect);

        ByteBuffer buf = ByteBuffer.allocateDirect(BUFFER_SIZE);
        while (remain > 0) {
            buf.clear();
            int want = (int) Math.min(buf.capacity(), remain);
            buf.limit(want);
            int n = channel.read(buf, off);
            if (n <= 0) break;
            buf.flip();
            while (buf.hasRemaining()) {
                byte b = buf.get();
                if (b == LF || b == CR) continue;         // omit line breaks on the fly
                sb.append((char)(b & 0xFF));               // ASCII
            }
            remain -= n;
            off    += n;
        }
        return sb.toString();
    }

    // ------------------ header seeking & line reading ------------------

    private OptionalLong seekToNextHeader(long from) throws IOException {
        if (from >= fileSize) return OptionalLong.empty();
        ByteBuffer buf = ByteBuffer.allocateDirect(BUFFER_SIZE);

        while (from < fileSize) {
            buf.clear();
            int want = (int) Math.min(buf.capacity(), fileSize - from);
            buf.limit(want);
            int n = channel.read(buf, from);
            if (n <= 0) break;
            buf.flip();
            while (buf.hasRemaining()) {
                long abs = from + buf.position();
                if (buf.get() == GT && isLineStart(abs)) {
                    return OptionalLong.of(abs);
                }
            }
            from += n;
        }
        return OptionalLong.empty();
    }

    private boolean isLineStart(long abs) throws IOException {
        if (abs == 0) return true;
        if (abs > fileSize) return false;
        return peek(abs - 1) == LF;
    }

    private byte peek(long abs) throws IOException {
        if (abs < 0 || abs >= fileSize) return 0;
        ByteBuffer one = ByteBuffer.allocate(1);
        int n = channel.read(one, abs);
        return (n == 1) ? one.get(0) : 0;
    }

    /** Reads one ASCII line from current position, advances past LF or to EOF. */
    private String readAsciiLineFromCurrentPosition() throws IOException {
        long scanPos = channel.position();
        if (scanPos >= fileSize) return null;

        StringBuilder sb = new StringBuilder(256);
        ByteBuffer buf = ByteBuffer.allocateDirect(BUFFER_SIZE);

        while (scanPos < fileSize) {
            buf.clear();
            int want = (int) Math.min(buf.capacity(), fileSize - scanPos);
            buf.limit(want);
            int n = channel.read(buf, scanPos);
            if (n <= 0) break;
            buf.flip();

            int lfIndex = indexOf(buf, LF);
            if (lfIndex >= 0) {
                appendAscii(sb, buf, lfIndex);
                long nextLineStart = scanPos + lfIndex + 1; // consume LF
                channel.position(nextLineStart);
                return sb.toString();
            } else {
                appendAscii(sb, buf, buf.remaining());
                scanPos += n;
            }
        }
        channel.position(fileSize);
        return sb.toString();
    }

    private static int indexOf(ByteBuffer buf, byte target) {
        for (int i = 0; i < buf.remaining(); i++) {
            if (buf.get(buf.position() + i) == target) return i;
        }
        return -1;
    }

    private static void appendAscii(StringBuilder sb, ByteBuffer buf, int len) {
        byte[] chunk = new byte[len];
        buf.get(chunk);
        sb.append(new String(chunk, java.nio.charset.StandardCharsets.US_ASCII));
    }

    private long safePos() {
        try { return channel.position(); } catch (IOException e) { return -1; }
    }
}
