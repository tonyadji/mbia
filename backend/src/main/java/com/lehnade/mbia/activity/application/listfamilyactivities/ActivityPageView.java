package com.lehnade.mbia.activity.application.listfamilyactivities;

import java.util.List;

public record ActivityPageView(List<ActivityView> items, int page, int size, long totalElements) {

    public int totalPages() {
        return (int) ((totalElements + size - 1) / size);
    }
}
