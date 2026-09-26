package com.lehnade.mbia.memory.application.listpersonmemories;

import com.lehnade.mbia.memory.application.MemoryView;
import java.util.List;

/** One page of a Person's Memories. */
public record PersonMemoriesView(List<MemoryView> items, int page, int size, long totalElements) {

    public int totalPages() {
        return (int) ((totalElements + size - 1) / size);
    }
}
