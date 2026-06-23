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

import java.util.Map;
import uk.ac.ebi.embl.fastareader.SequenceRangeOption;
import uk.ac.ebi.embl.gff3tools.sequence.SequenceLookup;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;

public class ValidationUtils {

    public static Long resolveSequenceLength(
            String seqId, Map<String, Long> sequenceLengthCache, ValidationContext context) {
        if (sequenceLengthCache.containsKey(seqId)) {
            return sequenceLengthCache.get(seqId);
        }
        if (context.contains(SequenceLookup.class)) {
            SequenceLookup lookup = context.get(SequenceLookup.class);
            if (lookup != null) {
                try {
                    Long length = lookup.getSequenceLength(seqId, SequenceRangeOption.WITHOUT_EDGE_N_BASES);
                    sequenceLengthCache.put(seqId, length);
                    return length;
                } catch (Exception ex) {
                    throw new IllegalStateException("Unable to resolve sequence length for " + seqId, ex);
                }
            }
        }
        return null;
    }
}
