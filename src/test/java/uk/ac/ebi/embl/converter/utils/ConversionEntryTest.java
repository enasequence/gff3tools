package uk.ac.ebi.embl.converter.utils;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.api.entry.feature.Feature;
import uk.ac.ebi.embl.api.entry.feature.FeatureFactory;
import uk.ac.ebi.embl.converter.fftogff3.GFF3AnnotationFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ConversionEntryTest {

    @Test
    public void testParseQualifier() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method method = ConversionEntry.class.getDeclaredMethod("parseQualifier", String.class);
        method.setAccessible(true);

        ConversionEntry entry = new ConversionEntry(new String[] {"id", "term", "definition", "feature"});

        Object res1 = method.invoke(entry, "/q1=\"q1value\"");
        assertEquals(new Tuple2<>("q1", "q1value"), res1);

        Object res2 = method.invoke(entry, "/q1");
        assertEquals(new Tuple2<>("q1", "true"), res2);
    }
}
