
### Non existing qualifier on TSV File
INSDC feature
misc_feature (https://github.com/enasequence/gff3tools/blob/4036126630de6291f176e43c518038a08e643055/src/main/resources/INSDC_SO_mappings.tsv#L33)
SO Term
sequence_feature (https://github.com/enasequence/gff3tools/blob/4036126630de6291f176e43c518038a08e643055/src/main/resources/feature-mapping.tsv#L31)
Issue
The /feat_class qualifier does not exist (https://www.ebi.ac.uk/ena/WebFeat/misc_feature_s.html)

### Non existing feature on INSDC SO Mapping File

SO Term
U14_snRNA  (https://github.com/enasequence/gff3tools/blob/4036126630de6291f176e43c518038a08e643055/src/main/resources/INSDC_SO_mappings.tsv#L115)
Issue
Term does not exist on INSDC mapping file

### Non existing term on TSV file / different mapping on files

INSDC feature
variation
SO Term
copy_number_variation | sequence_alteration
Issue
TSV provides mapping with qualifier, INSDC_SO_mappings provides direct mapping to different values

### TSV mapping requires more matching qualifiers

INSDC feature
protein_bind (https://github.com/enasequence/gff3tools/blob/4036126630de6291f176e43c518038a08e643055/src/main/resources/INSDC_SO_mappings.tsv#L81)

SO Term
protein_binding_site (https://github.com/enasequence/gff3tools/blob/4036126630de6291f176e43c518038a08e643055/src/main/resources/feature-mapping.tsv#L144)

Issue
TSV file requires the /bound_moiety qualifier but the INSDC mapping does not


