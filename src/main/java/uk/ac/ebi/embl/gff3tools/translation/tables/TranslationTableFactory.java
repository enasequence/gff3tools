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
package uk.ac.ebi.embl.gff3tools.translation.tables;

import java.util.HashMap;
import java.util.Map;

/**
 * Singleton factory for creating TranslationTable instances from NCBI table numbers.
 * Translation tables are initialized once and cached for the lifetime of the application.
 *
 * <p>Usage:
 * <pre>
 * TranslationTable table = TranslationTableFactory.getInstance().getTranslationTable(11);
 * </pre>
 */
public class TranslationTableFactory {

    private static final TranslationTableFactory INSTANCE = new TranslationTableFactory();

    private final Map<Integer, TranslationTable> translationTables;

    /**
     * Private constructor - use getInstance() to get the singleton instance.
     */
    private TranslationTableFactory() {
        translationTables = new HashMap<>();
        initializeTables();
    }

    /**
     * Returns the singleton instance of the factory.
     *
     * @return the singleton TranslationTableFactory instance
     */
    public static TranslationTableFactory getInstance() {
        return INSTANCE;
    }

    /**
     * Gets a translation table by its NCBI table translationTableNumber.
     *
     * @param translationTableNumber the NCBI translation table translationTableNumber (1-33)
     * @return the TranslationTable, or null if the translationTableNumber is invalid
     */
    public TranslationTable getTranslationTable(Integer translationTableNumber) {
        if (translationTableNumber == null) {
            return null;
        }
        return translationTables.get(translationTableNumber);
    }

    /**
     * @deprecated Use {@link #getTranslationTable(Integer)} instead.
     */
    @Deprecated
    public TranslationTable createTranslationTable(Integer number) {
        return getTranslationTable(number);
    }

    /**
     * Initializes all translation tables from descriptors.
     */
    private void initializeTables() {
        for (TranslationTableDescriptor descriptor : TranslationTableDescriptor.values()) {
            translationTables.put(descriptor.getNumber(), createTable(descriptor));
        }
    }

    /**
     * Creates a TranslationTable from a descriptor.
     */
    private TranslationTable createTable(TranslationTableDescriptor descriptor) {
        Map<String, Character> startCodonMap = new HashMap<>();
        Map<String, Character> otherCodonMap = new HashMap<>();
        char[] codon = new char[3];
        int i = 0;
        char[] bases = {'t', 'c', 'a', 'g'};

        for (char base1 : bases) {
            codon[0] = base1;
            for (char base2 : bases) {
                codon[1] = base2;
                for (char base3 : bases) {
                    codon[2] = base3;
                    char aminoAcid = descriptor.getAminoAcids().charAt(i);
                    otherCodonMap.put(new String(codon), aminoAcid);
                    if (descriptor.getStarts().charAt(i) == 'M') {
                        aminoAcid = 'M';
                    }
                    startCodonMap.put(new String(codon), aminoAcid);
                    ++i;
                }
            }
        }

        return new TranslationTable(descriptor.getNumber(), descriptor.getName(), startCodonMap, otherCodonMap);
    }
}
