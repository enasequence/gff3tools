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
package uk.ac.ebi.embl.gff3tools.validation.builtin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.utils.ConversionUtils;
import uk.ac.ebi.embl.gff3tools.utils.OntologyClient;
import uk.ac.ebi.embl.gff3tools.utils.OntologyTerm;
import uk.ac.ebi.embl.gff3tools.validation.*;
import uk.ac.ebi.embl.gff3tools.validation.meta.Gff3Validation;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationMethod;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationType;

@Gff3Validation(name = "LOCATION")
public class LocationValidation extends Validation {

    private static final String INVALID_START_END_MESSAGE = "Invalid start/end for accession \"%s\"";
    private static final String INVALID_PROPEPTIDE_CDS_LOCATION_MESSAGE = "Propeptide [%d %d] not inside any CDS";
    private static final String INVALID_PROPEPTIDE_PEPTIDE_LOCATION_MESSAGE =
            "Propeptide [%d %d] overlaps with peptide features";

    private final OntologyClient ontologyClient = ConversionUtils.getOntologyClient();

    @ValidationMethod(rule = "LOCATION", type = ValidationType.FEATURE)
    public void validateLocation(GFF3Feature feature, int line) throws ValidationException {
        long start = feature.getStart();
        long end = feature.getEnd();

        if (start <= 0 || end <= 0) {
            throw new ValidationException(line, INVALID_START_END_MESSAGE.formatted(feature.accession()));
        }
        boolean isCircular = Boolean.TRUE
                .toString()
                .equalsIgnoreCase(feature.getAttributeByName(GFF3Attributes.CIRCULAR_RNA)
                        .map(List::getFirst)
                        .get());
        if (!isCircular && end < start) {
            throw new ValidationException(line, INVALID_START_END_MESSAGE.formatted(feature.accession()));
        }
    }

    @ValidationMethod(rule = "CDS_LOCATION_BOUNDARIES", type = ValidationType.ANNOTATION)
    public void validateCdsLocation(GFF3Annotation annotation, int line) throws ValidationException {
        List<GFF3Feature> propFeatures = new ArrayList<>();
        List<GFF3Feature> cdsFeatures = new ArrayList<>();
        List<GFF3Feature> peptideFeatures = new ArrayList<>();

        for (GFF3Feature feature : annotation.getFeatures()) {
            String featureName = feature.getName();
            Optional<String> soIdOpt = ontologyClient.findTermByNameOrSynonym(featureName);
            if (soIdOpt.isEmpty()) {
                continue;
            }
            String soId = soIdOpt.get();
            if (OntologyTerm.PROPEPTIDE_REGION_OF_CDS.ID.equals(soId)) {
                propFeatures.add(feature);
            }
            if (OntologyTerm.CDS_REGION.ID.equals(soId)) {
                cdsFeatures.add(feature);
            }
            if (OntologyTerm.SIGNAL_PEPTIDE_REGION_OF_CDS.ID.equals(soId)
                    || OntologyTerm.MATURE_PROTEIN_REGION_OF_CDS.ID.equals(soId)) {
                peptideFeatures.add(feature);
            }
        }

        cdsFeatures.sort(Comparator.comparingLong(GFF3Feature::getStart));
        peptideFeatures.sort(Comparator.comparingLong(GFF3Feature::getStart));

        for (GFF3Feature propFeature : propFeatures) {

            long start = propFeature.getStart();
            long end = propFeature.getEnd();

            // TODO: Need to separate the below validation - after confirmation on parent child
            // Must be inside at least one CDS
            boolean insideCds = false;
            for (GFF3Feature cds : cdsFeatures) {
                if (cds.getEnd() < start) continue;
                if (cds.getStart() > end) break;
                if (start >= cds.getStart() && end <= cds.getEnd()) {
                    insideCds = true;
                    break;
                }
            }

            if (!insideCds) {
                throw new ValidationException(line, INVALID_PROPEPTIDE_CDS_LOCATION_MESSAGE.formatted(start, end));
            }

            // Must not overlap any peptide features
            for (GFF3Feature peptide : peptideFeatures) {
                if (peptide.getEnd() < start) continue;
                if (peptide.getStart() > end) break;
                if (start < peptide.getEnd() && end > peptide.getStart()) {
                    throw new ValidationException(
                            line, INVALID_PROPEPTIDE_PEPTIDE_LOCATION_MESSAGE.formatted(start, end));
                }
            }
        }
    }
}
