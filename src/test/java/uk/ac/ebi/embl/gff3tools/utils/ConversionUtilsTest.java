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

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ConversionUtilsTest {

    // --- Exact matching (no wildcards) ---

    @Test
    void matchesWildcardValue_exactMatch() {
        assertTrue(ConversionUtils.matchesWildcardValue("snoRNA", "snoRNA"));
    }

    @Test
    void matchesWildcardValue_exactMatchCaseInsensitive() {
        assertTrue(ConversionUtils.matchesWildcardValue("snoRNA", "SNORNA"));
    }

    @Test
    void matchesWildcardValue_exactMatchFails() {
        assertFalse(ConversionUtils.matchesWildcardValue("snoRNA", "miRNA"));
    }

    // --- Named wildcard <NAME> (1+ characters) ---

    @Test
    void matchesWildcardValue_fullWildcard() {
        // Pattern like <length of feature> matches any non-empty value
        assertTrue(ConversionUtils.matchesWildcardValue("<length of feature>", "91"));
        assertTrue(ConversionUtils.matchesWildcardValue("<length of feature>", "12345"));
        assertTrue(ConversionUtils.matchesWildcardValue("<NAME>", "anything"));
    }

    @Test
    void matchesWildcardValue_fullWildcardRequiresAtLeastOneChar() {
        assertFalse(ConversionUtils.matchesWildcardValue("<NAME>", ""));
    }

    @Test
    void matchesWildcardValue_namedWildcardWithPrefix() {
        assertTrue(ConversionUtils.matchesWildcardValue("prefix:<NAME>", "prefix:value"));
    }

    @Test
    void matchesWildcardValue_namedWildcardWithPrefixRequiresContent() {
        // Named wildcard must match at least one character
        assertFalse(ConversionUtils.matchesWildcardValue("prefix:<NAME>", "prefix:"));
    }

    @Test
    void matchesWildcardValue_namedWildcardWrongPrefix() {
        assertFalse(ConversionUtils.matchesWildcardValue("prefix:<NAME>", "other:value"));
    }

    @Test
    void matchesWildcardValue_namedWildcardCaseInsensitivePrefix() {
        assertTrue(ConversionUtils.matchesWildcardValue("PREFIX:<NAME>", "prefix:something"));
    }

    // --- Glob wildcard * (0+ characters) ---

    @Test
    void matchesGlob_barePrefix() {
        // "transposon*" matches the bare type with zero chars after the prefix
        assertTrue(ConversionUtils.matchesWildcardValue("transposon*", "transposon"));
    }

    @Test
    void matchesGlob_prefixWithColonAndName() {
        assertTrue(ConversionUtils.matchesWildcardValue("transposon*", "transposon:Mutator_TIR"));
        assertTrue(ConversionUtils.matchesWildcardValue("transposon*", "transposon:Ac"));
    }

    @Test
    void matchesGlob_prefixWithColonOnly() {
        assertTrue(ConversionUtils.matchesWildcardValue("transposon*", "transposon:"));
    }

    @Test
    void matchesGlob_wrongPrefix() {
        assertFalse(ConversionUtils.matchesWildcardValue("transposon*", "retrotransposon:copia"));
        assertFalse(ConversionUtils.matchesWildcardValue("other*", "transposon:Mutator_TIR"));
    }

    @Test
    void matchesGlob_caseInsensitivePrefix() {
        assertTrue(ConversionUtils.matchesWildcardValue("MITE*", "mite:something"));
        assertTrue(ConversionUtils.matchesWildcardValue("MITE*", "mite"));
    }

    @Test
    void matchesGlob_otherTypes() {
        assertTrue(ConversionUtils.matchesWildcardValue("other*", "other:helitron"));
        assertTrue(ConversionUtils.matchesWildcardValue("other*", "other"));
        assertTrue(ConversionUtils.matchesWildcardValue("retrotransposon*", "retrotransposon:copia"));
        assertTrue(ConversionUtils.matchesWildcardValue("retrotransposon*", "retrotransposon"));
        assertTrue(ConversionUtils.matchesWildcardValue("SINE*", "SINE:Alu"));
        assertTrue(ConversionUtils.matchesWildcardValue("SINE*", "SINE"));
        assertTrue(ConversionUtils.matchesWildcardValue("LINE*", "LINE:L1"));
        assertTrue(ConversionUtils.matchesWildcardValue("LINE*", "LINE"));
        assertTrue(ConversionUtils.matchesWildcardValue("insertion sequence*", "insertion sequence:IS1"));
        assertTrue(ConversionUtils.matchesWildcardValue("insertion sequence*", "insertion sequence"));
    }

    @Test
    void matchesGlob_satellite() {
        assertTrue(ConversionUtils.matchesWildcardValue("satellite*", "satellite:ALR_"));
        assertTrue(ConversionUtils.matchesWildcardValue("satellite*", "satellite"));
        assertTrue(ConversionUtils.matchesWildcardValue("microsatellite*", "microsatellite:GT"));
        assertTrue(ConversionUtils.matchesWildcardValue("microsatellite*", "microsatellite"));
        assertTrue(ConversionUtils.matchesWildcardValue("minisatellite*", "minisatellite:VNTR"));
        assertTrue(ConversionUtils.matchesWildcardValue("minisatellite*", "minisatellite"));
    }

    @Test
    void matchesGlob_standaloneStarMatchesAnything() {
        assertTrue(ConversionUtils.matchesWildcardValue("*", "anything"));
        assertTrue(ConversionUtils.matchesWildcardValue("*", ""));
    }

    @Test
    void matchesGlob_tooShortForPrefix() {
        assertFalse(ConversionUtils.matchesWildcardValue("transposon*", "trans"));
    }
}
