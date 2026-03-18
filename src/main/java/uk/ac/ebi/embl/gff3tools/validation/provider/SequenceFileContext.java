package uk.ac.ebi.embl.gff3tools.validation.provider;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class SequenceFileContext {

    List<Path> sequenceFilePath = new ArrayList<Path>();


    public void setSequenceId(String submissionId, List<Path> sequencePaths) {
        this.sequenceFilePath = sequencePaths;
    }

}
