package uk.ac.ebi.embl.gff3tools.gff3;

import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class GFF3FileTest {

    @Test
    void testWriteTranslation() throws Exception {

        String expectedOutput = "##FASTA\n" +
                ">geneB\n" +
                "GGTTAA\n" +
                ">geneA\n" +
                "ATGC\n";

        // Inject cdsTranslationMap
        Map<String, String> testMap = new HashMap<>();
        testMap.put("geneA", "ATGC");
        testMap.put("geneB", "GGTTAA");

        GFF3File obj = new GFF3File(null,null,null,testMap,null);


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
