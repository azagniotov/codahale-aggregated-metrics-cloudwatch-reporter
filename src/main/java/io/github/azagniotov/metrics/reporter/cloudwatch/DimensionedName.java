package io.github.azagniotov.metrics.reporter.cloudwatch;

import com.amazonaws.services.cloudwatch.model.Dimension;
import java.util.Collections;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class DimensionedName {
  private static final Pattern dimensionPattern = Pattern.compile("([\\w.-]+)\\[([\\w\\W]+)]");
  private final String name;
  private final Set<Dimension> dimensions;

  private String encoded;

  DimensionedName(final String name, final Set<Dimension> dimensions) {
    this.name = name;
    this.dimensions = Collections.unmodifiableSet(dimensions);
  }

  public static DimensionedName decode(final String encodedDimensionedName) {
    final Matcher matcher = dimensionPattern.matcher(encodedDimensionedName);
    if (matcher.find() && matcher.groupCount() == 2) {
      final DimensionedNameBuilder builder = new DimensionedNameBuilder(matcher.group(1).trim());
      for(String t : matcher.group(2).split(",")) {
        final String[] keyAndValue = t.split("=");
        builder.addDimension(new Dimension()
            .withName(keyAndValue[0].trim())
            .withValue(keyAndValue[1].trim()));
      }
      return builder.build();
    } else {
      return new DimensionedNameBuilder(encodedDimensionedName).build();
    }
  }

  public String getName() {
    return name;
  }

  public Set<Dimension> getDimensions() {
    return dimensions;
  }

  public synchronized String encode() {
    if (this.encoded == null) {
      if (!dimensions.isEmpty()) {
        final StringBuilder sb = new StringBuilder(this.name);
        sb.append('[');
        sb.append(this.dimensions.stream()
            .map(dimension -> dimension.getName() + "=" + dimension.getValue())
            .collect(Collectors.joining(",")));
        sb.append(']');

        this.encoded = sb.toString();
      } else {
        this.encoded = this.name;
      }
    }
    return this.encoded;
  }

  @Override
  public String toString() {
    return this.encode();
  }
}
