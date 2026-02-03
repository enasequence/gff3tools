/*
 * Copyright 2025 EMBL - European Bioinformatics Institute
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package uk.ac.ebi.embl.gff3tools.validation.fix;

import static org.mockito.Mockito.*;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.utils.ConversionUtils;
import uk.ac.ebi.embl.gff3tools.utils.OntologyClient;
import uk.ac.ebi.embl.gff3tools.utils.OntologyTerm;

class RepeatRegionRptTypeFixTest {

    private RepeatRegionRptTypeFix fix;
    private OntologyClient ontologyClient;

    @BeforeEach
    void setUp() {
        ontologyClient = mock(OntologyClient.class);

        try (MockedStatic<ConversionUtils> mocked = mockStatic(ConversionUtils.class)) {
            mocked.when(ConversionUtils::getOntologyClient).thenReturn(ontologyClient);
            fix = new RepeatRegionRptTypeFix();
        }
    }

    @Test
    void shouldAddRptTypeOtherForRepeatRegionWithoutQualifiers() {
        GFF3Feature feature = mock(GFF3Feature.class);

        when(feature.getName()).thenReturn("repeat_region");
        when(feature.hasAttribute(GFF3Attributes.RPT_TYPE)).thenReturn(false);
        when(feature.hasAttribute(GFF3Attributes.RPT_FAMILY)).thenReturn(false);
        when(feature.hasAttribute(GFF3Attributes.SATELLITE)).thenReturn(false);
        when(ontologyClient.findTermByNameOrSynonym("repeat_region")).thenReturn(Optional.of(OntologyTerm.REPEAT_REGION.ID));

        fix.fixFeature(feature, 42);

        verify(feature).addAttribute(GFF3Attributes.RPT_TYPE, "other");
    }

    @Test
    void shouldNotAddRptTypeIfRptTypeAlreadyPresent() {
        GFF3Feature feature = mock(GFF3Feature.class);

        when(feature.getName()).thenReturn("repeat_region");
        when(feature.hasAttribute(GFF3Attributes.RPT_TYPE)).thenReturn(true);
        when(ontologyClient.findTermByNameOrSynonym("repeat_region")).thenReturn(Optional.of(OntologyTerm.REPEAT_REGION.ID));

        fix.fixFeature(feature, 10);

        verify(feature, never()).addAttribute(eq(GFF3Attributes.RPT_TYPE), anyString());
    }

    @Test
    void shouldNotAddRptTypeIfRptFamilyPresent() {
        GFF3Feature feature = mock(GFF3Feature.class);

        when(feature.getName()).thenReturn("repeat_region");
        when(feature.hasAttribute(GFF3Attributes.RPT_TYPE)).thenReturn(false);
        when(feature.hasAttribute(GFF3Attributes.RPT_FAMILY)).thenReturn(true);
        when(ontologyClient.findTermByNameOrSynonym("repeat_region")).thenReturn(Optional.of(OntologyTerm.REPEAT_REGION.ID));

        fix.fixFeature(feature, 11);

        verify(feature, never()).addAttribute(eq(GFF3Attributes.RPT_TYPE), anyString());
    }

    @Test
    void shouldNotAddRptTypeIfSatellitePresent() {
        GFF3Feature feature = mock(GFF3Feature.class);

        when(feature.getName()).thenReturn("repeat_region");
        when(feature.hasAttribute(GFF3Attributes.RPT_TYPE)).thenReturn(false);
        when(feature.hasAttribute(GFF3Attributes.RPT_FAMILY)).thenReturn(false);
        when(feature.hasAttribute(GFF3Attributes.SATELLITE)).thenReturn(true);
        when(ontologyClient.findTermByNameOrSynonym("repeat_region")).thenReturn(Optional.of(OntologyTerm.REPEAT_REGION.ID));

        fix.fixFeature(feature, 12);

        verify(feature, never()).addAttribute(eq(GFF3Attributes.RPT_TYPE), anyString());
    }

    @Test
    void shouldNotAddRptTypeForNonRepeatRegionFeature() {
        GFF3Feature feature = mock(GFF3Feature.class);

        when(feature.getName()).thenReturn("tandem_repeat");
        when(ontologyClient.findTermByNameOrSynonym("tandem_repeat")).thenReturn(Optional.of("SO:0000705"));

        fix.fixFeature(feature, 5);

        verify(feature, never()).addAttribute(eq(GFF3Attributes.RPT_TYPE), anyString());
    }

    @Test
    void shouldNotAddRptTypeForGeneFeature() {
        GFF3Feature feature = mock(GFF3Feature.class);

        when(feature.getName()).thenReturn("gene");
        when(ontologyClient.findTermByNameOrSynonym("gene")).thenReturn(Optional.of(OntologyTerm.GENE.ID));

        fix.fixFeature(feature, 3);

        verify(feature, never()).addAttribute(eq(GFF3Attributes.RPT_TYPE), anyString());
    }

    @Test
    void shouldDoNothingIfOntologyTermNotFound() {
        GFF3Feature feature = mock(GFF3Feature.class);

        when(feature.getName()).thenReturn("unknown_feature");
        when(ontologyClient.findTermByNameOrSynonym("unknown_feature")).thenReturn(Optional.empty());

        fix.fixFeature(feature, 1);

        verify(feature, never()).addAttribute(anyString(), anyString());
    }
}
