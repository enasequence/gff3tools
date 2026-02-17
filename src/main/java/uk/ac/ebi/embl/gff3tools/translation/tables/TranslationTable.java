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

import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * NCBI translation table. Bases are encoded using lower case single letter JCBN abbreviations
 * and amino acids are encoded using upper case single letter JCBN abbreviations.
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class TranslationTable {

    public static final String DEFAULT_TRANSLATION_TABLE = "11";
    /** Translation table number. */
    private final Integer number;

    /** Translation table name. */
    private final String name;

    /** Start codon translations (first codon special handling). */
    private final Map<String, Character> startCodonMap;

    /** Non-start codon translations. */
    private final Map<String, Character> otherCodonMap;
}
