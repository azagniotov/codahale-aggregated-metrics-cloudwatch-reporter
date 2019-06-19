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
  public void canEncodeDimensionedNameToString() {
    final DimensionedName dimensionedName = DimensionedName.withName("test")
        .withDimension("key1", "val1")
        .withDimension("key2", "val2")
        .withDimension("key3", "val3")
        .build();

    assertEquals("test[key1:val1,key2:val2,key3:val3]", dimensionedName.encode());
  }

  @Test
  public void canDeriveDimensionedNameFromCurrent() {
    final DimensionedName dimensionedName = DimensionedName.withName("test")
        .withDimension("key1", "val1")
        .withDimension("key2", "val2")
        .withDimension("key3", "val3")
        .build();


    final DimensionedName derivedDimensionedName = dimensionedName
        .withDimension("key3", "new_value")
        .withDimension("key4", "val4").build();

    assertEquals("test[key1:val1,key2:val2,key3:val3]", dimensionedName.encode());
    assertEquals("test[key1:val1,key2:val2,key3:new_value,key4:val4]", derivedDimensionedName.encode());
  }
}