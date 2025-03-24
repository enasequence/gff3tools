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

public record GFF3File(
    GFF3Headers headers, GFF3SourceMetadata metadata, Map<String, List<GFF3Feature>> geneMap, List<GFF3Feature> nonGeneFeatures)
    implements IGFF3Feature {

  private void writeGFF3Feature(Writer writer, GFF3Feature feature) throws IOException {
    writer.write(feature.accession());
    writer.write('\t' + feature.source());
    writer.write('\t' + feature.name());
    writer.write("\t%d".formatted(feature.start()));
    writer.write("\t%d".formatted(feature.end()));
    writer.write('\t' + feature.score());
    writer.write('\t' + feature.strand().toString());
    writer.write('\t' + feature.phase());
    writer.write(
            '\t'
                    + feature.attributes().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey()) // Sort by key
                    .map(entry -> entry.getKey() + "=" + entry.getValue()) // Format k=v
                    .collect(Collectors.joining(";", "", ";")));
    writer.write("\n");
  }

  @Override
  public void writeGFF3String(Writer writer) throws IOException {
    this.headers.writeGFF3String(writer);
    if (metadata != null) {
      this.metadata.writeGFF3String(writer);
    }
    writer.write('\n');
    for (String geneName : geneMap.keySet()) {
      for (GFF3Feature feature : geneMap.get(geneName)) {
        writeGFF3Feature(writer, feature);
      }
    }
    for (GFF3Feature feature : nonGeneFeatures) {
      writeGFF3Feature(writer, feature);
    }
  }
}
