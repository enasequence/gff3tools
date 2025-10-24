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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import uk.ac.ebi.embl.gff3tools.fftogff3.FeatureMapping;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Anthology;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.validation.meta.Gff3Fix;

@Gff3Fix(
        name = "TransformAttributeToNote",
        description = "Moves the value one of the mutually exclusive feature attributes to the note attribute",
        enabled = false)
public class TransformAttributeToNoteFix {

    private static final String REMOVE_ATTRIBUTE_MESSAGE_ID = "ExclusiveAttributeTransformToNoteAttributeFix";

    List<ExclusiveAttributePair> pairs = new ArrayList<ExclusiveAttributePair>();
    private String note;

    public TransformAttributeToNoteFix() {
        String product = FeatureMapping.getGFF3Attribute(GFF3Anthology.FF_PRODUCT_QUALIFIER)
                .orElse(GFF3Anthology.FF_PRODUCT_QUALIFIER);
        String pseudo = FeatureMapping.getGFF3Attribute(GFF3Anthology.FF_PSEUDO_QUALIFIER)
                .orElse(GFF3Anthology.FF_PSEUDO_QUALIFIER);
        String pseudogene = FeatureMapping.getGFF3Attribute(GFF3Anthology.FF_PSEUDOGENE_QUALIFIER)
                .orElse(GFF3Anthology.FF_PSEUDOGENE_QUALIFIER);
        note = FeatureMapping.getGFF3Attribute(GFF3Anthology.FF_NOTE_QUALIFIER).orElse(GFF3Anthology.FF_NOTE_QUALIFIER);

        pairs.add(new ExclusiveAttributePair(product, pseudo));
        pairs.add(new ExclusiveAttributePair(product, pseudogene));
    }

    // Pair of (attributeToRemove, exclusiveAttribute)
    public static class ExclusiveAttributePair {
        public final String toRemove;
        public final String exclusive;

        public ExclusiveAttributePair(String toRemove, String exclusive) {
            this.toRemove = Objects.requireNonNull(toRemove);
            this.exclusive = Objects.requireNonNull(exclusive);
        }
    }

    /**
     * Applies the transformation to a single GFF3Feature:
     * For each (toRemove, exclusive) pair:
     *   if feature has both attributes, move the toRemove value(s) into Note and delete toRemove.
     * Returns the list of messages indicating what was changed.
     */
    public void fix(GFF3Feature feature) {

        if (feature == null
                || feature.getAttributes() == null
                || feature.getAttributes().isEmpty()) return;

        for (ExclusiveAttributePair pair : pairs) {
            if (feature.attributesContainsKey(pair.toRemove) && feature.attributesContainsKey(pair.exclusive)) {

                String valueToAppend = feature.getAttributeByName(pair.toRemove);
                if (!valueToAppend.isEmpty()) {
                    appendToNote(feature, valueToAppend);
                }

                // Remove the source attribute entirely
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

        feature.setAttribute(note, current + ";" + valueToAppend);
    }
}
