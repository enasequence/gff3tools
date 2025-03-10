package uk.ac.ebi.embl.converter.rules;

import io.vavr.Tuple2;

import java.util.List;
import java.util.ListIterator;
import java.util.Optional;

public interface IConversionRule<I, O> {
  Tuple2<List<O>, List<ConversionError>> from(ListIterator<I> input);
  class ConversionError extends Error {}
}
