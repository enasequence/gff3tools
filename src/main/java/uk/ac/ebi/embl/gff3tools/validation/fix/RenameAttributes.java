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
        name = "RENAME_ATTRIBUTES",
        description = "Moves 'label' into 'Note' and renames 'mobile_element' to 'mobile_element_type'")
public class RenameAttributes {

    @FixMethod(
            rule = "RENAME_ATTRIBUTES",
            description = "Moves 'label' into 'Note' and renames 'mobile_element' to 'mobile_element_type'",
            type = FEATURE)
    public void fixFeature(GFF3Feature feature, int line) {

        String label = feature.getAttributeByName(GFF3Attributes.LABEL);
        String mobileElement = feature.getAttributeByName(GFF3Attributes.MOBILE_ELEMENT);
        if (label != null) {
            feature.removeAttribute(GFF3Attributes.LABEL);
            String noteValue = feature.getAttributeByName(GFF3Attributes.NOTE);
            String newNote;
            if (noteValue != null) {
                newNote = noteValue.trim() + ";label:" + label;
            } else {
                newNote = "label:" + label.trim();
            }
            feature.setAttribute(GFF3Attributes.NOTE, newNote);
            log.info(
                    "Moving {} attribute value to {} attribute at line: {}",
                    GFF3Attributes.LABEL,
                    GFF3Attributes.NOTE,
                    line);
        }
        if (mobileElement != null) {
            feature.removeAttribute(GFF3Attributes.MOBILE_ELEMENT);
            feature.setAttribute(GFF3Attributes.MOBILE_ELEMENT_TYPE, mobileElement);
            log.info(
                    "Moving {} attribute value to {} attribute at line: {}",
                    GFF3Attributes.MOBILE_ELEMENT,
                    GFF3Attributes.MOBILE_ELEMENT_TYPE,
                    line);
        }
    }
}
