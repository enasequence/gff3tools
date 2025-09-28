
package uk.ac.ebi.embl.gff3tools.gff3.reader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import uk.ac.ebi.embl.gff3tools.TestUtils;
import uk.ac.ebi.embl.gff3tools.exception.*;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3File;
import uk.ac.ebi.embl.gff3tools.gff3.directives.*;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngine;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class GFF3FileReaderTest {

    private ValidationEngine validationEngine;

    @BeforeEach
    void setUp() throws UnregisteredValidationRuleException {
        validationEngine = TestUtils.initValidationEngine(new HashMap<>());
    }

    @Test
    void testReadHeader_validVersion() throws Exception {
        String input = "##gff-version 3.1\n";
        try (GFF3FileReader reader = new GFF3FileReader(validationEngine, new StringReader(input))) {
            GFF3Header header = reader.readHeader();
            assertNotNull(header);
            assertEquals("3.1", header.version());
        }
    }

    @Test
    void testReadHeader_invalidLine_callsValidation() throws Exception {
        String input = "not-a-header\n";
        try (GFF3FileReader reader = new GFF3FileReader(validationEngine, new StringReader(input))) {
            ValidationException exception = assertThrows(ValidationException.class, () -> reader.readHeader());
            assertTrue(exception.getMessage().contains("Violation of rule GFF3_INVALID_HEADER on line 1: Invalid gff3 header \"not-a-header\""));
        }
    }

   /* @Test
    void testReadSpecies_validSpecies() throws Exception {
        String input = "##species http://example.org?name=Homo sapiens\n";
        try (GFF3FileReader reader = new GFF3FileReader(validationEngine, new StringReader(input))) {
            GFF3Species species = reader.getSpecies();
            assertNotNull(species);
            assertEquals("http://example.org?name=Homo sapiens", species.species());
        }
    }*/

    @Test
    void testReadSpecies_unexpectedLine_returnsNullAndPushback() throws Exception {
        String input = "##gff-version 3\n";
        try (GFF3FileReader reader = new GFF3FileReader(validationEngine, new StringReader(input))) {
            GFF3Species species = reader.getSpecies();
            assertNull(species);
            // The line should have been unread (pushed back), so next readHeader can still parse it
            GFF3Header header = reader.readHeader();
            assertNotNull(header);
            assertEquals("3", header.version());
        }
    }

    @Test
    void testAttributesFromString_singleValue() throws IOException {
        try (GFF3FileReader reader = new GFF3FileReader(validationEngine, new StringReader(""))) {
            Map<String, Object> attrs = reader.attributesFromString("ID=gene0001;note=note1,note2");
            assertTrue(attrs.containsKey("ID"));
            assertEquals("gene0001", attrs.get("ID"));
            assertTrue(attrs.containsKey("note"));
            assertEquals("note1", ((java.util.List<?>) attrs.get("note")).get(0));
            assertEquals("note2", ((java.util.List<?>) attrs.get("note")).get(1));
        }
    }

    @Test
    void testReadGff3Entry_callsHandler() throws Exception {
        // Minimal valid GFF3 input with sequence-region, feature, and resolution directive
        String input = "##gff-version 3\n" +
                "##sequence-region seq1 1 100\n" +
                "seq1\tsource\tgene\t1\t10\t.\t+\t.\tID=gene1\n" +
                "###\n";

       /* try (GFF3FileReader reader = new GFF3FileReader(validationEngine, new StringReader(input))) {
            final boolean[] called = {false};
            reader.readGff3Entry(annotation -> {
                assertNotNull(annotation);
                assertEquals("seq1", annotation.getAccession());
                called[0] = true;
            });
            assertTrue(called[0], "Annotation handler should have been called");
        }*/
    }

    @Test
    void testReadSpecies_noSpecies() throws Exception {
        // GFF3 with species
       /* String input = "##gff-version 3\n"+
                "##species http://example.org?name=Homo sapiens\n"+
                "##sequence-region BN000065.1 1 315242\n" +
                "BN000065.1\t.\tgene\t1\t315242\t.\t+\t.\tID=gene_RHD;gene=RHD;\n\n"+
                "##gff-version 3\n"+
                "##species http://example.org?name=Homo sapiens\n"+
                "##sequence-region BN000066.1 1 315242\n" +
                "BN000066.1\t.\tgene\t1\t315242\t.\t+\t.\tID=gene_RHD;gene=RHD;\n\n";
        String output = testRead(input);
        assertEquals(input,output);

        // GFF3 with out species
        input = "##gff-version 3\n"+
                "##sequence-region BN000065.1 1 315242\n" +
                "BN000065.1\t.\tgene\t1\t315242\t.\t+\t.\tID=gene_RHD;gene=RHD;\n\n";
        output = testRead(input);
        assertEquals(output, input);


          input = "##gff-version 3\n"+
                "##species http://example.org?name=Homo sapiens\n"+
                "##sequence-region BN000065.1 1 315242\n\n" +
                "##gff-version 3\n"+
                "##species http://example.org?name=Homo sapiens\n"+
                "##sequence-region BN000066.1 1 315242\n\n" ;
          output = testRead(input);*/


         String input = "##gff-version 3\n" +
                 "##sequence-region seq0 1 100\n" +
                 "##gff-version 3\n"
                + "##sequence-region seq1 1 200\n"
                 + "seq1\tsource\tfeature1\t1\t100\t.\t+\t.\tID=feata1;\n\n"
                 + "##gff-version 3\n"
                 + "##sequence-region seq2 1 200\n"
                 + "seq2\tsource\tfeature1\t1\t100\t.\t+\t.\tID=featb1;\n"
                 + "seq2\tsource\tfeature2\t1\t100\t.\t+\t.\tID=featb2;\n\n"
                 + "##gff-version 3\n"
                 + "##sequence-region seq3 1 200\n"
                 + "seq3\tsource\tfeature2\t1\t100\t.\t+\t.\tID=featb2;\n\n"
                 + "##gff-version 3\n"
                 + "##sequence-region seq4 1 200\n\n"
                 + "##gff-version 3\n"
                 + "##sequence-region seq5 1 200\n"
                 + "seq5\tsource\tfeature2\t1\t100\t.\t+\t.\tID=featb2;\n"
                 + "seq5\tsource\tfeature2\t1\t100\t.\t+\t.\tID=featb2;\n"
                 + "###\n"
                 + "seq5\tsource\tfeature2\t1\t100\t.\t+\t.\tID=featb2;\n\n"
                 + "##gff-version 3\n"
                 + "##sequence-region seq6 1 200\n\n"
                 + "##gff-version 3\n"
                 + "##sequence-region seq7 1 200\n\n";



        String output = testRead(input);
        assertEquals(input,output);


    }

    private String testRead(String input) throws IOException, ValidationException, ReadException, WriteException {
        StringWriter writer = new StringWriter();
        try (GFF3FileReader reader = new GFF3FileReader(validationEngine, new StringReader(input))) {
            GFF3Header gff3Header = reader.readHeader();



           /* reader.readGff3Entry(annotation -> {
                //assertNotNull(annotation);
                GFF3Species gff3Species = reader.getSpecies();
                GFF3File gff3File = new GFF3File(gff3Header, gff3Species, Arrays.asList(annotation),null);
                gff3File.writeGFF3String(writer);
            });*/

            /*reader.readEntryX(entry -> {
                assertNotNull(entry);
                GFF3Species gff3Species = entry.getSpecies();
                GFF3File gff3File = new GFF3File(gff3Header, gff3Species, Arrays.asList(entry.getAnnotation()),null);
                gff3File.writeGFF3String(writer);
            });*/

            AtomicBoolean first = new AtomicBoolean(true);

            reader.readAnnotation(annotation -> {
                //assertNotNull(annotation);
                //GFF3Species gff3Species = reader.getSpecies();
                //GFF3File gff3File = new GFF3File(gff3Header, gff3Species, Arrays.asList(annotation),null);
                //gff3File.writeGFF3String(writer);

                if (first.getAndSet(false)) {
                    // first annotation → write header + species only once
                    GFF3Species gff3Species = reader.getSpecies();
                    GFF3File gff3File = new GFF3File(gff3Header, gff3Species,
                            Arrays.asList(annotation), null);
                    gff3File.writeGFF3String(writer);
                } else {
                    // subsequent annotations → only write features
                    GFF3File gff3File = new GFF3File(null, null,
                            Arrays.asList(annotation), null);
                    gff3File.writeGFF3String(writer);
                }
            });
        }
        return writer.toString();
    }
}

