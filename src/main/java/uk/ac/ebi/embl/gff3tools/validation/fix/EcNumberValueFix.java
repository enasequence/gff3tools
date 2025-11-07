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

import static uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes.EC_NUMBER;
import static uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes.PRODUCT;
import static uk.ac.ebi.embl.gff3tools.validation.meta.ValidationType.FEATURE;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.validation.meta.FixMethod;
import uk.ac.ebi.embl.gff3tools.validation.meta.Gff3Fix;

@Slf4j
@Gff3Fix(name = "EC_NUMBER", description = "Remove the EC_number attribute if necessary conditions met")
public class EcNumberValueFix {

    record ProductEcResult(String product, List<String> ecNumbers) {}

    private static final Pattern PRODUCT_EC_PATTERN =
            Pattern.compile("(?i)\\bEC\\s*[:=]?\\s*(\\d+(?:\\.\\d+|\\.-){2,3})");

    private static final Pattern EC_NUMBER_PATTERN = Pattern.compile("\\d+(?:\\.(?:\\d+|-|n\\d*)){3}");

    private static final Set<String> INVALID_EC_VALUES = Set.of("deleted", "-.-.-.-", "-.-.-", "-.-", "-");

    @FixMethod(
            rule = "PRODUCT_WITH_EC_NUMBER",
            description = "Derive EC_NUMBER from PRODUCT and clean PRODUCT value",
            type = FEATURE)
    public void fixEcFromProduct(GFF3Feature feature, int line) {

        Optional<List<String>> optProducts = feature.getAttributeList(PRODUCT);
        if (optProducts.isEmpty()) {
            return;
        }

        Set<String> derivedEcNumbers = new LinkedHashSet<>();
        List<String> cleanedProducts = new ArrayList<>();

        for (String productValue : optProducts.get()) {
            if (productValue == null) continue;

            String normalized = productValue.trim().toLowerCase();
            if (normalized.contains("hypothetical protein") || normalized.contains("unknown")) {

                log.info(
                        "Fix: removing EC_NUMBER because product is 'hypothetical protein' or 'unknown' at line {}",
                        line);

                feature.removeAttributeList(EC_NUMBER);
                return;
            }

            ProductEcResult result = extractEcFromProduct(productValue);
            cleanedProducts.add(result.product());

            for (String ec : result.ecNumbers()) {
                if (isValidEc(ec)) {
                    derivedEcNumbers.add(ec);
                }
            }
        }

        // Update PRODUCT if changed
        if (!cleanedProducts.equals(optProducts.get())) {
            feature.setAttributeList(PRODUCT, new ArrayList<>(cleanedProducts));
        }

        // Update EC_NUMBER based on derivation
        if (!derivedEcNumbers.isEmpty()) {
            feature.setAttributeList(EC_NUMBER, new ArrayList<>(derivedEcNumbers));
        } else if (feature.hasAttribute(EC_NUMBER)) {
            log.info("Fix: removing EC_NUMBER because no valid EC found in PRODUCT at line {}", line);
            feature.removeAttributeList(EC_NUMBER);
        }
    }

    @FixMethod(rule = "EC_NUMBER", description = "Remove invalid EC_NUMBER values", type = FEATURE)
    public void fixEcNumber(GFF3Feature feature, int line) {

        Optional<List<String>> opt = feature.getAttributeList(EC_NUMBER);
        if (opt.isEmpty()) return;

        List<String> original = opt.get();
        List<String> valid = new ArrayList<>();

        for (String ec : original) {
            if (ec == null) continue;

            String trimmed = ec.trim();
            if (isValidEc(trimmed)) {
                valid.add(trimmed);
            } else {
                log.info("Fix: removing invalid EC_NUMBER '{}' at line {}", ec, line);
            }
        }

        if (valid.isEmpty()) {
            feature.removeAttributeList(EC_NUMBER);
        } else if (!valid.equals(original)) {
            feature.setAttributeList(EC_NUMBER, new ArrayList<>(valid));
        }
    }

    private ProductEcResult extractEcFromProduct(String product) {

        List<String> ecNumbers = new ArrayList<>();
        Matcher matcher = PRODUCT_EC_PATTERN.matcher(product);

        while (matcher.find()) {
            ecNumbers.add(matcher.group(1));
        }

        String cleanedProduct =
                PRODUCT_EC_PATTERN.matcher(product).replaceAll("").trim();

        return new ProductEcResult(cleanedProduct, ecNumbers);
    }

    private boolean isValidEc(String ec) {
        return !INVALID_EC_VALUES.contains(ec.toLowerCase())
                && EC_NUMBER_PATTERN.matcher(ec).matches();
    }
}
