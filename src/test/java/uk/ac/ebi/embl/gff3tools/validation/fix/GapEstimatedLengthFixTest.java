package uk.ac.ebi.embl.gff3tools.validation.fix;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.utils.ConversionUtils;
import uk.ac.ebi.embl.gff3tools.utils.OntologyClient;
import uk.ac.ebi.embl.gff3tools.utils.OntologyTerm;

import java.util.Optional;


import static org.mockito.Mockito.*;

class GapEstimatedLengthFixTest {

    private GapEstimatedLengthFix fix;
    private OntologyClient ontologyClient;

    @BeforeEach
    void setUp() {
        ontologyClient = mock(OntologyClient.class);

        try (MockedStatic<ConversionUtils> mocked = mockStatic(ConversionUtils.class)) {
            mocked.when(ConversionUtils::getOntologyClient).thenReturn(ontologyClient);
            fix = new GapEstimatedLengthFix();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"gap", "assembly_gap"})
    void shouldAddEstimatedLengthForGapAndAssemblyGap(String featureName) {
        GFF3Feature feature = mock(GFF3Feature.class);

        when(feature.getName()).thenReturn(featureName);
        when(feature.hasAttribute(GFF3Attributes.ESTIMATED_LENGTH)).thenReturn(false);
        when(feature.getLength()).thenReturn(150L);
        when(ontologyClient.findTermByNameOrSynonym(featureName))
                .thenReturn(Optional.of(OntologyTerm.GAP.ID));

        fix.fixFeature(feature, 12);

        verify(feature)
                .addAttribute(GFF3Attributes.ESTIMATED_LENGTH, "150");
    }

    @Test
    void shouldNotAddEstimatedLengthIfAlreadyPresent() {
        GFF3Feature feature = mock(GFF3Feature.class);

        when(feature.getName()).thenReturn("gap");
        when(feature.hasAttribute(GFF3Attributes.ESTIMATED_LENGTH)).thenReturn(true);
        when(ontologyClient.findTermByNameOrSynonym("gap"))
                .thenReturn(Optional.of(OntologyTerm.GAP.ID));

        fix.fixFeature(feature, 5);

        verify(feature, never())
                .addAttribute(eq(GFF3Attributes.ESTIMATED_LENGTH), anyString());
    }

    @Test
    void shouldNotAddEstimatedLengthForNonGapFeature() {
        GFF3Feature feature = mock(GFF3Feature.class);

        when(feature.getName()).thenReturn("exon");
        when(ontologyClient.findTermByNameOrSynonym("exon"))
                .thenReturn(Optional.of("SO:0000147"));

        fix.fixFeature(feature, 3);

        verify(feature, never())
                .addAttribute(eq(GFF3Attributes.ESTIMATED_LENGTH), anyString());
    }

    @Test
    void shouldDoNothingIfOntologyTermNotFound() {
        GFF3Feature feature = mock(GFF3Feature.class);

        when(feature.getName()).thenReturn("gap");
        when(ontologyClient.findTermByNameOrSynonym("gap"))
                .thenReturn(Optional.empty());

        fix.fixFeature(feature, 1);

        verify(feature, never())
                .addAttribute(anyString(), anyString());
    }
}
