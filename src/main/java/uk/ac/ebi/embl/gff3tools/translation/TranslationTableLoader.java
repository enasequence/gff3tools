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
package uk.ac.ebi.embl.gff3tools.translation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class TranslationTableLoader {

    private static final TranslationTableLoader INSTANCE = new TranslationTableLoader();

    private final Map<Integer, TranslationTable> tables;

    private TranslationTableLoader() {
        this.tables = load();
    }

    public static TranslationTableLoader getInstance() {
        return INSTANCE;
    }

    // Public access point
    public TranslationTable get(int id) {
        TranslationTable table = tables.get(id);
        if (table == null) {
            throw new IllegalArgumentException("Unknown translation table: " + id);
        }
        return table;
    }

    public Map<Integer, TranslationTable> all() {
        return tables;
    }

    private static Map<Integer, TranslationTable> load() {
        try (InputStream is =
                TranslationTableLoader.class.getClassLoader().getResourceAsStream("translation_tables.json")) {

            if (is == null) {
                throw new IllegalStateException("translation_tables.json not found on classpath");
            }

            ObjectMapper mapper = new ObjectMapper();

            Map<String, TranslationTableJson> raw = mapper.readValue(is, new TypeReference<>() {});

            Map<Integer, TranslationTable> tables = new HashMap<>();

            for (Map.Entry<String, TranslationTableJson> entry : raw.entrySet()) {
                int id = Integer.parseInt(entry.getKey());
                TranslationTableJson jsonValue = entry.getValue();
                validate(jsonValue);
                tables.put(id, buildTranslationTable(id, jsonValue));
            }

            return Collections.unmodifiableMap(tables);

        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static TranslationTable buildTranslationTable(int id, TranslationTableJson translationTableJson) {
        Map<String, Character> startCodonMap = new HashMap<String, Character>();
        Map<String, Character> otherCodonMap = new HashMap<String, Character>();
        String name = translationTableJson.name;
        char[] codon = new char[3];
        int i = 0;
        char[] bases = {'t', 'c', 'a', 'g'};
        for (char base1 : bases) {
            codon[0] = base1;
            for (char base2 : bases) {
                codon[1] = base2;
                for (char base3 : bases) {
                    codon[2] = base3;
                    char aminoAcid = translationTableJson.aminoAcids.charAt(i);
                    otherCodonMap.put(new String(codon), aminoAcid);
                    if (translationTableJson.startCodons.charAt(i) == 'M') {
                        aminoAcid = 'M';
                    }
                    startCodonMap.put(new String(codon), aminoAcid);
                    ++i;
                }
            }
        }
        return new TranslationTable(id, name, startCodonMap, otherCodonMap);
    }

    private static void validate(TranslationTableJson json) {
        if (json.aminoAcids.length() != 64 || json.startCodons.length() != 64) {
            throw new IllegalArgumentException("Translation table must contain exactly 64 codons");
        }
    }

    public static class TranslationTableJson {

        public String name;
        public String aminoAcids;
        public String startCodons;
    }
}
