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
package uk.ac.ebi.embl.converter.gff3;

import java.io.IOException;
import java.io.Writer;
import uk.ac.ebi.embl.converter.exception.WriteException;

public record GFF3Header(String version) implements IGFF3Feature {

    @Override
    public void writeGFF3String(Writer writer) throws WriteException {
        try {
            writer.write("##gff-version %s\n".formatted(version));
        } catch (IOException exception) {
            throw new WriteException(exception);
        }
    }
}
