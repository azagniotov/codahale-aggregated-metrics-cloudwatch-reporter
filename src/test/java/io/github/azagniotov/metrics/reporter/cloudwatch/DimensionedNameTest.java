package io.github.azagniotov.metrics.reporter.cloudwatch;

import org.junit.Test;
import software.amazon.awssdk.services.cloudwatch.model.Dimension;

import java.util.regex.Pattern;

import static io.github.azagniotov.metrics.reporter.cloudwatch.DimensionedName.*;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.junit.Assert.*;

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

    @Test
    public void canDecodeExoticNames() {
        Pattern nsPattern = Pattern.compile(pNamespace);
        assertTrue(nsPattern.matcher("Namespace09").matches());
        assertTrue(nsPattern.matcher(" Namespace ").matches());
        assertTrue(nsPattern.matcher("_-./#:").matches());

        Pattern tokenPattern = Pattern.compile(pToken);
        assertTrue(tokenPattern.matcher("!\"#$%&'()*+-./;<=>?@[]^_`{|}~").matches());

        assertTrue(tokenPattern.matcher(" x ").matches());
        assertTrue(tokenPattern.matcher("x y").matches());

        assertFalse(tokenPattern.matcher(",").matches()); // needed as separators
        assertFalse(tokenPattern.matcher(":").matches()); // needed as separators

        assertFalse(tokenPattern.matcher("").matches()); // at least one non-ws char
        assertFalse(tokenPattern.matcher(" \n\t").matches()); // at least one non-ws char
    }
}