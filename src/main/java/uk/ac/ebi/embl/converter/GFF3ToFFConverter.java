package uk.ac.ebi.embl.converter;

import uk.ac.ebi.embl.api.entry.Entry;
import uk.ac.ebi.embl.api.entry.EntryFactory;
import uk.ac.ebi.embl.api.entry.Text;
import uk.ac.ebi.embl.api.entry.feature.Feature;
import uk.ac.ebi.embl.api.entry.feature.FeatureFactory;
import uk.ac.ebi.embl.api.entry.location.*;
import uk.ac.ebi.embl.api.entry.qualifier.Qualifier;
import uk.ac.ebi.embl.api.entry.qualifier.QualifierFactory;
import uk.ac.ebi.embl.api.entry.sequence.Sequence;
import uk.ac.ebi.embl.api.gff3.GFF3Record;
import uk.ac.ebi.embl.api.gff3.GFF3RecordSet;
import uk.ac.ebi.embl.api.validation.ValidationResult;
import uk.ac.ebi.embl.converter.utils.ConversionEntry;
import uk.ac.ebi.embl.converter.utils.ConversionUtils;
import uk.ac.ebi.embl.flatfile.reader.EntryReader;
import uk.ac.ebi.embl.flatfile.reader.embl.EmblEntryReader;
import uk.ac.ebi.embl.flatfile.writer.embl.EmblEntryWriter;
import uk.ac.ebi.embl.gff3.reader.GFF3FlatFileEntryReader;

import java.io.BufferedReader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class GFF3ToFFConverter {

    private final Map<String, ConversionEntry> featureMap = ConversionUtils.getGFF3ToFFFeatureMap();
    ;
    private final FeatureFactory featureFactory = new FeatureFactory();
    private final LocationFactory locationFactory = new LocationFactory();
    private final QualifierFactory qualifierFactory = new QualifierFactory();
    private final Map<String,String> idToParentGeneMap = new HashMap<>();
    private final Map<String, List<Feature>> idToChildrenMap = new HashMap<>();
    private final Map<String, Feature> idToFeatureMap = new HashMap<>();

    public GFF3ToFFConverter() throws Exception {
    }

    public static void main(String[] args) throws Exception {
        GFF3ToFFConverter gff3ToFFConverter = new GFF3ToFFConverter();
        String filename = "src/test/resources/embl_BN000065/embl_BN000065.gff3";

        try (BufferedReader bufferedReader = Files.newBufferedReader(Path.of(filename))) {
            GFF3FlatFileEntryReader entryReader = new GFF3FlatFileEntryReader(bufferedReader);
            ValidationResult validationResult = entryReader.read();
            GFF3RecordSet recordSet = entryReader.getEntry();
            Writer ffWriter = new StringWriter();
            Entry entry = gff3ToFFConverter.convertRecordSetToEntry(recordSet);
            //new EmblEntryWriter(new GFF3Mapper().mapGFF3ToEntry(recordSet).get(0)).write(ffWriter); // existing incorrect implementation
            new EmblEntryWriter(entry).write(ffWriter);
            ffWriter.close();
            Path path = Paths.get("delete-this.embl");
            Files.write(path, ffWriter.toString().getBytes());
            ValidationResult validationResultAfterConversion = new EmblEntryReader( Files.newBufferedReader(path) ).read();
            System.out.println(validationResultAfterConversion);

        }

    }

    // TODO: Add a wrapper method to make the interface consistent with FFToGFF3Converter
    public Entry convertRecordSetToEntry(GFF3RecordSet recordSet) throws Exception {
        Entry entry = new EntryFactory().createEntry();

        if (recordSet == null || recordSet.getRecords().isEmpty()) return entry;
        List<GFF3Record> records = recordSet.getRecords();
        GFF3Record first = records.get(0);

        // Set accession by trimming the version if it exists
        String accession = first.getSequenceID();
        if (accession.matches(".*\\.\\d+")) {
            accession = accession.substring(0, accession.lastIndexOf("."));
        }
        entry.setPrimaryAccession(accession);

        // Add features
        records.stream()
                .map(record -> {
                    Feature feature = convertRecordToFeature(record);
                    if (feature == null) throw new RuntimeException("Invalid Feature " + record.getType());
                    return feature;
                })
                .forEach(entry::addFeature);

        // update locations
        // Rule: The location of parent will be the join of locations of all children, complemented if strand is "-"
        idToChildrenMap.forEach((id, features) -> {
            CompoundLocation<Location> compoundLocation = new Join<>();
            features.forEach(feature -> feature
                    .getLocations()
                    .getLocations()
                    .forEach(compoundLocation::addLocation)
            );
            Feature feature = idToFeatureMap.get(id);
            if (feature.getLocations().isComplement())
                compoundLocation.setComplement(true);

            feature.setLocations(compoundLocation);

        });
        return entry;
    }

    private Feature convertRecordToFeature(GFF3Record record) {

        // Rule: throw an error on invalid feature name
        if (!featureMap.containsKey(record.getType())) return null;

        // Rule: If more than one feature is found to match, always use the first one
        Feature feature = featureFactory.createFeature(featureMap.get(record.getType()).getSOTerm());

        // Add location - ignoring strand in location because it is handled at the parent level
        Location location = locationFactory.createLocalRange((long) record.getStart(), (long) record.getEnd());
        Order<Location> compoundJoin = new Order<>();
        compoundJoin.addLocation(location);
        feature.setLocations(compoundJoin);

        // Add strand
        if (record.getStrand()<0) feature.getLocations().setComplement(true);


        Map<String, String> attributes = record.getAttributes();
        String parent = attributes.remove("Parent"); // remove parent
        String ID = attributes.remove("ID"); // remove ID


        // Build qualifier list
        List<Qualifier> qualifiers = attributes.entrySet().stream()
                .map(entry -> qualifierFactory.createQualifier(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());

        // Use parent to trace the /gene qualifier if needed
        // Assumption: Only one parent is allowed per feature (as per the INSDC draft)
        String parentGene = null;
        if (parent!=null) {
            parentGene = idToParentGeneMap.get(parent);
            if (parentGene!=null) {
                qualifiers.add(qualifierFactory.createQualifier("gene", parentGene));
                if (ID != null) {
                    idToParentGeneMap.put(ID, parentGene);
                }
            }
        }

        // Add qualifiers
        feature.addQualifiers(qualifiers);

        // Update maps
        if (ID!=null) {
            idToFeatureMap.put(ID, feature);
            // TODO: Check if we need to restrict this to a subset of qualifiers
            if (attributes.get("gene")!=null)
                idToParentGeneMap.put(ID,attributes.get("gene"));
        }
        if (parent!=null && feature.getName().equalsIgnoreCase("exon")) {
            idToChildrenMap.putIfAbsent(parent, new ArrayList<>());
            idToChildrenMap.get(parent).add(feature);
        }

        return feature;
    }

}
