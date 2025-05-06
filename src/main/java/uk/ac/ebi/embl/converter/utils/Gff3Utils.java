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
}
