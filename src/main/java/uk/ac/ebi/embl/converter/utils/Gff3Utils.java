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
package uk.ac.ebi.embl.converter.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Gff3Utils {

    public static void addAttribute(Map<String, Object> attributes, String key, String value) {

        Object attributeValue = attributes.get(key);
        if (attributeValue == null) {
            attributes.put(key, value);
        } else if (attributeValue instanceof String) {
            List<String> list = new ArrayList<>();
            list.add((String) attributeValue);
            list.add(value);
            attributes.put(key, list);
        } else if (attributeValue instanceof List) {
            ((List<String>) attributeValue).add(value);
        }
    }

    public static void addAttributes(Map<String, Object> attributes, String key, List<String> value) {
        for (String v : value) {
            addAttribute(attributes, key, v);
        }
    }
}
