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

import static uk.ac.ebi.embl.gff3tools.validation.meta.ValidationType.FEATURE;

import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.utils.ConversionUtils;
import uk.ac.ebi.embl.gff3tools.utils.OntologyClient;
import uk.ac.ebi.embl.gff3tools.utils.OntologyTerm;
import uk.ac.ebi.embl.gff3tools.validation.meta.FixMethod;
import uk.ac.ebi.embl.gff3tools.validation.meta.Gff3Fix;

@Slf4j
@Gff3Fix(
        name = "REPEAT_REGION_RPT_TYPE",
        description = "Add rpt_type=\"other\" to repeat_region features missing rpt_type, rpt_family, or satellite")
public class RepeatRegionRptTypeFix {

    private final OntologyClient ontologyClient = ConversionUtils.getOntologyClient();

    @FixMethod(
            rule = "REPEAT_REGION_RPT_TYPE",
            description = "Add rpt_type=\"other\" to repeat_region features",
            type = FEATURE)
    public void fixFeature(GFF3Feature feature, int line) {
        Optional<String> soIdOpt = ontologyClient.findTermByNameOrSynonym(feature.getName());
        if (soIdOpt.isEmpty()) {
            return;
        }

        // Only apply to repeat_region (SO:0000657)
        if (!OntologyTerm.REPEAT_REGION.ID.equals(soIdOpt.get())) {
            return;
        }

        // Skip if any of the repeat qualifiers are already present
        if (feature.hasAttribute(GFF3Attributes.RPT_TYPE)
                || feature.hasAttribute(GFF3Attributes.RPT_FAMILY)
                || feature.hasAttribute(GFF3Attributes.SATELLITE)) {
            return;
        }

        feature.addAttribute(GFF3Attributes.RPT_TYPE, "other");
        log.info("Adding rpt_type=\"other\" for repeat_region feature at line: {}", line);
    }
}
