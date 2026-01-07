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

import lombok.extern.slf4j.Slf4j;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.validation.meta.FixMethod;
import uk.ac.ebi.embl.gff3tools.validation.meta.Gff3Fix;

@Slf4j
@Gff3Fix(
        name = "ATTRIBUTES_VALUE",
        description = "Change the attribute value of mod_base. Refer: Modified base abbreviations")
public class AttributeValueFix {

    @FixMethod(
            rule = "ATTRIBUTES_VALUE",
            description = "Change the attribute value of mod_base. Refer: Modified base abbreviations",
            type = FEATURE)
    public void fixFeature(GFF3Feature feature, int line) {
        String modBaseValue = feature.getAttribute(GFF3Attributes.MOD_BASE).orElse(null);
        if (modBaseValue != null && modBaseValue.trim().equalsIgnoreCase("d")) {
            log.info("Updating value from  'd' to 'dhu' on {} attribute at line: {}", GFF3Attributes.MOD_BASE, line);
            feature.removeAttributeList(GFF3Attributes.MOD_BASE);
            feature.addAttribute(GFF3Attributes.MOD_BASE, "dhu");
        }
    }
}
