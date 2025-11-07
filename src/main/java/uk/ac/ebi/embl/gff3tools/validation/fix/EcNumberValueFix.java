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

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.validation.meta.FixMethod;
import uk.ac.ebi.embl.gff3tools.validation.meta.Gff3Fix;

@Slf4j
@Gff3Fix(name = "EC_NUMBER", description = "Remove the EC_number attribute if not matches the pattern")
public class EcNumberValueFix {

    private static final Pattern EC_NUMBER_PATTERN =
            Pattern.compile("^[0-9]+(\\.(?:[0-9]+|-)){0,2}\\.(?:[0-9]+|-|n[0-9]*)$");
    private static final Pattern PRODUCT_EC_NUMBER_PATTERN = Pattern.compile(
            "\\(?\\[?(?:EC|ec|Ec)[:=]?\\s*((?:\\d{1,3}|-)\\.(?:\\d{1,3}|-)\\.(?:\\d{1,3}|-)\\.(?:\\d{1,3}|-))]?\\)?");

    @FixMethod(
            rule = "EC_NUMBER",
            description = "Remove the EC_number attribute if not matches the pattern",
            type = FEATURE)
    public void fixFeature(GFF3Feature feature, int line) {

        if (!feature.hasAttribute(GFF3Attributes.EC_NUMBER) && !feature.hasAttribute(GFF3Attributes.PRODUCT)) {
            return;
        }

        String ecNumber = feature.getAttributeByName(GFF3Attributes.EC_NUMBER);
        String product = feature.getAttributeByName(GFF3Attributes.PRODUCT);

        if (ecNumber != null) {
            if ("deleted".equalsIgnoreCase(ecNumber) || !isValidECNumber(ecNumber.trim())) {
                log.info(
                        "Fix to remove {} attribute due to invalid value '{}' at line: {}",
                        GFF3Attributes.EC_NUMBER,
                        ecNumber,
                        line);
                feature.removeAttribute(GFF3Attributes.EC_NUMBER);
                return;
            }
        }

        if ("hypothetical protein".equalsIgnoreCase(product) || "unknown".equalsIgnoreCase(product)) {
            log.info(
                    "Fix to remove {} attribute due to invalid product '{}' at line: {}",
                    GFF3Attributes.EC_NUMBER,
                    product,
                    line);
            feature.removeAttribute(GFF3Attributes.EC_NUMBER);
            return;
        }

        if (product != null && hasEcNumber(product)) {
            String ecNumberFromProduct = getEcNumberFromProduct(product);
            if (!ecNumberFromProduct.isEmpty() && isValidECNumber(ecNumberFromProduct)) {
                log.info(
                        "Fix to add {} attribute extracted from product '{}' at line: {}",
                        GFF3Attributes.EC_NUMBER,
                        product,
                        line);
                feature.setAttribute(GFF3Attributes.EC_NUMBER, ecNumberFromProduct);
            } else {
                log.info(
                        "Fix to remove {} attribute due to invalid product '{}' at line: {}",
                        GFF3Attributes.EC_NUMBER,
                        product,
                        line);
                feature.removeAttribute(GFF3Attributes.EC_NUMBER);
            }
        }
    }

    private String getEcNumberFromProduct(String product) {
        Matcher matcher = PRODUCT_EC_NUMBER_PATTERN.matcher(product);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return "";
    }

    private boolean hasEcNumber(String product) {
        return PRODUCT_EC_NUMBER_PATTERN.matcher(product).find();
    }

    private boolean isValidECNumber(String ecNumber) {
        return EC_NUMBER_PATTERN.matcher(ecNumber).matches();
    }
}
