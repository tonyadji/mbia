package com.lehnade.mbia.activity.application.listfamilyactivities;

import com.lehnade.mbia.activity.application.ActivityFeed.FeedItem;

/** One line of the feed, with whether its resource is still ACTIVE (false for members). */
public record ActivityView(FeedItem item, boolean resourceActive) {}
