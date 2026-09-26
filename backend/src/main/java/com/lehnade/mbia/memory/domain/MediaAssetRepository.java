package com.lehnade.mbia.memory.domain;

public interface MediaAssetRepository {

    /** Writes a new asset (data-model.md §13). */
    void insert(MediaAsset asset);
}
