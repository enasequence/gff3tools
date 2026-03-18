package uk.ac.ebi.embl.gff3tools.validation.provider;

import uk.ac.ebi.embl.gff3tools.sequence.readers.SequenceReader;
import uk.ac.ebi.embl.gff3tools.sequence.readers.SequenceReaderFactory;
import uk.ac.ebi.embl.gff3tools.validation.ContextProvider;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;

public class SequenceReaderProvider implements ContextProvider<SequenceReader> {

    private HashMap<String, SequenceReader> cached;

    @Override
    public SequenceReader get(ValidationContext context) {
        if (cached == null) {
            try {
                initialiseReader(context);
            }catch (Exception e) {
                //log.oops //todo
            }
        }
        return cached;
    }

    @Override
    public Class<SequenceReader> type() {
        return SequenceReader.class;
    }

    private void initialiseReader(ValidationContext context) throws Exception {
        TranslationContext translationContext = context.get(TranslationContext.class);
        Path sequenceFilePath = translationContext.getSequenceFastaPath();
        if (!Files.exists(sequenceFilePath) || !Files.isRegularFile(sequenceFilePath)) {
            throw new IllegalArgumentException("Not a valid file: " + sequenceFilePath);
        }
        File sequenceFile = sequenceFilePath.toFile();

        switch (translationContext()){
            case FASTA -> {
                cached = SequenceReaderFactory.readFasta(sequenceFile); //todo revamp after fasta reader update live
            }
            case PLAIN_SEQUENCE -> {
                cached = SequenceReaderFactory.readPlainSequence(sequenceFile, "temp");  //todo revamp after fasta reader update live
            }
            default -> {
                throw new IllegalArgumentException("Unknown sequence file format");
            }
        }
    }
}