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

import lombok.extern.slf4j.Slf4j;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.utils.PseudoLookup;
import uk.ac.ebi.embl.gff3tools.validation.ContextProvider;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;

/**
 * The {@link PseudoLookup} for the annotation being validated, shared by every rule that runs on it.
 *
 * <p>It keeps one annotation (one {@code ###} block) at a time and builds a new lookup when a
 * different annotation object asks. That is enough because each annotation runs all its
 * annotation-level rules before the next one starts, and no rule comes back to an earlier one. A
 * caller that switched back and forth between annotations would rebuild the lookup on every switch:
 * the answers stay right, only the saving is lost.
 *
 * <p>Annotations are told apart by object identity, not accession: one accession can be several
 * {@code ###} blocks, each its own annotation.
 */
@Slf4j
public class PseudoLookupProvider implements ContextProvider<PseudoLookupProvider> {

    private String accession;
    private GFF3Annotation annotation;
    private PseudoLookup lookup;

    @Override
    public PseudoLookupProvider get(ValidationContext context) {
        return this;
    }

    @Override
    public Class<PseudoLookupProvider> type() {
        return PseudoLookupProvider.class;
    }

    /** The lookup for {@code annotation}, built on the first call for it. */
    public PseudoLookup lookupFor(GFF3Annotation annotation, ValidationContext context) {
        if (annotation != this.annotation) {
            this.annotation = annotation;
            this.accession = annotation.getAccession();
            this.lookup = PseudoLookup.build(annotation, context);
            log.debug("Built the pseudo CDS lookup for {}", accession);
        }
        return lookup;
    }

    @Override
    public void close() {
        accession = null;
        annotation = null;
        lookup = null;
    }
}
