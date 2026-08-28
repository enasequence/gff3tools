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

import java.util.Locale;
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

    /** Entry data classes that identify a genome submission.*/
    private static final Set<String> GENOME_DATA_CLASSES = Set.of("WGS", "STD");

    /**
     * The molecule types an assembly is submitted under: genomic DNA for most of them, genomic RNA
     * for RNA-virus genomes, viral cRNA for certain viral submissions. A transcript-level molecule
     * type - mRNA, rRNA, tRNA and the rest - means the entry is not a genome, whatever data class it
     * carries.
     */
    private static final Set<MolType> ASSEMBLY_MOLECULE_TYPES =
            Set.of(MolType.GENOMIC_DNA, MolType.GENOMIC_RNA, MolType.VIRAL_CRNA);

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
                || hasLocusTagExemptFeature(gff3Annotation)
                || isVirus(accession)
                || !hasNonGapFeatures(gff3Annotation)
                || hasLocusTag(gff3Annotation)) {
            return;
        }

        throw new ValidationException(line, LOCUS_TAG_EXISTS_MESSAGE);
    }

    /**
     * A genome submission is identified from caller-supplied context, never from the GFF3 file
     * itself, which carries no such marker. Either signal is enough: the analysis type when the
     * caller registered an {@link AnalysisContext}, or the entry data class when a master entry is
     * available. A caller may supply one without the other.
     *
     * <p>Both providers are auto-registered by the classpath scan, but with nothing in them unless
     * a caller supplies it: the analysis type defaults to {@link AnalysisType#UNKNOWN} and the
     * metadata provider has no sources until {@code --master-entry} is given. So a plain
     * {@code gff3tools validation <file>} resolves to neither signal and the rule does not apply —
     * firing there would reject single-sequence submissions that never needed a locus_tag.
     *
     * <p>The molecule type is a veto rather than a signal of its own: it cannot tell a genome from a
     * single annotated gene, both of which are genomic DNA, but a declared transcript-level molecule
     * type rules a genome out. It is only consulted when a FASTA header supplies one.
     */
    private boolean isGenomeSubmission(String accession) {
        AnalysisType analysisType = context.contains(AnalysisContext.class)
                ? context.get(AnalysisContext.class).getAnalysisType()
                : AnalysisType.UNKNOWN;
        switch (analysisType) {
            case TRANSCRIPTOME_ASSEMBLY, SEQUENCE_FLATFILE ->  {
                if (hasNonAssemblyMoleculeType(accession)) {return false;}
                return false;
            }
            case SEQUENCE_ASSEMBLY ->   {
                if (hasNonAssemblyMoleculeType(accession)) {return false;}
                return true;
            }
            case UNKNOWN ->   {
                return ValidationUtils.getMasterMetadata(accession, context)
                    .map(MasterMetadata::getEffectiveDataClass)
                    .map(dataClass -> GENOME_DATA_CLASSES.contains(dataClass.toUpperCase(Locale.ROOT)))
                    .orElse(false);}
            default ->  {throw new IllegalStateException("Unexpected value: " + analysisType);}
        }
    }

    /** Whether a molecule type is declared and is one an assembly is never submitted under. */
    private boolean hasNonAssemblyMoleculeType(String accession) {
        return ValidationUtils.getMoleculeType(accession, context)
                .filter(molType -> !ASSEMBLY_MOLECULE_TYPES.contains(molType))
                .isPresent();
    }

    /**
     * Whether the entry carries a feature that exempts it from the requirement. ENA drops the whole
     * entry from the check when a GFF3 equivalent of repeat_region or misc_feature is present.
     *
     * Equates behaviour in {@code sequencetools} for {}
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
            if ( // features that replace repeat_region, descendants included
            ontologyClient.isSelfOrDescendantOf(soId, OntologyTerm.REPEAT_REGION.ID)
                    // features that replace misc_feature in gff3, which is another
                    || OntologyTerm.FEATURE.ID.equals(soId)
                    || OntologyTerm.BIOLOGICAL_REGION.ID.equals(soId)
                    || OntologyTerm.SEQUENCE_COMPARISON.ID.equals(soId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Viruses are exempt. ENA asks its taxonomy client whether the organism is a child of
     * "Viruses"; the lineage on the master entry answers the same question without a lookup, and
     * {@link TaxonProvider} covers callers that supply taxonomy but no master entry.
     *
     * <p>An organism that cannot be resolved is not treated as a virus: ENA's check falls through
     * to the locus_tag test when it has no source feature either, and guessing the other way would
     * silently exempt every entry a caller supplies no taxonomy for.
     */
    private boolean isVirus(String accession) {
        Optional<String> lineage =
                ValidationUtils.getMasterMetadata(accession, context).map(MasterMetadata::getLineage);

        if (lineage.isEmpty() && context.contains(TaxonProvider.class)) {
            lineage = context.get(TaxonProvider.class).resolve(accession).map(Taxon::getLineage);
        }

        return lineage.filter(LocusTagExistsValidation::isVirusLineage).isPresent();
    }

    private static boolean isVirusLineage(String lineage) {
        return VIRUS_LINEAGE_DOMAIN.equalsIgnoreCase(lineage.split(";", 2)[0].trim());
    }

    /**
     * Mirrors {@code SequenceEntryUtils.hasAnnotation}: an unannotated contig has nothing to tag.
     * Gaps do not count, nor does the whole-sequence {@code region} line.
     */
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
