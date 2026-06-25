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
package uk.ac.ebi.embl.gff3tools.sequence.fasta.header.utils;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;

public final class ControlledVocabularyUtils {
    private ControlledVocabularyUtils() {}

    public interface ControlledVocabulary {
        String getValue();
    }

    public enum MolType implements ControlledVocabulary {
        GENOMIC_DNA("genomic DNA"),
        GENOMIC_RNA("genomic RNA"),
        MRNA("mRNA"),
        TRNA("tRNA"),
        RRNA("rRNA"),
        OTHER_RNA("other RNA"),
        OTHER_DNA("other DNA"),
        TRANSCRIBED_RNA("transcribed RNA"),
        VIRAL_CRNA("viral cRNA"),
        UNASSIGNED_DNA("unassigned DNA"),
        UNASSIGNED_RNA("unassigned RNA");

        private final String value;

        MolType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public static Optional<MolType> fromValue(String value) {
            return fromControlledVocabularyValue(MolType.class, value);
        }
    }

    public enum Topology implements ControlledVocabulary {
        LINEAR("linear"),
        CIRCULAR("circular");

        private final String value;

        Topology(String value) {
            this.value = value;
        }

        @Override
        public String getValue() {
            return value;
        }

        public static Optional<Topology> fromValue(String value) {
            return fromControlledVocabularyValue(Topology.class, value);
        }
    }

    public enum ChromosomeType implements ControlledVocabulary {
        CHROMOSOME("chromosome"),
        PLASMID("plasmid"),
        LINKAGE_GROUP("linkage_group"),
        MONOPARTITE("monopartite"),
        SEGMENTED("segmented"),
        MULTIPARTITE("multipartite");

        private final String value;

        ChromosomeType(String value) {
            this.value = value;
        }

        @Override
        public String getValue() {
            return value;
        }

        public static Optional<ChromosomeType> fromValue(String value) {
            return fromControlledVocabularyValue(ChromosomeType.class, value);
        }
    }

    public enum ChromosomeLocation implements ControlledVocabulary {
        MACRONUCLEAR("Macronuclear"),
        NUCLEOMORPH("Nucleomorph"),
        MITOCHONDRION("Mitochondrion"),
        KINETOPLAST("Kinetoplast"),
        CHLOROPLAST("Chloroplast"),
        CHROMOPLAST("Chromoplast"),
        PLASTID("Plastid"),
        VIRION("Virion"),
        PHAGE("Phage"),
        PROVIRAL("Proviral"),
        PROPHAGE("Prophage"),
        VIROID("Viroid"),
        CYANELLE("Cyanelle"),
        APICOPLAST("Apicoplast"),
        LEUCOPLAST("Leucoplast"),
        PROPLASTID("Proplastid"),
        HYDROGENOSOME("Hydrogenosome"),
        CHROMATOPHORE("Chromatophore");

        private final String value;

        ChromosomeLocation(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public static Optional<ChromosomeLocation> fromValue(String value) {
            return fromControlledVocabularyValue(ChromosomeLocation.class, value);
        }
    }

    public static Optional<MolType> normaliseMolType(FastaHeader header) {
        return normalise(header, FastaHeader::getMoleculeType, MolType.class);
    }

    public static Optional<Topology> normaliseTopology(FastaHeader header) {
        return normalise(header, FastaHeader::getTopology, Topology.class);
    }

    public static Optional<ChromosomeType> normaliseChromosomeType(FastaHeader header) {
        return normalise(header, FastaHeader::getChromosomeType, ChromosomeType.class);
    }

    public static Optional<ChromosomeLocation> normaliseChromosomeLocation(FastaHeader header) {
        return normalise(header, FastaHeader::getChromosomeLocation, ChromosomeLocation.class);
    }

    private static <T extends Enum<T> & ControlledVocabulary> Optional<T> normalise(
            FastaHeader header, Function<FastaHeader, String> valueExtractor, Class<T> vocabularyType) {
        if (header == null) {
            return Optional.empty();
        }

        return fromControlledVocabularyValue(vocabularyType, valueExtractor.apply(header));
    }

    private static <T extends Enum<T> & ControlledVocabulary> Optional<T> fromControlledVocabularyValue(
            Class<T> vocabularyType, String value) {
        return Arrays.stream(vocabularyType.getEnumConstants())
                .filter(vocabulary -> vocabulary.getValue().equals(value))
                .findFirst();
    }

    /**
     * Resolves a raw value to its canonical controlled-vocabulary form.
     *
     * <p>The value is first trimmed of leading/trailing whitespace and has dashes replaced with
     * underscores, then matched case-insensitively against the vocabulary's allowed values. When a
     * match is found the canonical value (with its original casing and spacing) is returned;
     * otherwise the result is empty and callers should leave the value untouched.
     *
     * @param vocabularyType the controlled vocabulary to match against
     * @param value the raw value to canonicalise
     * @return the canonical value, or empty if the input does not match any allowed value
     */
    public static <T extends Enum<T> & ControlledVocabulary> Optional<String> canonicalise(
            Class<T> vocabularyType, String value) {
        if (value == null) {
            return Optional.empty();
        }

        String candidate = value.strip().replace('-', '_');
        return Arrays.stream(vocabularyType.getEnumConstants())
                .map(ControlledVocabulary::getValue)
                .filter(canonical -> canonical.equalsIgnoreCase(candidate))
                .findFirst();
    }
}
