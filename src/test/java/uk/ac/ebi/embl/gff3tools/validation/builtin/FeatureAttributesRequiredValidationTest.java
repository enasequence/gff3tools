package uk.ac.ebi.embl.gff3tools.validation.builtin;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.gff3tools.TestUtils;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Anthology;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;

import java.util.HashMap;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class FeatureAttributesRequiredValidationTest {

    private FeatureAttributesRequiredValidation attributesRequiredValidation;

    @BeforeEach
    public void setUp() { attributesRequiredValidation = new FeatureAttributesRequiredValidation(); }


    public static <E> E getRandomEntryFromSet(Set<String> set) {
        Objects.requireNonNull(set, "set");
        if (set.isEmpty()) throw new NoSuchElementException("empty set");
        Object[] a = set.toArray();
        return (E) a[ThreadLocalRandom.current().nextInt(a.length)];
    }

    @Test
    public void testFeatureAttriuteRequiredValidationSuccess() {
        String featureName = getRandomEntryFromSet(GFF3Anthology.ATTRIBUTES_REQUIRED_FEATURE_SET);
        GFF3Feature feature = TestUtils.createGFF3Feature(featureName, ".", new HashMap<>(){{
            put("attributeKey", "attributeValue");
        }});

        Assertions.assertDoesNotThrow(() -> attributesRequiredValidation.validateFeature(feature, 1));
    }

    @Test
    public void testFeatureAttriuteRequiredValidationFailure() {
        String featureName = getRandomEntryFromSet(GFF3Anthology.ATTRIBUTES_REQUIRED_FEATURE_SET);
        GFF3Feature feature = TestUtils.createGFF3Feature(featureName, new HashMap<>());

        Assertions.assertThrows(ValidationException.class, () -> attributesRequiredValidation.validateFeature(feature, 1));
    }
}
