package uk.ac.ebi.embl.converter.cli;

import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.api.entry.feature.Feature;
import uk.ac.ebi.embl.api.entry.feature.FeatureFactory;
import uk.ac.ebi.embl.converter.fftogff3.GFF3AnnotationFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MainTest {
    @Test
    public void testValidateFileType()
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        CommandConversion command = new CommandConversion();
        Method method = CommandConversion.class.getDeclaredMethod("validateFileType", CommandConversion.FileFormat.class, Path.class, String.class);
        method.setAccessible(true);

        Object validate = null;

        validate = method.invoke(command, CommandConversion.FileFormat.ff, Path.of("foo"), "-f");
        assertEquals(CommandConversion.FileFormat.ff, validate, "If format is provided returns the same format");

        validate = method.invoke(command, CommandConversion.FileFormat.gff3, Path.of("foo"), "-f");
        assertEquals(CommandConversion.FileFormat.gff3, validate, "If format is provided returns the same format");

        validate = method.invoke(command, null, Path.of("foo.ff"), "-f");
        assertEquals(CommandConversion.FileFormat.ff, validate, "If format in path extension, use it");

        validate = method.invoke(command, null, Path.of("foo.gff3"), "-f");
        assertEquals(CommandConversion.FileFormat.gff3, validate, "If format in path extension, use it");

        try {
            validate = method.invoke(command, null, Path.of("foo.embl"), "-f");
        } catch (InvocationTargetException e) {
            assertTrue(e.getTargetException().getMessage().contains("-f"), "If no format matches extension, show error with options");
        }

        try {
            validate = method.invoke(command, null, Path.of("foo.embl"), "-t");
        } catch (InvocationTargetException e) {
            assertTrue(e.getTargetException().getMessage().contains("-t"), "If no format matches extension, show error with options");
        }

    }
}
