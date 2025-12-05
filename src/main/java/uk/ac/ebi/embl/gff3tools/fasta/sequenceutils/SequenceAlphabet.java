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
package uk.ac.ebi.embl.gff3tools.fasta.sequenceutils;

public final class SequenceAlphabet {
    private final boolean[] allowed = new boolean[128];

    public SequenceAlphabet(String chars) {
        for (char c : chars.toCharArray()) if (c < 128) allowed[c] = true;
        allowed['>'] = false;
    }

    /** Fast ASCII check for is it an allowed char. */
    public boolean isAllowed(byte b) {
        int i = b & 0xFF;
        return i < 128 && allowed[i];
    }

    /** Fast ASCII check for 'N' or 'n' without decoding. */
    public boolean isNBase(byte b) {
        return ((b | 0x20) == 'n');
    }

    public static SequenceAlphabet defaultNucleotideAlphabet() {
        return new SequenceAlphabet("ACGTURYSWKMBDHVNacgturyswkmbdhvn-.*");
    }

    public String describeAllowed() {
        StringBuilder sb = new StringBuilder();
        sb.append("[");

        boolean first = true;
        for (int i = 0; i < allowed.length; i++) {
            if (allowed[i]) {
                char c = (char) i;

                // Render unprintables safely
                String display;
                if (c >= 32 && c < 127) {
                    display = Character.toString(c);
                } else {
                    display = String.format("\\x%02X", i); // e.g. non-printable → \x1B
                }

                if (!first) sb.append(", ");
                sb.append(display);
                first = false;
            }
        }

        sb.append("]");
        return sb.toString();
    }

}
