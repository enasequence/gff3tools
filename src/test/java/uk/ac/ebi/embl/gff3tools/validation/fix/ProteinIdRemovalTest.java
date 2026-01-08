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

import static uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes.LOCUS_TAG;
import static uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes.PROTEIN_ID;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.gff3tools.TestUtils;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.utils.OntologyTerm;

public class ProteinIdRemovalTest {

    private ProteinIdRemoval proteinIdRemoval;

    @BeforeEach
    public void setUp() {
        proteinIdRemoval = new ProteinIdRemoval();
    }

    @Test
    public void testProteinIdRemovalSuccess() {
        Map<String, List<String>> attr = new HashMap<>();
        attr.put(PROTEIN_ID, List.of("protein1"));
        GFF3Feature f1 = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), OntologyTerm.CDS.name(), attr);
        proteinIdRemoval.fix(f1, 1);
        Assertions.assertTrue(f1.getAttribute(PROTEIN_ID).isEmpty());
        Assertions.assertFalse(f1.getAttribute(PROTEIN_ID).isPresent());
    }

    @Test
    public void testProteinIdRemovalWithoutProteinId() {
        Map<String, List<String>> attr = new HashMap<>();
        attr.put(LOCUS_TAG, List.of("LOCUS_TAG"));

        GFF3Feature f1 = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), OntologyTerm.CDS.name(), attr);

        proteinIdRemoval.fix(f1, 1);
        Assertions.assertTrue(f1.getAttribute(LOCUS_TAG).isPresent());
        Assertions.assertFalse(f1.getAttribute(LOCUS_TAG).get().isEmpty());
    }
}
