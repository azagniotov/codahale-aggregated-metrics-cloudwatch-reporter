package io.github.azagniotov.metrics.reporter.cloudwatch;

import org.junit.Test;
import software.amazon.awssdk.services.cloudwatch.model.Dimension;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

public class DimensionedNameTest {
    @Test
    public void canDecodeDimensionedString() {
        final String dimensioned = "test[key1:val1,key2:val2,key3:val3]";

        final DimensionedName dimensionedName = DimensionedName.decode(dimensioned);

        assertEquals("test", dimensionedName.getName());
        assertEquals(3, dimensionedName.getDimensions().size());

        assertThat(dimensionedName.getDimensions(), hasItems(
                Dimension.builder().name("key1").value("val1").build(),
                Dimension.builder().name("key2").value("val2").build(),
                Dimension.builder().name("key3").value("val3").build()));
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