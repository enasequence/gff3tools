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
package uk.ac.ebi.embl.gff3tools.sequence;

/** A contiguous run of N/n bases in a sequence, using 1-based inclusive coordinates. */
public record GapRegion(long startBase, long endBase) {

    public GapRegion {
        if (startBase < 1 || endBase < startBase) {
            throw new IllegalArgumentException("bad gap region: " + startBase + ".." + endBase);
        }
    }

    public long lengthBases() {
        return endBase - startBase + 1;
    }

    public boolean overlaps(long fromBase, long toBase) {
        if (fromBase < 1 || toBase < fromBase) {
            throw new IllegalArgumentException("bad base range: " + fromBase + ".." + toBase);
        }
        return startBase <= toBase && endBase >= fromBase;
    }
}
