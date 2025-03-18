package uk.ac.ebi.embl.converter.fftogff3;

import uk.ac.ebi.embl.converter.IConversionRule;
import uk.ac.ebi.embl.converter.gff3.GFF3File;
import uk.ac.ebi.embl.converter.gff3.GFF3Header;
import uk.ac.ebi.embl.converter.gff3.GFF3Sequence;
import uk.ac.ebi.embl.flatfile.reader.embl.EmblEntryReader;

import java.util.ArrayList;
import java.util.List;


public class FFGFF3FileFactory implements IConversionRule<EmblEntryReader, GFF3File>  {
    @Override
    public GFF3File from(EmblEntryReader input) throws ConversionError {
        GFF3Header header = new GFF3Header("3.1.26");
        FFGFF3SequenceFactory seqFactory = new FFGFF3SequenceFactory();
        List<GFF3Sequence> sequences = new ArrayList<>();
        try {
            while (input.read() != null && input.isEntry()) {
                sequences.add(seqFactory.from(input.getEntry()));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return new GFF3File(header, sequences);

      }
  }