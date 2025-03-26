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

public record GFF3Directives(List<GFF3Directive> directives) implements IGFF3Feature {
  @Override
  public void writeGFF3String(Writer writer) throws IOException {
    for (GFF3Directive directive : directives) {
      directive.writeGFF3String(writer);
    }
  }

  public record GFF3SequenceRegion(String accession, long start, long end)
      implements GFF3Directive {

    @Override
    public void writeGFF3String(Writer writer) throws IOException {
      writer.write("##sequence-region %s %d %d\n".formatted(accession, start, end));
    }
  }

  public record GFF3Species(String species) implements GFF3Directive {
    @Override
    public void writeGFF3String(Writer writer) throws IOException {
      if (species != null) {
        writer.write("##species %s\n".formatted(species));
      }
    }
  }

  public interface GFF3Directive extends IGFF3Feature {}
}
