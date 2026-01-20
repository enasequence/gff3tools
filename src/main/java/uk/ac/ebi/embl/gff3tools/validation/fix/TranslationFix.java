package uk.ac.ebi.embl.gff3tools.validation.fix;

import lombok.extern.slf4j.Slf4j;
import uk.ac.ebi.embl.api.translation.Codon;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.validation.meta.FixMethod;
import uk.ac.ebi.embl.gff3tools.validation.meta.Gff3Fix;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationContext;

import java.util.ArrayList;
import java.util.List;

import static uk.ac.ebi.embl.gff3tools.validation.meta.ValidationType.FEATURE;

@Slf4j
@Gff3Fix(
        name = "CDS_TRANSLATION",
        description = "Perform translation for the for the passed CDS feature")
public class TranslationFix {

    @FixMethod(
            rule = "CDS_TRANSLATION",
            description = "Perform translation for the for the passed CDS feature",
            type = FEATURE)
    public void fixFeature(GFF3Feature feature, int line, ValidationContext context) {

    }


    /*public void translateCodons(byte[] sequence) {

        int startIndex = codonStart - 1;
        int length = sequence.length;

        List<Codon> codons = new ArrayList<>(length / 3);
        int xCount = 0;

        int index = startIndex;

        // Translate complete codons
        while (index + 3 <= length) {
            Codon codon = translateCodon(sequence, index, index == startIndex);
            codons.add(codon);

            if (codon.getAminoAcid() == 'X') {
                xCount++;
            }

            index += 3;
        }

        // Fail if more than 50% X
        if (!codons.isEmpty() && xCount > codons.size() / 2) {
            //ValidationException.throwError("Translator-20");
        }

        // Handle trailing bases
        handleTrailingBases(sequence, index, codons);

        result.setCodons(codons);
    }

    private Codon translateCodon(
            byte[] sequence,
            int index,
            boolean isFirstCodon
    ) {
        Codon codon = new Codon();
        codon.setCodon(new String(sequence, index, 3));
        codon.setPos(index + 1);

        if (isFirstCodon && !fivePrimePartial) {
            translateStartCodon(codon);
        } else {
            translateOtherCodon(codon);
        }

        return codon;
    }

    private void handleTrailingBases(
            byte[] sequence,
            int index,
            List<Codon> codons
    ) {
        int trailing = sequence.length - index;

        if (trailing <= 0) {
            //result.setTrailingBases("");
            return;
        }

        Codon codon = new Codon();
        String partial = new String(sequence, index, trailing);
        codon.setCodon(extendCodon(partial));
        codon.setPos(index + 1);

        if (index == codonStart - 1 && !fivePrimePartial) {
            translateStartCodon(codon, result);
        } else {
            translateOtherCodon(codon);
        }

        // Only keep if not translated to X
        if (codon.getAminoAcid() != 'X') {
            codons.add(codon);
            result.setTrailingBases("");
        } else {
            result.setTrailingBases(partial);
        }
    }
*/

}
