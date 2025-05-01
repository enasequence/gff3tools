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
package uk.ac.ebi.embl.converter.utils;

public class ConversionEntry {
    String sOID;
    String sOTerm;
    String definition;
    String feature;
    String qualifier1;
    String qualifier2;

    ConversionEntry(String[] tokens) {
        this.sOID = tokens[0];
        this.sOTerm = tokens[1];
        this.definition = tokens[2];
        this.feature = tokens[3];
        for (String token : tokens)
            if (tokens.length > 4) this.qualifier1 = tokens[4].equalsIgnoreCase("null") ? null : tokens[4];
        if (tokens.length > 5) this.qualifier2 = tokens[5].equalsIgnoreCase("null") ? null : tokens[5];
    }

    public String getQualifier1() {
        return qualifier1;
    }

    public String getQualifier2() {
        return qualifier2;
    }

    public String getSOID() {
        return sOID;
    }

    public String getFeature() {
        return feature;
    }

    public String getSOTerm() {
        return sOTerm;
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("ConversionEntry{");
        sb.append(", sOTerm='").append(sOTerm).append('\'');
        sb.append(", feature='").append(feature).append('\'');
        sb.append(", qualifier1='").append(qualifier1).append('\'');
        sb.append(", qualifier2='").append(qualifier2).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
