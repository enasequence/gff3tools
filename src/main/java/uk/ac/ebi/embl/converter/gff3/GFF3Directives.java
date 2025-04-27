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
import java.io.Writer;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import uk.ac.ebi.embl.converter.gff3.reader.GFF3ValidationError;

public record GFF3Directives(List<GFF3Directive> directives) implements IGFF3Feature {
    @Override
    public void writeGFF3String(Writer writer) throws IOException {
        for (GFF3Directive directive : directives) {
            directive.writeGFF3String(writer);
        }
    }

    public record GFF3SequenceRegion(String accession, long start, long end) implements GFF3Directive {
        // This regex provides the following matching roups:
        //  - accession
        //      - accessionID
        //      - accessionVersion (optional)
        //  - start
        //  - end
        static Pattern RX = Pattern.compile(
                "^##sequence-region (?<accession>(?<accessionID>[0-9a-zA-Z]+)([.](?<accessionVersion>[0-9]+))?)\\s(?<start>[0-9]+)\\s(?<end>[0-9]+)\\s*$");

        @Override
        public void writeGFF3String(Writer writer) throws IOException {
            writer.write("##sequence-region %s %d %d\n".formatted(accession, start, end));
        }

        public static GFF3SequenceRegion fromGFF3Reader(BufferedReader bufferedReader)
                throws IOException, GFF3ValidationError {
            bufferedReader.mark(1024);
            String line = bufferedReader.readLine();
            if (line == null || !line.startsWith("##sequence-region")) {
                bufferedReader.reset();
                return null;
            }
            Matcher matcher = RX.matcher(line);
            if (matcher.matches()) {
                String id = matcher.group("accessionID");
                long start = Long.parseLong(matcher.group("start"));
                long end = Long.parseLong(matcher.group("end"));
                return new GFF3SequenceRegion(id, start, end);
            }
            throw new GFF3ValidationError("Invalid sequence-region");
        }
    }

    public record GFF3Species(String species) implements GFF3Directive {
        static Pattern RX = Pattern.compile("^##species (?<species>.+)$");

        @Override
        public void writeGFF3String(Writer writer) throws IOException {
            if (species != null) {
                writer.write("##species %s\n".formatted(species));
            }
        }

        public static GFF3Species fromGFF3Reader(BufferedReader bufferedReader)
                throws IOException, GFF3ValidationError {
            bufferedReader.mark(1024);
            String line = bufferedReader.readLine();
            if (line == null || !line.startsWith("##species")) {
                bufferedReader.reset();
                return null;
            }
            Matcher matcher = RX.matcher(line);
            if (matcher.matches()) {
                String species = matcher.group("species");
                return new GFF3Species(species);
            }
            throw new GFF3ValidationError("Invalid species");
        }
    }

    public interface GFF3Directive extends IGFF3Feature {}
}
