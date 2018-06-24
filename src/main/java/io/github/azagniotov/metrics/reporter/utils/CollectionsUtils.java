package io.github.azagniotov.metrics.reporter.utils;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class CollectionsUtils {

    public static <T> Collection<List<T>> partition(final Collection<T> wholeCollection, final int partitionSize) {
        final int[] itemCounter = new int[]{0};

        return wholeCollection.stream()
                .collect(Collectors.groupingBy(item -> itemCounter[0]++ / partitionSize))
                .values();
    }
}
