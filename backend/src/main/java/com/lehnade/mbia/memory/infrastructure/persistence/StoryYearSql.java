package com.lehnade.mbia.memory.infrastructure.persistence;

/** The SQL of the family story (data-model.md §14, §23.5). */
final class StoryYearSql {

    /**
     * The story year of a Memory: null when its date is UNKNOWN. It must stay identical to the
     * expression of {@code idx_memories_family_story_year} (V012) for that index to be used.
     */
    static final String STORY_YEAR = "COALESCE(EXTRACT(YEAR FROM happened_date)::int, happened_year::int)";

    /** The story year and the number of ACTIVE Memories of a Family, oldest first, undated last. */
    static final String YEARS = "SELECT " + STORY_YEAR + " AS story_year, COUNT(*) AS memory_count FROM memories"
            + " WHERE family_id = :familyId AND status = 'ACTIVE'"
            + " GROUP BY story_year ORDER BY story_year NULLS LAST";

    static final String OF_YEAR = " FROM memories WHERE family_id = :familyId AND status = 'ACTIVE' AND "
            + STORY_YEAR + " = :year";

    /**
     * The ACTIVE Memories of a year: EXACT dates first by date, then the years only in the order
     * they were added, then by id (OQ-064).
     */
    static final String MEMORIES_OF_YEAR = "SELECT *" + OF_YEAR
            + " ORDER BY happened_date ASC NULLS LAST, created_at ASC, id ASC";

    static final String COUNT_OF_YEAR = "SELECT COUNT(*)" + OF_YEAR;

    private StoryYearSql() {}
}
