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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record GFF3Annotation(
        GFF3Directives directives, List<GFF3Feature> features)
        implements IGFF3Feature {
    private void writeFeature(Writer writer, GFF3Feature feature) throws IOException {
        writer.write(feature.getAccession());
        writer.write('\t' + feature.getSource());
        writer.write('\t' + feature.getName());
        writer.write("\t%d".formatted(feature.getStart()));
        writer.write("\t%d".formatted(feature.getEnd()));
        writer.write('\t' + feature.getScore());
        writer.write('\t' + feature.getStrand().toString());
        writer.write('\t' + feature.getPhase());
        writer.write('\t'
                + feature.getAttributes().entrySet().stream()
                        .sorted(Map.Entry.comparingByKey()) // Sort by key
                        .map(entry -> entry.getKey() + "=" + entry.getValue()) // Format k=v
                        .collect(Collectors.joining(";", "", ";")));
        writer.write("\n");
    }

    @Override
    public void writeGFF3String(Writer writer) throws IOException {
        this.directives.writeGFF3String(writer);
        for (GFF3Feature feature : features) {
            writeFeature(writer, feature);
        }
        writer.write('\n');
    }
}
