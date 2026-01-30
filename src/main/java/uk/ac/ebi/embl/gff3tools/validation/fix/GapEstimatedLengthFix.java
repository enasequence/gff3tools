package uk.ac.ebi.embl.gff3tools.validation.fix;

import lombok.extern.slf4j.Slf4j;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.utils.ConversionUtils;
import uk.ac.ebi.embl.gff3tools.utils.OntologyClient;
import uk.ac.ebi.embl.gff3tools.utils.OntologyTerm;
import uk.ac.ebi.embl.gff3tools.validation.meta.FixMethod;
import uk.ac.ebi.embl.gff3tools.validation.meta.Gff3Fix;

import java.util.Optional;

import static uk.ac.ebi.embl.gff3tools.validation.meta.ValidationType.FEATURE;

@Slf4j
@Gff3Fix(name = "GAP_ESTIMATED_LENGTH", description = "Set estimated_length for a gap or assembly_gap feature")
public class GapEstimatedLengthFix {

    private final OntologyClient ontologyClient = ConversionUtils.getOntologyClient();

    @FixMethod(
            rule = "GAP_ESTIMATED_LENGTH",
            description = "Set estimated_length for a gap feature",
            type = FEATURE)
    public void fixFeature(GFF3Feature feature, int line) {

        Optional<String> soIdOpt = ontologyClient.findTermByNameOrSynonym(feature.getName());
        if (soIdOpt.isEmpty()) return;

        if (OntologyTerm.GAP.ID.equals(soIdOpt.get()) && !feature.hasAttribute(GFF3Attributes.ESTIMATED_LENGTH)) {
            feature.addAttribute(GFF3Attributes.ESTIMATED_LENGTH, String.valueOf(feature.getLength()));
            log.info("Adding {} for feature at line: {}", GFF3Attributes.ESTIMATED_LENGTH, line);
        }

    }
}
