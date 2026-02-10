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

    @Test
    void matchesWildcardValue_fullWildcard() {
        // Pattern like <length of feature> matches any value
        assertTrue(ConversionUtils.matchesWildcardValue("<length of feature>", "91"));
        assertTrue(ConversionUtils.matchesWildcardValue("<length of feature>", "12345"));
        assertTrue(ConversionUtils.matchesWildcardValue("<NAME>", "anything"));
    }

    @Test
    void matchesWildcardValue_embeddedWildcardTransposon() {
        assertTrue(ConversionUtils.matchesWildcardValue("transposon:<NAME>", "transposon:Mutator_TIR"));
        assertTrue(ConversionUtils.matchesWildcardValue("transposon:<NAME>", "transposon:Ac"));
    }

    @Test
    void matchesWildcardValue_embeddedWildcardOther() {
        assertTrue(ConversionUtils.matchesWildcardValue("other:<NAME>", "other:helitron"));
    }

    @Test
    void matchesWildcardValue_embeddedWildcardRetrotransposon() {
        assertTrue(ConversionUtils.matchesWildcardValue("retrotransposon:<NAME>", "retrotransposon:copia"));
    }

    @Test
    void matchesWildcardValue_embeddedWildcardSINE() {
        assertTrue(ConversionUtils.matchesWildcardValue("SINE:<NAME>", "SINE:Alu"));
    }

    @Test
    void matchesWildcardValue_embeddedWildcardLINE() {
        assertTrue(ConversionUtils.matchesWildcardValue("LINE:<NAME>", "LINE:L1"));
    }

    @Test
    void matchesWildcardValue_embeddedWildcardInsertionSequence() {
        assertTrue(ConversionUtils.matchesWildcardValue("insertion sequence:<NAME>", "insertion sequence:IS1"));
    }

    @Test
    void matchesWildcardValue_embeddedWildcardWrongPrefix() {
        assertFalse(ConversionUtils.matchesWildcardValue("transposon:<NAME>", "retrotransposon:copia"));
        assertFalse(ConversionUtils.matchesWildcardValue("other:<NAME>", "transposon:Mutator_TIR"));
    }

    @Test
    void matchesWildcardValue_embeddedWildcardPrefixOnly() {
        // Wildcard must match at least one character
        assertFalse(ConversionUtils.matchesWildcardValue("transposon:<NAME>", "transposon:"));
    }

    @Test
    void matchesWildcardValue_embeddedWildcardCaseInsensitivePrefix() {
        assertTrue(ConversionUtils.matchesWildcardValue("MITE:<NAME>", "mite:something"));
    }
}
