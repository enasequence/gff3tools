package uk.ac.ebi.embl.converter;

public interface IConversionRule<I, O> {
  O from(I input) throws ConversionError;
  class ConversionError extends Error {}
}
