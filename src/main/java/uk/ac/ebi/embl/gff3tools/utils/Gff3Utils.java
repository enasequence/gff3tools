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
package uk.ac.ebi.embl.gff3tools.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Gff3Utils {

    public static void addAttribute(Map<String, List<String>> attributes, String key, String value) {

        List<String> attributeValue = attributes.getOrDefault(key, new ArrayList<>());
        attributeValue.add(value);
        attributes.put(key, attributeValue);
    }

    public static void addAttributes(Map<String, List<String>> attributes, String key, List<String> value) {
        for (String v : value) {
            addAttribute(attributes, key, v);
        }
    }
}
