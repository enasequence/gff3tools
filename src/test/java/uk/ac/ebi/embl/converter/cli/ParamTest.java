package uk.ac.ebi.embl.converter.cli;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.*;

public class ParamTest {

    @Test
    void testValidArgs() {
        String[] args = "-in inFile -out outFile".split(" ");

        Params params = Params.parse(args);
        assertEquals(params.inFile.getName(),"inFile");
        assertEquals(params.outFile.getName(),"outFile");
    }

    @Test
    void testInvalidArgs() {
        String[] args = {};

        Exception exception = assertThrows(CommandLine.MissingParameterException.class, () -> {
            Params.parse(args);
        });
        assertTrue(exception.getMessage().contains("Missing required options"));
    }
}
