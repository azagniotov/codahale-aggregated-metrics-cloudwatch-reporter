package io.github.azagniotov.metrics.reporter.utils;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;

public class CollectionsUtilsTest {

    @Test
    public void shouldPartitionEmptyList() throws Exception {
        final List<Integer> wholeList = new ArrayList<>();

        List<List<Integer>> partitions = new ArrayList<>(CollectionsUtils.partition(wholeList, 4));
        assertThat(partitions).isEmpty();
    }

    @Test
    public void shouldPartitionWholeList() throws Exception {
        final List<Integer> wholeList = Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8);

        List<List<Integer>> partitions = new ArrayList<>(CollectionsUtils.partition(wholeList, 4));
        assertThat(partitions).hasSize(3);

        assertThat(partitions.get(0)).containsExactly(0, 1, 2, 3);
        assertThat(partitions.get(1)).containsExactly(4, 5, 6, 7);
        assertThat(partitions.get(2)).containsExactly(8);
    }
}