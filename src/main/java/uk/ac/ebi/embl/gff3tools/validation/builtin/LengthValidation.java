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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.utils.ConversionUtils;
import uk.ac.ebi.embl.gff3tools.utils.OntologyClient;
import uk.ac.ebi.embl.gff3tools.utils.OntologyTerm;
import uk.ac.ebi.embl.gff3tools.validation.Validation;
import uk.ac.ebi.embl.gff3tools.validation.meta.Gff3Validation;
import uk.ac.ebi.embl.gff3tools.validation.meta.RuleSeverity;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationMethod;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationType;

@Gff3Validation(name = "LENGTH")
public class LengthValidation extends Validation {

    private static final long INTRON_FEATURE_MIN_LENGTH = 10;
    private static final long EXON_FEATURE_MIN_LENGTH = 15;

    private static final String INVALID_PROPEPTIDE_LENGTH_MESSAGE =
            "Propeptide feature length must be a multiple of 3 for accession \"%s\"";
    private static final String INVALID_INTRON_LENGTH_MESSAGE = "Intron feature length is invalid for accession \"%s\"";
    private static final String INVALID_EXON_LENGTH_MESSAGE = "Exon feature length is invalid for accession \"%s\"";

    private static final String INVALID_CDS_INTRON_LENGTH_MESSAGE =
            "Intron usually expected to be at least 10 nt long. Please check accuracy and Use one of the following options for annotation: \n /artificial_location=\"heterogeneous population sequenced\" \n OR \n /artificial_location=\"low-quality sequence region\". \n Alternatively, use where appropriate: \n /pseudo, /pseudogene, /trans_splicing, /ribosomal_slippage";

    private final OntologyClient ontologyClient = ConversionUtils.getOntologyClient();
    private final Map<String, Map<String, List<GFF3Feature>>> annotationCds = new HashMap<>();

    @ValidationMethod(rule = "INTRON_LENGTH", type = ValidationType.FEATURE)
    public void validateIntronLength(GFF3Feature feature, int line) throws ValidationException {
        String featureName = feature.getName();
        long length = feature.getLength();
        Optional<String> soIdOpt = ontologyClient.findTermByNameOrSynonym(featureName);
        if (soIdOpt.isEmpty()) {
            return;
        }
        String soId = soIdOpt.get();
        if (ontologyClient.isSelfOrDescendantOf(soId, OntologyTerm.INTRON.ID) && length < INTRON_FEATURE_MIN_LENGTH) {
            throw new ValidationException(line, INVALID_INTRON_LENGTH_MESSAGE.formatted(feature.accession()));
        }

        boolean isCds =
                OntologyTerm.CDS.ID.equals(soId) || ontologyClient.isSelfOrDescendantOf(soId, OntologyTerm.CDS.ID);

        if (!isCds) return;

        if (isPseudo(feature)
                || feature.hasAttribute(GFF3Attributes.RIBOSOMAL_SLIPPAGE)
                || feature.hasAttribute(GFF3Attributes.TRANS_SPLICING)) {
            return;
        }

        String accession = feature.accession();
        String attributeId = feature.getAttributeByName(GFF3Attributes.ATTRIBUTE_ID);

        annotationCds
                .computeIfAbsent(accession, k -> new HashMap<>())
                .computeIfAbsent(attributeId, k -> new ArrayList<>())
                .add(feature);
    }

    @ValidationMethod(rule = "CDS_INTRON_LENGTH", type = ValidationType.ANNOTATION)
    public void validateIntronLengthWithinCDS(GFF3Annotation gff3Annotation, int line) throws ValidationException {

        Map<String, List<GFF3Feature>> cdsFeaturesMap = annotationCds.remove(gff3Annotation.getAccession());

        if (cdsFeaturesMap == null) return;

        for (Map.Entry<String, List<GFF3Feature>> entry : cdsFeaturesMap.entrySet()) {
            validateCdsIntronLength(entry.getValue(), line);
        }

        cdsFeaturesMap.clear();
    }

    private void validateCdsIntronLength(List<GFF3Feature> cdsList, int line) throws ValidationException {

        if (cdsList.size() <= 1) {
            return;
        }
        cdsList.sort(Comparator.comparingLong(GFF3Feature::getStart));

        for (int i = 1; i < cdsList.size(); i++) {
            GFF3Feature prev = cdsList.get(i - 1);
            GFF3Feature curr = cdsList.get(i);
            long intron = curr.getStart() - prev.getEnd();
            if (intron >= 0 && intron < 10) {
                boolean artificial = prev.hasAttribute(GFF3Attributes.ARTIFICIAL_LOCATION)
                        || curr.hasAttribute(GFF3Attributes.ARTIFICIAL_LOCATION);

                if (!artificial && !isPseudo(curr)) {
                    throw new ValidationException(line, INVALID_CDS_INTRON_LENGTH_MESSAGE);
                }
            }
        }
        cdsList.clear();
    }

    @ValidationMethod(rule = "EXON_LENGTH", type = ValidationType.FEATURE, severity = RuleSeverity.WARN)
    public void validateExonLength(GFF3Feature feature, int line) throws ValidationException {
        String featureName = feature.getName();
        long length = feature.getLength();

        Optional<String> soIdOpt = ontologyClient.findTermByNameOrSynonym(featureName);
        if (soIdOpt.isEmpty()) {
            return;
        }
        String soId = soIdOpt.get();
        if (ontologyClient.isSelfOrDescendantOf(soId, OntologyTerm.EXON.ID) && length < EXON_FEATURE_MIN_LENGTH) {
            throw new ValidationException(line, INVALID_EXON_LENGTH_MESSAGE.formatted(feature.accession()));
        }
    }

    @ValidationMethod(rule = "PROPEPTIDE_LENGTH", type = ValidationType.FEATURE)
    public void validatePropeptideLength(GFF3Feature feature, int line) throws ValidationException {
        String featureName = feature.getName();
        Optional<String> soIdOpt = ontologyClient.findTermByNameOrSynonym(featureName);
        if (soIdOpt.isEmpty()) {
            return;
        }
        String soId = soIdOpt.get();
        if (!OntologyTerm.PROPEPTIDE.ID.equals(soId)) {
            return;
        }
        if (!feature.hasAttribute(GFF3Attributes.TRANSL_EXCEPT)
                && !feature.hasAttribute(GFF3Attributes.EXCEPTION)
                && !feature.hasAttribute(GFF3Attributes.RIBOSOMAL_SLIPPAGE)
                && feature.getLength() % 3 != 0) {
            throw new ValidationException(line, INVALID_PROPEPTIDE_LENGTH_MESSAGE.formatted(feature.accession()));
        }
    }

    public boolean isPseudo(GFF3Feature feature) {
        Optional<String> soIdOpt = ontologyClient.findTermByNameOrSynonym(feature.getName());
        if (soIdOpt.isEmpty()) {
            return false;
        }
        String soId = soIdOpt.get();
        if (ontologyClient.isSelfOrDescendantOf(soId, OntologyTerm.PSEUDOGENIC_REGION.ID)) {
            return true;
        }
        return feature.hasAttribute(GFF3Attributes.PSEUDO) || feature.hasAttribute(GFF3Attributes.PSEUDOGENE);
    }
}
