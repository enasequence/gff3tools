package uk.ac.ebi.embl.gff3tools.validation.builtin;

import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.utils.ConversionUtils;
import uk.ac.ebi.embl.gff3tools.validation.AnnotationValidation;
import uk.ac.ebi.embl.gff3tools.validation.FeatureValidation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public class FeatureQualifiersRequiredCheck implements FeatureValidation {

    public static final String VALIDATION_RULE = "GFF3_FEATURE_ATTRIBUTES_REQUIRED_CHECK";
    HashSet<String> ffFeatureNames = new HashSet<>(){{
        add("misc_binding");
        add("misc_difference");
        add("misc_feature");
        add("misc_recomb");
        add("misc_RNA");
        add("misc_signal");
        add("misc_structure");
    }};

    @Override
    public String getValidationRule() {
        return VALIDATION_RULE;
    }

    @Override
    public void validateFeature(GFF3Feature feature, int line) throws ValidationException {
        HashSet<String> gff3FeatureNames = getRelevantFeatureNames();
        if (gff3FeatureNames.contains(feature.getName())) {
            feature.getAttributes();
        }

    }

    private HashSet<String> getRelevantFeatureNames() {
        return new HashSet<>();
    }

    /*
    private final List<String> featuresList = new ArrayList();
    private static final String QUALIFIERS_REQUIRED_ID_1 = "FeatureQualifiersRequiredCheck";

    private void init() {
        DataSet keySet = GlobalDataSets.getDataSet(GlobalDataSetFile.FEATURE_REQUIRE_QUALIFIERS);
        if (keySet != null) {
            for(DataRow dataRow : keySet.getRows()) {
                String key = dataRow.getString(0);
                this.featuresList.add(key);
            }
        }

    }

    public ValidationResult check(Feature feature) {
        this.init();
        this.result = new ValidationResult();
        if (feature == null) {
            return this.result;
        } else {
            if (this.featuresList.contains(feature.getName()) && feature.getQualifiers().isEmpty()) {
                this.reportError(feature.getOrigin(), "FeatureQualifiersRequiredCheck", new Object[]{feature.getName()});
            }

            return this.result;
        }
    }

     */
}
