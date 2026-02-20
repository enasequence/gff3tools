package uk.ac.ebi.embl.gff3tools.sequence;

import java.util.Map;

public record SequenceStats(
        long totalBases,
        long totalBasesWithoutNBases,
        long leadingNsCount,
        long trailingNsCount,
        Map<Character, Long> baseCount
) {}
