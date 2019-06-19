package io.github.azagniotov.metrics.reporter.cloudwatch;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.junit.Assert.*;

import com.amazonaws.services.cloudwatch.model.Dimension;
import org.junit.Test;

public class DimensionedNameTest {
  @Test
  public void canDecodeDimensionedString() {
    final String dimensioned = "test[key1:val1,key2:val2,key3:val3]";

    final DimensionedName dimensionedName = DimensionedName.decode(dimensioned);

    assertEquals("test", dimensionedName.getName());
    assertEquals(3, dimensionedName.getDimensions().size());

    assertThat(dimensionedName.getDimensions(), hasItems(
        new Dimension().withName("key1").withValue("val1"),
        new Dimension().withName("key2").withValue("val2"),
        new Dimension().withName("key3").withValue("val3")));
  }

  @Test
  public void canEncodeDimensionedNameToStering() {
    final DimensionedName dimensionedName = new DimensionedNameBuilder("test")
        .addDimension(new Dimension().withName("key1").withValue("val1"))
        .addDimension(new Dimension().withName("key2").withValue("val2"))
        .addDimension(new Dimension().withName("key3").withValue("val3"))
        .build();

    assertEquals("test[key1:val1,key2:val2,key3:val3]", dimensionedName.encode());
  }
}