package uk.ac.ebi.embl.converter.rules;

import io.vavr.Tuple2;
import io.vavr.control.Either;
import uk.ac.ebi.embl.api.entry.feature.Feature;
import uk.ac.ebi.embl.converter.gff3.GFF3Phase;
import uk.ac.ebi.embl.converter.gff3.GFF3Record;
import uk.ac.ebi.embl.converter.gff3.GFF3Strand;

import java.util.*;

public class FFToGFF3Record implements IConversionRule<Feature, GFF3Record> {
    String accession;

    public FFToGFF3Record(String accession) {
        this.accession = accession;
    }

    @Override
    public Tuple2<Optional<GFF3Record>, List<ConversionError>> from(ListIterator<Feature> features) {
        Feature feature = features.next();
        return fromFeature(feature);
    }

    public Tuple2<Optional<GFF3Record>, List<ConversionError>> fromFeature(Feature feature) {
        ArrayList<ConversionError> errors = new ArrayList<>();

        String featureType = feature.getName();
        long start = feature.getLocations().getMinPosition();
        long end = feature.getLocations().getMaxPosition();
        GFF3Strand strand = feature.getLocations().isComplement() ? GFF3Strand.Negative : GFF3Strand.Positive;
        Optional<GFF3Phase> phase = feature.getQualifiers().stream()
                .filter(qualifier -> qualifier.getName().equalsIgnoreCase("phase"))
                .findFirst()
                .flatMap((q) -> getPhaseFromString(q.getValue())
                        .map(Optional::of)
                        .getOrElse(Optional.empty())) ;

        GFF3Record gff3Record = new GFF3Record(accession,
                Optional.empty(),
                featureType,
                start,
                end,
                Optional.empty(),
                strand,
                phase);
        feature.getQualifiers().forEach(qualifier -> {
            gff3Record.addAttribute(qualifier.getName(), qualifier.getValue());
        });
        return new Tuple2<>(Optional.of(gff3Record), errors);
    }

    private Either<ConversionError, GFF3Phase> getPhaseFromString(String value) {
        return switch (value) {
            case "0" -> Either.right(GFF3Phase.First);
            case "1" -> Either.right(GFF3Phase.Second);
            case "2" -> Either.right(GFF3Phase.Third);
            default -> Either.left(new InvalidGF3Phase());
        };
    }

    static class InvalidGF3Phase extends ConversionError {}
}
