package uk.ac.ebi.embl.gff3tools.fasta;

import uk.ac.ebi.embl.gff3tools.exception.FastaReadException;
import uk.ac.ebi.embl.gff3tools.validation.ValidationRegistry;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.nio.channels.FileChannel;

/*
    IMPORTANT: This file reader works with the assumption that the file is UTF-8. If it isn't, expect garbage. //TODO verify if this edge case ever actually happens, it shouldnt but if I need a special exception for it I should know
 */
public class SequentialFastaEntryReader { //todo: ignore the fact of whether this is a Singleton or needs a Manager to manage channels to different file channels FOR NOW

    File multiFastaFile;
    private final FileChannel channel;

    String currentId; //accessionNumber
    FastaHeader header;


    public SequentialFastaEntryReader(File file) {
        String reason = null;
        if (file == null) {
            throw new IllegalStateException("Inputted FASTA file object is null.");
        }else if (!file.exists()) {
            throw new IllegalArgumentException("File does not exist:" + file.getAbsolutePath());
        } else if (file.isDirectory()) {
            throw new IllegalArgumentException("Path is a directory, not a regular file:" + file.getAbsolutePath());
        } else if (!file.canRead()) {
            throw new IllegalArgumentException("Read permission denied for file:"  + file.getAbsolutePath());
        }

        this.multiFastaFile = file;
        this.channel = new FileInputStream(file).getChannel();
    }

    public void goToNextFastaEntry() {

    }


}
