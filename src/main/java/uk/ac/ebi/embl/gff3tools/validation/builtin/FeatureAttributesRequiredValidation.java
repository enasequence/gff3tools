package uk.ac.ebi.embl.gff3tools.validation.builtin;

import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.validation.FeatureValidation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Anthology;

public class FeatureAttributesRequiredValidation implements FeatureValidation {

    private static final String NO_QUUALIFIERS_MESSAGE = "No attributes are present for accession \"%s\" on feature \"%s\" ";
    public static final String VALIDATION_RULE = "GFF3_FEATURE_ATTRIBUTES_REQUIRED_VALIDATION";

    @Override
    public String getValidationRule() {
        return VALIDATION_RULE;
    }

    @Override
    public void validateFeature(GFF3Feature feature, int line) throws ValidationException {
        String featureName = feature.getName();

        if (GFF3Anthology.ATTRIBUTES_REQUIRED_FEATURE_SET.contains(featureName)
            && feature.getAttributes().isEmpty()) {
            throw new ValidationException(
                    VALIDATION_RULE, line, NO_QUUALIFIERS_MESSAGE.formatted(feature.accession(), featureName));
        }
    }
}