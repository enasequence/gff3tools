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

import java.util.HashMap;
import java.util.Map;
import uk.ac.ebi.embl.api.entry.feature.FeatureFactory;
import uk.ac.ebi.embl.api.entry.feature.SourceFeature;
import uk.ac.ebi.embl.api.entry.qualifier.Qualifier;
import uk.ac.ebi.embl.api.entry.qualifier.QualifierFactory;
import uk.ac.ebi.ena.taxonomy.taxon.Taxon;

public class SourceFeatureDTO {

    public String scientificName;
    public String commonName;
    public Long taxId;
    public Taxon taxon;
    public boolean focus;
    public boolean transgenic;
    public Map<String, String> qualifiers;

    public SourceFeatureDTO() {}

    public SourceFeatureDTO(SourceFeature original) {
        this.scientificName = original.getScientificName();
        this.commonName = original.getCommonName();
        this.taxId = original.getTaxId();
        this.taxon = original.getTaxon();
        this.focus = original.isFocus();
        this.transgenic = original.isTransgenic();

        this.qualifiers = new HashMap<>();

        for (Qualifier qualifier : original.getQualifiers()) {
            this.qualifiers.put(qualifier.getName(), qualifier.getValue());
        }
    }

    public SourceFeature toSourceFeature() {
        SourceFeature copy = new FeatureFactory().createSourceFeature();

        copy.setScientificName(scientificName);
        copy.setCommonName(commonName);
        copy.setTaxId(taxId);
        copy.setTaxon(taxon);
        copy.setFocus(focus);
        copy.setTransgenic(transgenic);

        if (qualifiers != null) {
            QualifierFactory qf = new QualifierFactory();

            for (Map.Entry<String, String> entry : qualifiers.entrySet()) {
                copy.addQualifier(qf.createQualifier(entry.getKey(), entry.getValue()));
            }
        }

        return copy;
    }
}
