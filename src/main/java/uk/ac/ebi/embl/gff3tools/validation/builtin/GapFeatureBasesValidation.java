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

import java.util.List;
import java.util.Optional;
import uk.ac.ebi.embl.fastareader.sequenceutils.GapRegion;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.sequence.SequenceLookup;
import uk.ac.ebi.embl.gff3tools.utils.OntologyClient;
import uk.ac.ebi.embl.gff3tools.utils.OntologyTerm;
import uk.ac.ebi.embl.gff3tools.utils.TopologyUtils;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;
import uk.ac.ebi.embl.gff3tools.validation.meta.Gff3Validation;
import uk.ac.ebi.embl.gff3tools.validation.meta.InjectContext;
import uk.ac.ebi.embl.gff3tools.validation.meta.Validation;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationMethod;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationType;

@Gff3Validation(
        name = "GAP_FEATURE_BASES",
        description = "Validates that gap features span only N bases in the sequence")
public class GapFeatureBasesValidation implements Validation {

    private static final String RULE_GAP_BASES = "GAP_BASES";
    private static final String GAP_BASES_MESSAGE =
            "gap or \"assembly_gap\" features must span a set of bases that are only \"n\"."
                    + "\nNo contiguous N-run spans locations %d-%d.";

    @InjectContext
    private ValidationContext context;

    @ValidationMethod(
            rule = RULE_GAP_BASES,
            description = "Gap feature span must contain only N/n bases",
            type = ValidationType.FEATURE)
    public void validateGapBases(GFF3Feature feature, int line) throws ValidationException {
        OntologyClient ontologyClient = context.get(OntologyClient.class);
        Optional<String> soIdOpt = ontologyClient.findTermByNameOrSynonym(feature.getName());
        if (soIdOpt.isEmpty() || !OntologyTerm.GAP.ID.equals(soIdOpt.get())) {
            return;
        }
        SequenceLookup lookup = context.contains(SequenceLookup.class) ? context.get(SequenceLookup.class) : null;
        if (lookup == null) {
            return;
        }
        long start = feature.getStart();
        long end = feature.getEnd();
        try {
            long sequenceLength = lookup.getSequenceLength(feature.accession());
            boolean circular = TopologyUtils.isCircularSequence(feature.accession(), context);
            // No bases past the end of a non-circular sequence: FEATURE_END_EXCEEDS_SEQUENCE_LENGTH
            // (LocationValidation) reports it instead.
            if (!circular && end > sequenceLength) {
                return;
            }
            // Virtual coordinates (past the end of a circular sequence) are checked from base 1.
            for (TopologyUtils.Span span :
                    TopologyUtils.unwrapOriginSpan(start, end, circular ? sequenceLength : null)) {
                List<GapRegion> gapRegions = lookup.getGapRegions(feature.accession(), span.start(), span.end());
                boolean covered =
                        gapRegions.stream().anyMatch(g -> g.startBase <= span.start() && g.endBase >= span.end());
                if (!covered) {
                    throw new ValidationException(RULE_GAP_BASES, line, GAP_BASES_MESSAGE.formatted(start, end));
                }
            }
        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Unable to retrieve gap regions for locations %d-%d: %s".formatted(start, end, e.getMessage()), e);
        }
    }
}
