package uk.ac.ebi.embl.converter;

import java.io.BufferedReader;
import java.io.BufferedWriter;

public interface Converter {
    public void convert(BufferedReader reader, BufferedWriter writer) throws ConversionError;
}
