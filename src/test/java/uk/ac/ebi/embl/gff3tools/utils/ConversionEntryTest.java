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
package uk.ac.ebi.embl.gff3tools.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import io.vavr.Tuple2;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

public class ConversionEntryTest {

    @Test
    public void testParseQualifier() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method method = ConversionEntry.class.getDeclaredMethod("parseQualifier", String.class);
        method.setAccessible(true);

        ConversionEntry entry = new ConversionEntry(new String[] {"id", "term", "definition", "feature"});

        Object res1 = method.invoke(entry, "/q1=\"q1value\"");
        assertEquals(new Tuple2<>("q1", "q1value"), res1);

        Object res2 = method.invoke(entry, "/q1");
        assertEquals(new Tuple2<>("q1", "true"), res2);

        try {
            Object res3 = method.invoke(entry, "one=two=three");
            fail();
        } catch (InvocationTargetException e) {
            assertEquals(
                    "Invalid qualifier format: one=two=three",
                    e.getTargetException().getMessage());
        }
    }
}
