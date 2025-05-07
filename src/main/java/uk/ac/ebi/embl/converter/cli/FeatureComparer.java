package uk.ac.ebi.embl.converter.cli;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.embl.api.validation.helper.FlatFileComparator;
import uk.ac.ebi.embl.api.validation.helper.FlatFileComparatorException;
import uk.ac.ebi.embl.api.validation.helper.FlatFileComparatorOptions;

public class FeatureComparer {

    private static final Logger LOG = LoggerFactory.getLogger(FeatureComparer.class);
    public static void main(String[] args) {
        try {

            compare(args[0],args[1]);

        } catch (FlatFileComparatorException e) {
            LOG.error(e.getMessage());
            System.exit(1);
        }
    }

    public static void compare(String file1, String file2) throws FlatFileComparatorException {
        FlatFileComparator flatfileComparator = getFeatureComparator();

        if (!flatfileComparator.compare(file1, file2)) {
            throw new FlatFileComparatorException("File comparison failed:  \n" + file1 + "\n" + file2);
        }
        LOG.info("\n\nFeatures are identical for files: \n" + file1 + "\n" + file2);
    }

    private static FlatFileComparator getFeatureComparator() {
        FeatureComparerOption options = new FeatureComparerOption();
        options.setIgnoreLine("FT   source");
        options.setIgnoreLine("FT                   /organism");
        options.setIgnoreLine("FT                   /plasmid");
        options.setIgnoreLine("FT                   /isolate");
        options.setIgnoreLine("FT                   /mol_type");
        options.setIgnoreLine("FT   region");
        options.setIgnoreLine("FT                   /circular_RNA=true");

        FlatFileComparator flatfileComparator = new FlatFileComparator(options);
        return flatfileComparator;
    }

}

class FeatureComparerOption extends FlatFileComparatorOptions {
    @Override
    public boolean isIgnoreLine(String line) {
        return ! line.startsWith("FT") || super.isIgnoreLine(line);
    }
}
