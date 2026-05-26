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
import java.util.Optional;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import uk.ac.ebi.embl.api.entry.feature.FeatureFactory;
import uk.ac.ebi.embl.api.entry.feature.SourceFeature;
import uk.ac.ebi.embl.api.entry.qualifier.Qualifier;
import uk.ac.ebi.embl.api.entry.qualifier.QualifierFactory;
import uk.ac.ebi.ena.taxonomy.taxon.Taxon;

@Getter
@Setter
@NoArgsConstructor
public class SourceFeatureDTO {

    private String id;

    // extracted data from getters/setters
    private String scientificName;
    private String commonName;
    private Long taxId;
    private Taxon taxon;
    private boolean focus;
    private boolean transgenic;
    private Map<String, String> qualifiers;

    public SourceFeatureDTO(String id, SourceFeature original) {
        if (original == null) {
            throw new NullPointerException("Original feature is null");
        }
        if (id == null || id.isEmpty()) {
            throw new NullPointerException("Associated id is null or empty");
        }

        this.id = id;
        this.scientificName = original.getScientificName();
        this.commonName = original.getCommonName();
        this.taxId = original.getTaxId();
        this.taxon = original.getTaxon();
        this.focus = original.isFocus();
        this.transgenic = original.isTransgenic();

        this.qualifiers = new HashMap<>();
        if (original.getQualifiers() != null) {
            for (Qualifier qualifier : original.getQualifiers()) {
                if (qualifier == null) continue;

                if (qualifier.getName() != null) {
                    this.qualifiers.put(qualifier.getName(), qualifier.getValue());
                }
            }
        }
    }

    public SourceFeature toSourceFeature() {

        SourceFeature copy = new FeatureFactory().createSourceFeature();

        Optional.ofNullable(scientificName).ifPresent(copy::setScientificName);
        Optional.ofNullable(commonName).ifPresent(copy::setCommonName);
        Optional.ofNullable(taxId).ifPresent(copy::setTaxId);
        Optional.ofNullable(taxon).ifPresent(copy::setTaxon);

        copy.setFocus(focus);
        copy.setTransgenic(transgenic);

        if (qualifiers != null) {
            QualifierFactory qf = new QualifierFactory();

            qualifiers.entrySet().stream()
                    .filter(entry -> entry.getKey() != null)
                    .forEach(entry -> copy.addQualifier(qf.createQualifier(entry.getKey(), entry.getValue())));
        }

        return copy;
    }
}
