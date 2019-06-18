package io.github.azagniotov.metrics.reporter.cloudwatch;

import com.amazonaws.services.cloudwatch.model.Dimension;
import java.util.HashSet;
import java.util.Set;

public class DimensionedNameBuilder {
  private final String name;
  private Set<Dimension> dimensions = new HashSet<>();

  public DimensionedNameBuilder(final String name) {
    this.name = name;
  }


  public DimensionedNameBuilder addDimension(final Dimension dimension) {
    this.dimensions.add(dimension);
    return this;
  }

  public DimensionedName build() {
    return new DimensionedName(this.name, this.dimensions);
  }
}
