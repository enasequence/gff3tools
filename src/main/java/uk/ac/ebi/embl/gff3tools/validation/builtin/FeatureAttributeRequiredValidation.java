package uk.ac.ebi.embl.gff3tools.validation.builtin;

import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Anthology;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.validation.Validation;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationMethod;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationType;

public class FeatureAttributeRequiredValidation extends Validation {

    private static final String NO_QUALIFIERS_MESSAGE =
            "No attributes are present for accession \"%s\" on feature \"%s\" ";

    @ValidationMethod(type = ValidationType.FEATURE)
    public void validateFeature(GFF3Feature feature, int line) throws ValidationException {
        String featureName = feature.getName();

        if (GFF3Anthology.ATTRIBUTES_REQUIRED_FEATURE_SET.contains(featureName)
                && feature.getAttributes().isEmpty()) {
            throw new ValidationException(line, NO_QUALIFIERS_MESSAGE.formatted(feature.accession(), featureName));
        }
    }
}
