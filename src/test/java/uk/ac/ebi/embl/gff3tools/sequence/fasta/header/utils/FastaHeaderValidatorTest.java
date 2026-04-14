package uk.ac.ebi.embl.gff3tools.sequence.fasta.header.utils;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FastaHeaderValidatorTest {

    @Test
    void shouldPassValidation_whenValidHeader() {
        FastaHeader h = new FastaHeader();
        h.setDescription("Some description");
        h.setMoleculeType("DNA");
        h.setTopology("linear");
        h.setChromosomeType("chromosome");
        h.setChromosomeLocation("Mitochondrion");
        h.setChromosomeName("seq1_valid");

        FastaHeader h2 = new FastaHeader();
        h2.setDescription("Some description");
        h2.setMoleculeType("DNA");
        h2.setTopology("circular");

        List<String> errors = FastaHeaderValidator.validate(h);
        List<String> errors2 = FastaHeaderValidator.validate(h2);

        assertTrue(errors.isEmpty());
        assertTrue(errors2.isEmpty());
    }

    @Test
    void shouldFailValidation_whenHeaderNullOrEmpty() {
        FastaHeader h = new FastaHeader();

        List<String> errors = FastaHeaderValidator.validate(h);
        List<String> errors2 = FastaHeaderValidator.validate(null);

        assertFalse(errors.isEmpty());
        assertFalse(errors2.isEmpty());
    }

    @Test
    void shouldFail_whenMandatoryFieldsMissing() {
        FastaHeader h = new FastaHeader();

        List<String> errors = FastaHeaderValidator.validate(h);

        assertTrue(errors.contains("description is mandatory"));
        assertTrue(errors.contains("molecule_type is mandatory"));
        assertTrue(errors.contains("topology is mandatory"));
    }

    @Test
    void shouldFail_whenInvalidTopology() {
        FastaHeader h = validHeader();
        h.setTopology("triangle");

        List<String> errors = FastaHeaderValidator.validate(h);

        assertTrue(errors.contains("topology must be 'linear' or 'circular'"));
    }

    @Test
    void shouldFail_whenInvalidChromosomeType() {
        FastaHeader h = validHeader();
        h.setChromosomeType("invalid_type");

        List<String> errors = FastaHeaderValidator.validate(h);

        assertTrue(errors.contains("invalid chromosome_type - see allowed values list"));
    }

    @Test
    void shouldFail_whenInvalidChromosomeLocation() {
        FastaHeader h = validHeader();
        h.setChromosomeLocation("mars");

        List<String> errors = FastaHeaderValidator.validate(h);

        assertTrue(errors.contains("invalid chromosome_location - see allowed values list"));
    }

    @Test
    void shouldFail_whenInvalidChromosomeNamePattern() {
        FastaHeader h = validHeader();
        h.setChromosomeName("-badName");

        List<String> errors = FastaHeaderValidator.validate(h);

        assertTrue(errors.contains("invalid chromosome_name format"));
    }

    @Test
    void shouldFail_whenChromosomeNameTooLong() {
        FastaHeader h = validHeader();
        h.setChromosomeName("a".repeat(33));

        List<String> errors = FastaHeaderValidator.validate(h);

        assertTrue(errors.contains("chromosome_name must be shorter than 33 characters"));
    }

    @Test
    void shouldFail_whenChromosomeNameContainsForbiddenWord() {
        FastaHeader h = validHeader();
        h.setChromosomeName("myChromosome1");

        List<String> errors = FastaHeaderValidator.validate(h);

        assertTrue(errors.stream().anyMatch(e -> e.contains("forbidden term")));
    }

    @Test
    void shouldFail_whenHeaderIsNull() {
        List<String> errors = FastaHeaderValidator.validate(null);

        assertEquals(1, errors.size());
        assertEquals("FastaHeader must not be null", errors.get(0));
    }

    private FastaHeader validHeader() {
        FastaHeader h = new FastaHeader();
        h.setDescription("desc");
        h.setMoleculeType("DNA");
        h.setTopology("linear");
        return h;
    }
}
