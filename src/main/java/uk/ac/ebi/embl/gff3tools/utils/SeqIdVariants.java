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

/**
 * Candidate seqId forms for version-tolerant id resolution.
 *
 * <p>Sequence sources and header sources are keyed by the submission id of the FASTA header,
 * which usually carries a version (e.g. "OZ375371.1"), while the GFF3 mapper and validators
 * query with whichever id form their input uses (versioned or unversioned). Callers try the
 * candidate forms in order and take the first one the source holds:
 *
 * <ol>
 *   <li>the seqId as given (exact match),
 *   <li>the seqId with a trailing {@code .version} stripped, e.g. "OZ375371.1" → "OZ375371",
 *   <li>the seqId with each of the versions {@code .1} to {@code .9} appended.
 * </ol>
 */
public class SeqIdVariants {

    private static final int MAX_SINGLE_DIGIT_VERSION = 9;

    private SeqIdVariants() {}

    /**
     * Returns the candidate forms of the given seqId in resolution order, starting with the
     * seqId as given. Returns an empty list for a null seqId.
     */
    public static List<String> variants(String seqId) {
        if (seqId == null) {
            return List.of();
        }
        List<String> variants = new ArrayList<>();
        variants.add(seqId);
        // Versioned query over an unversioned key: strip the trailing .version.
        int dot = seqId.lastIndexOf('.');
        if (dot > 0 && isDigits(seqId.substring(dot + 1))) {
            variants.add(seqId.substring(0, dot));
        }
        // Unversioned query over a versioned key: try versions 1..9.
        for (int version = 1; version <= MAX_SINGLE_DIGIT_VERSION; version++) {
            variants.add(seqId + "." + version);
        }
        return variants;
    }

    private static boolean isDigits(String s) {
        if (s.isEmpty()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
