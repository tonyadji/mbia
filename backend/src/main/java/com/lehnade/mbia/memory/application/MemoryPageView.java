package com.lehnade.mbia.memory.application;

import java.util.List;

/** One page of a Memory list ({@code MemoryPage}). */
public record MemoryPageView(List<MemoryView> items, int page, int size, long totalElements) {

    public int totalPages() {
        return (int) ((totalElements + size - 1) / size);
    }
}
