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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@RequiredArgsConstructor
@Getter
@Setter
public class GFF3Feature {

  final Optional<String> id;
  final Optional<String> parentId;
  final String accession;
  final String source;
  final String name;
  final long start;
  final long end;
  final String score;
  final String strand;
  final String phase;
  final Map<String, String> attributes;

  List<GFF3Feature> children = new ArrayList<>();
  GFF3Feature parent;

  public void addChild(GFF3Feature child) {
    children.add(child);
  }
  public boolean hasChildren() {
    return !children.isEmpty();
  }
}
