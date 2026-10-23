- Feature Name: `fasta_header_dependent_rules`
- Document Date: 2026-10-23
- Last Updated: 2026-10-23

# Summary

Some validation rules and fixes read a sequence's FASTA header: its molecule type, topology or
chromosome fields. This document lists what each of them does when there is no header, whether
because no header source was given or because the source has no header for that sequence.

Only `FASTA_HEADER_MAPPING` fails on a missing header. Every other rule either skips, applies a
more lenient limit, or assumes the sequence is linear.

# Motivation & Rationale

Not every submission has a FASTA header for every sequence (TTENA-304, FASTA header legacy
compatibility). With `FASTA_HEADER_MAPPING` turned off, a sequence without a header has to be
judged on what is actually known about it, not on what a header would have said.

Two rules were changed for this:

- **`GAP_BASES_PERCENTAGE`.** With a header source registered but no header for the sequence, the
  rule used to apply the strict 50% limit. With no header source at all it applied the lenient
  100% limit. Both cases now get the lenient limit, which is what the rule's description already
  said. This reverses a deliberate choice made in TTENA-180.
- **`NO_TERMINAL_GAPS`.** An undeclared topology used to be treated as linear, so leading or
  trailing Ns failed even on a sequence that was really circular. The rule now runs only on a
  sequence declared linear.

The other topology-based rules still assume linear when nothing declares the topology. For them,
a circular sequence has to be declared before origin-spanning coordinates are accepted.

# Usage Guidelines

- **Where headers come from.** In `conversion`, from the JSON headers of the FASTA files given with
  `--sequence` (one per sequence), then from `--fasta-header` (one header used for every sequence
  that has none). A header provider with no sources is not registered at all.
- **`validation` registers no header source.** It accepts `--sequence` and `--fasta-header`, but
  every rule below behaves there as if no header was supplied (see Technical Debt).
- **Accepting sequences without a header.** Turn the mapping check off:
  `--rules FASTA_HEADER_MAPPING:OFF`. Nothing else needs changing.
- **Declaring topology without a header.** Topology is also read from the EMBL ID line (EMBL →
  GFF3 only) and from the master entry (`--master-entry`), so a circular sequence without a header
  can still be declared circular there.

# Detailed Design & Implementation

## Where each value comes from

| Value | Sources, first match wins |
|---|---|
| Topology | EMBL ID line (EMBL → GFF3 only), FASTA header, master entry (`TopologyUtils.resolveTopology`) |
| Molecule type | FASTA header only (`ValidationUtils.getFastaMoleculeType`) |
| Chromosome name, type, location | FASTA header only |

A value the controlled vocabulary does not recognise counts as missing.

## Fails without a header

| Rule | Behaviour |
|---|---|
| `FASTA_HEADER_MAPPING` | Fails when a header source is registered but has no header for the sequence. Skipped when no header source is registered. |

## Skipped without a header

| Rule | Needs |
|---|---|
| `FASTA_HEADER_SYNTAX_VALIDATION` | the header itself |
| `CHROMOSOME_NAME_UNIQUE` | chromosome name |
| `CHROMOSOME_OR_LINKAGE_GROUP_NAME_NOT_UNASSIGNED` | chromosome name and type |
| `PLASMID_CHROMOSOME_NAME_FORMAT` | chromosome name and type |
| `MOLECULE_TYPE_REQUIRED_FEATURE` | molecule type |
| `MOLECULE_TYPE_FORBIDDEN_FEATURE` | molecule type |
| `MRNA_CDS_COMPLEMENT` | molecule type (checks mRNA only) |
| `MRNA_CDS_JOINED_LOCATION` | molecule type (checks mRNA and transcribed RNA only) |
| `MOLECULE_TYPE_ANALYSIS_TYPE` | molecule type |
| `NO_TERMINAL_GAPS` | topology declared linear, by any source |
| `FEATURE_LONGER_THAN_CIRCULAR_SEQUENCE` | topology declared circular, by any source |
| fix `FASTA_HEADER_VALUE_NORMALISATION` | the header itself (logs a warning) |
| fix `CHROMOSOME_NAME` | chromosome name (logs a warning) |

## More lenient without a header

| Rule | With a header | Without a header |
|---|---|---|
| `GAP_BASES_PERCENTAGE` | Fails above 50% Ns, or only at 100% Ns when the header has a chromosome field | Fails only at 100% Ns |

## Assume linear when no source declares the topology

These give the right answer for a linear sequence. For an undeclared circular sequence they are
stricter than they would be with the topology known.

| Rule | Behaviour on an undeclared topology |
|---|---|
| `FEATURE_END_EXCEEDS_SEQUENCE_LENGTH` | A feature may end at most at the sequence length (twice the length on a circular sequence). |
| `GAP_BASES` | A gap is not unwrapped across the origin. A gap running past the end is left to `FEATURE_END_EXCEEDS_SEQUENCE_LENGTH`. |
| `JOINED_LOCATION_ORDER` | Same verdict either way. On a circular sequence the message adds a hint to write the segment in virtual coordinates. |
| fix `TRANSLATION` | A CDS crossing the origin is not unwrapped, so it fails with a `TRANSLATION` error. |
| fix `ANTICODON_ATTRIBUTE_ADD_SEQUENCE` | An anticodon past the sequence end is not read, and gets no `seq:` value. |

## Conversion

Not a rule: in GFF3 → EMBL conversion the header fills the DE line, molecule type, topology and
chromosome qualifiers, after the master entry. Without a header they come from the master entry
or stay unset. See [spec-fasta-header-to-embl-entry-mapping.md](spec-fasta-header-to-embl-entry-mapping.md).

# Technical Debt / Future Considerations

- `validation` accepts `--sequence` and `--fasta-header` but registers neither a header source nor
  a master entry. Header rules never run there, and topology rules always assume linear.
- Molecule type and chromosome fields are read only from the FASTA header, although
  `MasterMetadata` carries both. A master entry cannot stand in for a missing header for those
  rules.

# Testing Strategy

- `NoFastaHeaderForSequenceTest` runs GFF3 through the full engine with stub sequence and header
  providers, where some sequences have no header. With `FASTA_HEADER_MAPPING` on, each header-less
  annotation is reported; with it off, regular coordinates pass. A gene in virtual coordinates past
  the origin is declined without a header, and passes when the header or the master entry declares
  the sequence circular.
- The unit tests of each rule above cover both cases: no header source registered, and a source
  with no header for the sequence.

# Related Documentation & Resources

- [spec-fasta-header-to-embl-entry-mapping.md](spec-fasta-header-to-embl-entry-mapping.md): FASTA
  header fields and where they land in an EMBL entry.
- [2607071200_effective_metadata_resolution.md](2607071200_effective_metadata_resolution.md):
  precedence between master entry and FASTA header.
- [cli-usage-guide.md](cli-usage-guide.md): `--sequence`, `--fasta-header`, `--rules`.
