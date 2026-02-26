package uk.ac.ebi.embl.gff3tools.validation.context;

import lombok.Builder;
import lombok.Getter;

import java.nio.file.Path;

@Getter
@Builder
public class FileValidationContext {
    private final Path gff3Path;
    private final Path fastaPath;
}
