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
package uk.ac.ebi.embl.gff3tools.gff3;

import java.io.IOException;
import java.io.Writer;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import uk.ac.ebi.embl.gff3tools.exception.WriteException;
import uk.ac.ebi.embl.gff3tools.gff3.directives.GFF3SequenceRegion;

@NoArgsConstructor
@Getter
@Setter
public class GFF3Annotation implements IGFF3Feature {
    GFF3SequenceRegion sequenceRegion = null;
    List<GFF3Feature> features = new ArrayList<>();

    private void writeFeature(Writer writer, GFF3Feature feature) throws IOException {
        writer.write(feature.accession());
        writer.write('\t' + feature.getSource());
        writer.write('\t' + feature.getName());
        writer.write("\t%d".formatted(feature.getStart()));
        writer.write("\t%d".formatted(feature.getEnd()));
        writer.write('\t' + feature.getScore());
        writer.write('\t' + feature.getStrand().toString());
        writer.write('\t' + feature.getPhase());
        writeAttributes(writer, feature);
        writer.write("\n");
    }

    private void writeAttributes(Writer writer, GFF3Feature feature) throws IOException {
        writer.write('\t');
        writer.write(feature.getAttributeKeys().stream()
                .sorted(
                        Comparator.comparingInt((String key) -> {
                                    if (key.equals("ID")) return -2; // Highest priority
                                    if (key.equals("Parent")) return -1; // Next
                                    return 0; // Others
                                })
                                .thenComparing(Comparator.naturalOrder()) // Sort others by key
                        )
                .map((key) -> {
                    List<String> attributes = feature.getAttributeList(key).orElse(new ArrayList<>());
                    return encodeAttribute(key, attributes);
                })
                .collect(Collectors.joining(";", "", ";")));
    }

    private static String encodeAttribute(String key, List<String> value) {
        String encodedKey = urlEncode(key);
        // URL-encode each item on the list and concatenate them.
        String encodedValue = value.stream().map(GFF3Annotation::urlEncode).collect(Collectors.joining(","));
        return "%s=%s".formatted(encodedKey, encodedValue);
    }

    public static String urlEncode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", " ");
    }

    @Override
    public void writeGFF3String(Writer writer) throws WriteException {
        try {
            if (this.sequenceRegion != null) {
                this.sequenceRegion.writeGFF3String(writer);
            }
            for (GFF3Feature feature : features) {
                writeFeature(writer, feature);
            }
            writer.write('\n');
        } catch (IOException e) {
            throw new WriteException(e);
        }
    }

    public void addFeature(GFF3Feature feature) {
        features.add(feature);
    }

    public void merge(GFF3Annotation other) {
        if (this.sequenceRegion == null) {
            this.sequenceRegion = other.sequenceRegion;
        }
        this.features.addAll(other.features);
    }

    public String getAccession() {
        if (this.sequenceRegion != null) {
            return this.sequenceRegion.accession();
        } else {
            // If there is no features and no sequence region on the annotation we consider it a bug of our
            // library.
            // All annotations must have either a sequence region or features.
            return this.features.stream()
                    .findFirst()
                    .map(GFF3Feature::accession)
                    .orElseThrow(RuntimeException::new);
        }
    }

    public void removeFeature(GFF3Feature feature) {
        features.remove(feature);
    }
}
