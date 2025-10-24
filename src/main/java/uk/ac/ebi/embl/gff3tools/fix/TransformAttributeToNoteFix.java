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
package uk.ac.ebi.embl.gff3tools.fix;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.validation.meta.FixMethod;
import uk.ac.ebi.embl.gff3tools.validation.meta.Gff3Fix;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationType;

@Gff3Fix(
        name = "TransformExclusiveAttributeToNote",
        description = "Moves the value one of the mutually exclusive feature attributes to the note attribute",
        enabled = false)
public class TransformAttributeToNoteFix {

    List<ExclusiveAttributePair> pairs = new ArrayList<ExclusiveAttributePair>();
    private final String note = GFF3Attributes.NOTE;

    public TransformAttributeToNoteFix() {
        pairs.add(new ExclusiveAttributePair(GFF3Attributes.PRODUCT, GFF3Attributes.PSEUDO));
        pairs.add(new ExclusiveAttributePair(GFF3Attributes.PRODUCT, GFF3Attributes.PSEUDOGENE));
    }

    @FixMethod(
            rule = "TransformExclusiveAttributeToNote",
            type = ValidationType.FEATURE,
            description = "Moves the value one of the mutually exclusive feature attributes to the note attribute",
            enabled = false)
    public void fix(GFF3Feature feature) {

        if (feature == null
                || feature.getAttributes() == null
                || feature.getAttributes().isEmpty()) return;

        for (ExclusiveAttributePair pair : pairs) {
            if (feature.containsAttribute(pair.toRemove) && feature.containsAttribute(pair.exclusive)) {
                String valueToAppend = feature.getAttributeByName(pair.toRemove);
                if (!valueToAppend.isEmpty()) {
                    appendToNote(feature, valueToAppend);
                }
                feature.removeAttribute(pair.toRemove);
            }
        }
    }

    private void appendToNote(GFF3Feature feature, String valueToAppend) {
        String current = feature.getAttributeByName(note);
        if (current == null) {
            feature.setAttribute(note, valueToAppend);
            return;
        }
        feature.setAttribute(note, current + "," + valueToAppend);
    }

    public static class ExclusiveAttributePair {
        public final String toRemove;
        public final String exclusive;

        public ExclusiveAttributePair(String toRemove, String exclusive) {
            this.toRemove = Objects.requireNonNull(toRemove);
            this.exclusive = Objects.requireNonNull(exclusive);
        }
    }
}
