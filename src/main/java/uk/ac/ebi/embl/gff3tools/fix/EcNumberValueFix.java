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

import static uk.ac.ebi.embl.gff3tools.validation.meta.ValidationType.FEATURE;

import java.util.regex.Pattern;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.validation.meta.FixMethod;
import uk.ac.ebi.embl.gff3tools.validation.meta.Gff3Fix;

@Gff3Fix
public class EcNumberValueFix {

    private static final Pattern EC_NUMBER_PATTERN =
            Pattern.compile("^[0-9]+(\\.(?:[0-9]+|-)){0,2}\\.(?:[0-9]+|-|n[0-9]*)$");

    @FixMethod(type = FEATURE)
    public void fixFeature(GFF3Feature feature) {
        String ecNumber = feature.getAttributeByName(GFF3Attributes.EC_NUMBER);
        if (ecNumber == null || ecNumber.isBlank()) return;

        if (ecNumber.equalsIgnoreCase("deleted") || !isValidECNumber(ecNumber.trim())) {
            feature.removeAttribute(GFF3Attributes.EC_NUMBER);
        }
    }

    private boolean isValidECNumber(String ecNumber) {
        return EC_NUMBER_PATTERN.matcher(ecNumber).matches();
    }
}
