package com.pischule.mentionbot.util;

import java.util.ArrayList;
import java.util.List;

public final class CollectionUtil {
    private CollectionUtil() {}

    public static <T> List<List<T>> chunked(Iterable<T> items, int chunkSize) {
        List<List<T>> chunks = new ArrayList<>();
        List<T> current = new ArrayList<>(chunkSize);

        for (T item : items) {
            current.add(item);

            if (current.size() == chunkSize) {
                chunks.add(current);
                current = new ArrayList<>(chunkSize);
            }
        }

        if (!current.isEmpty()) {
            chunks.add(current);
        }

        return chunks;
    }
}
