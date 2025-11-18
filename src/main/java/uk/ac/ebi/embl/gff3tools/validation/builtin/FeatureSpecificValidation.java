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

import java.util.*;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.utils.ConversionUtils;
import uk.ac.ebi.embl.gff3tools.utils.OntologyClient;
import uk.ac.ebi.embl.gff3tools.utils.OntologyTerm;
import uk.ac.ebi.embl.gff3tools.validation.Validation;
import uk.ac.ebi.embl.gff3tools.validation.meta.Gff3Validation;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationMethod;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationType;

@Gff3Validation(name = "FEATURE_SPECIFIC")
public class FeatureSpecificValidation extends Validation {

    private static final String INVALID_OPERON_MESSAGE =
            "Feature \"%s\" belongs to operon \"%s\", but no other features share this operon. Expected at least one additional member.";

    private static final String PSEUDO_ATTRIBUTE_REQUIRED_VALIDATION =
            "Peptide \"%s\" requires the 'pseudo' attribute because its CDS \"%s\" is marked as pseudo";

    private final OntologyClient ontologyClient = ConversionUtils.getOntologyClient();

    @ValidationMethod(rule = "OPERON_FEATURE", type = ValidationType.FEATURE)
    public void validateOperonFeatures(GFF3Feature feature, int line) throws ValidationException {
        String operonValue = feature.getAttributeByName(GFF3Attributes.OPERON);
        if (operonValue == null || operonValue.isBlank()) {
            return;
        }

        Optional<String> soIdOpt = ontologyClient.findTermByNameOrSynonym(feature.getName());
        boolean isOperon =
                soIdOpt.isPresent() && (ontologyClient.isSelfOrDescendantOf(soIdOpt.get(), OntologyTerm.OPERON.ID));
        if (isOperon) {
            return;
        }

        throw new ValidationException(line, INVALID_OPERON_MESSAGE.formatted(feature.getName(), operonValue));
    }

    @ValidationMethod(rule = "PEPTIDE_FEATURE", type = ValidationType.ANNOTATION)
    public void validatePeptideFeature(GFF3Annotation annotation, int line) throws ValidationException {
        List<GFF3Feature> cdsFeatures = new ArrayList<>();
        List<GFF3Feature> peptideFeatures = new ArrayList<>();

        for (GFF3Feature feature : annotation.getFeatures()) {
            Optional<String> soIdOpt = ontologyClient.findTermByNameOrSynonym(feature.getName());
            if (soIdOpt.isEmpty()) {
                continue;
            }
            String soId = soIdOpt.get();
            if (OntologyTerm.CDS.ID.equals(soId) || OntologyTerm.CDS_REGION.ID.equals(soId)) {
                cdsFeatures.add(feature);
            } else if (ontologyClient.isSelfOrDescendantOf(soId, OntologyTerm.POLYPEPTIDE_REGION.ID)) {
                peptideFeatures.add(feature);
            }
        }

        Map<String, List<GFF3Feature>> peptidesByLocus = new HashMap<>();
        Map<String, List<GFF3Feature>> peptidesByGene = new HashMap<>();

        for (GFF3Feature peptide : peptideFeatures) {
            String locusTag = peptide.getAttributeByName(GFF3Attributes.LOCUS_TAG);
            String gene = peptide.getAttributeByName(GFF3Attributes.GENE);

            if (locusTag != null) {
                peptidesByLocus
                        .computeIfAbsent(locusTag, k -> new ArrayList<>())
                        .add(peptide);
            }
            if (gene != null) {
                peptidesByGene.computeIfAbsent(gene, k -> new ArrayList<>()).add(peptide);
            }
        }

        for (GFF3Feature cds : cdsFeatures) {
            List<GFF3Feature> relevantPeptides = new ArrayList<>();

            String cdsLocus = cds.getAttributeByName(GFF3Attributes.LOCUS_TAG);
            String cdsGene = cds.getAttributeByName(GFF3Attributes.GENE);

            // Direct lookups instead of scanning entire peptide list
            if (cdsLocus != null && peptidesByLocus.containsKey(cdsLocus)) {
                for (GFF3Feature peptide : peptidesByLocus.get(cdsLocus)) {
                    if (areLocationsOnSameStrand(cds, peptide)) {
                        relevantPeptides.add(peptide);
                    }
                }
            }

            if (cdsGene != null && peptidesByGene.containsKey(cdsGene)) {
                for (GFF3Feature peptide : peptidesByGene.get(cdsGene)) {
                    if (doLocationsOverlap(cds, peptide)) {
                        relevantPeptides.add(peptide);
                    }
                }
            }

            if (!relevantPeptides.isEmpty()) {
                checkPseudoQualifier(cds, relevantPeptides, line);
            }
        }
    }

    private boolean doLocationsOverlap(GFF3Feature f1, GFF3Feature f2) {
        return f1.getStart() <= f2.getEnd() && f2.getStart() <= f1.getEnd();
    }

    private boolean areLocationsOnSameStrand(GFF3Feature f1, GFF3Feature f2) {
        String strand1 = f1.getStrand();
        String strand2 = f2.getStrand();

        if (".".equals(strand1) || ".".equals(strand2)) {
            return false;
        }
        return strand1.equals(strand2);
    }

    private void checkPseudoQualifier(GFF3Feature cdsFeature, List<GFF3Feature> peptideFeatures, int line)
            throws ValidationException {
        boolean hasPseudo = cdsFeature.getAttributes().containsKey(GFF3Attributes.PSEUDO);

        if (hasPseudo) {
            for (GFF3Feature peptide : peptideFeatures) {
                if (!peptide.getAttributes().containsKey(GFF3Attributes.PSEUDO)) {
                    throw new ValidationException(
                            line,
                            PSEUDO_ATTRIBUTE_REQUIRED_VALIDATION.formatted(peptide.getName(), cdsFeature.getName()));
                }
            }
        }
    }
}
