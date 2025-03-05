package uk.ac.ebi.embl.converter.rules;

import io.vavr.control.Either;

public interface IConversionRule<I, O> {
  O from(I input) throws ConversionError;
  class ConversionError extends Error {}
}
