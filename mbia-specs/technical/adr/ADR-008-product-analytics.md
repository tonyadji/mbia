# ADR-008 — Product analytics with PostHog (EU)

**Status:** Proposed

## Context

The MVP must measure the funnel defined in `product/mvp.md` §25. Data must stay pseudonymous and the tool must not require heavy operations.

## Decision

- Use PostHog Cloud, EU region.
- Business events (`user_registered`, `family_created`, `person_created`, …) are sent **server-side** by the backend after the transaction commits.
- View events (`tree_viewed`, `person_profile_viewed`) are sent by the frontend.
- The only identifier is the Mbia User UUID; `familyId` is sent as an event property/group. Never send names, emails, Person data, story content or photos.
- Frontend capture is configured without cookies or persistent storage, without session recording and without autocapture.
- All analytics calls go through a small `AnalyticsPort` (backend) / `analytics` module (frontend) so the provider can be replaced.
- Analytics failures never fail a user operation.

## Consequences

- The funnel can be built in PostHog without custom dashboards.
- The exact cookie and consent approach must be reviewed legally before public launch.
