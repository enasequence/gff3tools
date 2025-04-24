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

import java.io.BufferedReader;
import java.io.IOException;

public class GFF3Reader {
    BufferedReader reader;
    GFF3Feature parentFeature = null;

    public GFF3Reader(BufferedReader reader) {
        this.reader = reader;
    }

    public GFF3Feature read() throws IOException {
        String line = this.reader.readLine();
        if (this.parentFeature == null && line != null) {
            this.parentFeature = GFF3Feature.fromString(line);
        }

        while (line != null) {
            GFF3Feature feature = GFF3Feature.fromString(line);
            if (feature.getParentId().stream().allMatch((id) -> id.equals(parentFeature.getId()))) {
                // Child feature.
                feature.setParent(this.parentFeature);
                this.parentFeature.addChild(feature);
            } else {
                // We parsed a feature that is not a child. Return current parent and set new feature as
                GFF3Feature parentFeature = this.parentFeature;
                this.parentFeature = feature;
                return parentFeature;
            }
            line = this.reader.readLine();
        }

        GFF3Feature parentFeature = this.parentFeature;
        this.parentFeature = null;
        return parentFeature;
    }
}
