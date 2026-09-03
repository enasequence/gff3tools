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

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.metadata.MasterMetadata;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.utils.ControlledVocabularyUtils.MolType;
import uk.ac.ebi.embl.gff3tools.utils.OntologyClient;
import uk.ac.ebi.embl.gff3tools.utils.OntologyTerm;
import uk.ac.ebi.embl.gff3tools.utils.ValidationUtils;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;
import uk.ac.ebi.embl.gff3tools.validation.meta.Gff3Validation;
import uk.ac.ebi.embl.gff3tools.validation.meta.InjectContext;
import uk.ac.ebi.embl.gff3tools.validation.meta.Validation;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationMethod;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationPriority;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationType;
import uk.ac.ebi.embl.gff3tools.validation.provider.AnalysisContext;
import uk.ac.ebi.embl.gff3tools.validation.provider.AnalysisType;
import uk.ac.ebi.embl.gff3tools.validation.provider.TaxonProvider;
import uk.ac.ebi.ena.taxonomy.taxon.Taxon;

@Gff3Validation(name = "LOCUS_TAG")
public class LocusTagExistsValidation implements Validation {

    public static final String LOCUS_TAG_EXISTS_RULE = "LOCUS_TAG_EXISTS";

    private static final String LOCUS_TAG_EXISTS_MESSAGE =
            "/locus_tag must exist for annotated contigs/scaffolds/chromosomes.";

    /** Molecule types valid on a genome sequence. Any other declared value rules the entry out. */
    private static final Set<MolType> ASSEMBLY_MOLECULE_TYPES =
            Set.of(MolType.GENOMIC_DNA, MolType.GENOMIC_RNA, MolType.VIRAL_CRNA, MolType.OTHER_DNA, MolType.OTHER_RNA);

    /** The domain that opens a viral lineage, e.g. "Viruses; Riboviria; ...". */
    private static final String VIRUS_LINEAGE_DOMAIN = "Viruses";

    @InjectContext
    private ValidationContext context;

    @ValidationMethod(
            rule = LOCUS_TAG_EXISTS_RULE,
            description = "Check that annotated entries of a genome submission carry a locus_tag",
            type = ValidationType.ANNOTATION,
            priority = ValidationPriority.NORMAL)
    public void validateLocusTagExists(GFF3Annotation gff3Annotation, int line) throws ValidationException {
        String accession = gff3Annotation.getAccession();

        if (!isGenomeSubmission(accession)
                || !hasNonGapFeatures(gff3Annotation) // excludes FASTA submissions by default
                || hasLocusTagExemptFeature(gff3Annotation)
                || isVirus(accession) // for seqIds in GFF3, we can check taxId
                || hasLocusTag(gff3Annotation)) {
            return;
        }

        throw new ValidationException(LOCUS_TAG_EXISTS_RULE, line, LOCUS_TAG_EXISTS_MESSAGE);
    }

    /**
     * Only the caller can tell us this is an assembly submission - nothing in a GFF3 marks one.
     * {@link AnalysisContext} is classpath-scanned and defaults to {@link AnalysisType#UNKNOWN}, so
     * the rule stays inert unless a caller registers a real analysis type.
     */
    private boolean isGenomeSubmission(String accession) {
        AnalysisType analysisType = context.contains(AnalysisContext.class)
                ? context.get(AnalysisContext.class).getAnalysisType()
                : AnalysisType.UNKNOWN;
        return analysisType == AnalysisType.SEQUENCE_ASSEMBLY && !hasNonAssemblyMoleculeType(accession);
    }

    /** Whether a molecule type is declared and is one an assembly is never submitted under. */
    private boolean hasNonAssemblyMoleculeType(String accession) {
        return ValidationUtils.getMoleculeType(accession, context)
                .filter(molType -> !ASSEMBLY_MOLECULE_TYPES.contains(molType))
                .isPresent();
    }

    /**
     * One repeat_region or misc_feature exempts the whole entry, not just that feature - matching
     * the {@code excludeFeatureCheckList} early return in {@code sequencetools}.
     */
    private boolean hasLocusTagExemptFeature(GFF3Annotation gff3Annotation) {
        OntologyClient ontologyClient = context.get(OntologyClient.class);
        for (GFF3Feature feature : gff3Annotation.getFeatures()) {
            if (feature == null) {
                continue;
            }
            Optional<String> soIdOpt = ontologyClient.findTermByNameOrSynonym(feature.getName());
            if (soIdOpt.isEmpty()) {
                continue;
            }
            String soId = soIdOpt.get();
            // repeat_region and its descendants, plus the SO terms that stand in for misc_feature
            if (ontologyClient.isSelfOrDescendantOf(soId, OntologyTerm.REPEAT_REGION.ID)
                    || OntologyTerm.FEATURE.ID.equals(soId)
                    || OntologyTerm.BIOLOGICAL_REGION.ID.equals(soId)
                    || OntologyTerm.SEQUENCE_COMPARISON.ID.equals(soId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Viruses are out of scope. The organism is resolved through {@link TaxonProvider} first - the
     * downstream implementation resolves a taxId or scientific name against the ENA taxonomy API and
     * caches per organism, so a whole assembly costs one lookup - and only falls back to the master
     * entry's lineage, which most submissions do not carry. Core ships no {@link TaxonProvider}
     * implementation, so both may be absent.
     *
     * <p>A resolved taxon is asked with {@link Taxon#isChildOf}, which matches any element of the
     * lineage - the same call ENA's own check reaches through
     * {@code TaxonomyClient.isChildOf(name, "Viruses")}, and it does not assume the taxonomy API
     * returns a domain-first lineage the way the master entry's EMBL OC line does. Going through the
     * already-resolved taxon also keeps the provider's cache and avoids the client's unchecked
     * TaxonomyException.
     *
     * <p>Either source saying virus is enough: a taxon that resolves without a lineage answers
     * nothing, and should not cost an exemption the master entry can still justify. An organism
     * neither source can place is not treated as a virus, so missing taxonomy never silently exempts
     * an entry.
     */
    private boolean isVirus(String accession) {
        if (resolveTaxon(accession)
                .filter(taxon -> taxon.isChildOf(VIRUS_LINEAGE_DOMAIN))
                .isPresent()) {
            return true;
        }

        return ValidationUtils.getMasterMetadata(accession, context)
                .map(MasterMetadata::getLineage)
                .filter(LocusTagExistsValidation::isVirusLineage)
                .isPresent();
    }

    private Optional<Taxon> resolveTaxon(String accession) {
        return context.contains(TaxonProvider.class)
                ? context.get(TaxonProvider.class).resolve(accession)
                : Optional.empty();
    }

    private static boolean isVirusLineage(String lineage) {
        return VIRUS_LINEAGE_DOMAIN.equalsIgnoreCase(lineage.split(";", 2)[0].trim());
    }

    /** An unannotated entry has nothing to tag: gaps and the whole-sequence region line do not count. */
    private boolean hasNonGapFeatures(GFF3Annotation gff3Annotation) {
        OntologyClient ontologyClient = context.get(OntologyClient.class);
        return gff3Annotation.getFeatures().stream().filter(Objects::nonNull).anyMatch(feature -> ontologyClient
                .findTermByNameOrSynonym(feature.getName())
                .map(soId -> !OntologyTerm.REGION.ID.equals(soId)
                        && !ontologyClient.isSelfOrDescendantOf(soId, OntologyTerm.GAP.ID))
                .orElse(true));
    }

    private boolean hasLocusTag(GFF3Annotation gff3Annotation) {
        return gff3Annotation.getFeatures().stream()
                .filter(Objects::nonNull)
                .map(feature -> feature.getAttribute(GFF3Attributes.LOCUS_TAG).orElse(null))
                .anyMatch(locusTag -> locusTag != null && !locusTag.isBlank());
    }
}
