package uk.ac.ebi.embl.gff3tools.gff3;

import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class GFF3FileTest {

    @Test
    void testWriteTranslation() throws Exception {

        String input = "##FASTA\n" +
                ">geneB\n" +
                "GGTTAA\n" +
                ">geneA\n" +
                "ATGC\n";
        String expectedOutput = "##FASTA\n" + input;


        // Inject cdsTranslationMap
        Map<String, String> testMap = new HashMap<>();
        testMap.put("geneA", "ATGC");
        testMap.put("geneB", "GGTTAA");

        Files.writeString(Path.of("translation.fasta"),input, Charset.defaultCharset());
        GFF3File obj = new GFF3File(null,null,null, Path.of("translation.fasta"),null);


        // obj.cdsTranslationMap = testMap;

        StringWriter writer = new StringWriter();

        // Access private method via reflection
        Method method = GFF3File.class.getDeclaredMethod("writeTranslation", Writer.class);
        method.setAccessible(true);

        // call method
        method.invoke(obj, writer);

        // Assert
        String output = writer.toString();
        assertEquals(expectedOutput, output);
    }
}
