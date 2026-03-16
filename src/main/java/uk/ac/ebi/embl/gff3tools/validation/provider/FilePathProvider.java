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

import uk.ac.ebi.embl.gff3tools.validation.ContextProvider;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;

/** Provider that supplies a pre-built FilePathContext to the validation framework. */
public class FilePathProvider implements ContextProvider<FilePathContext> {

    private final FilePathContext filePathContext;

    public FilePathProvider(FilePathContext filePathContext) {
        this.filePathContext = filePathContext;
    }

    @Override
    public FilePathContext get(ValidationContext context) {
        return filePathContext;
    }

    @Override
    public Class<FilePathContext> type() {
        return FilePathContext.class;
    }
}
