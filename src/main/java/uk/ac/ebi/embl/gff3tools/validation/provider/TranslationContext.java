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
package uk.ac.ebi.embl.gff3tools.validation.provider;

import java.nio.file.Path;
import lombok.Builder;
import lombok.Getter;
import uk.ac.ebi.embl.gff3tools.sequence.readers.SubmissionType;

/**
 * Holds all shared translation-related resources for a validation run.
 * and registered via TranslationProvider.
 *
 * <p>Retrieved by validators via:
 * <pre>context.get(TranslationContext.class)</pre>
 */
@Getter
@Builder
public class TranslationContext {

    /** Working directory for this validation run. */
    private final Path processDir;

    /** Temp FASTA file used to store downloaded sequences during translation. */
    private final Path sequenceFastaPath;
}
