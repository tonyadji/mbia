import { useLayoutEffect, useRef, type KeyboardEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';
import type { components } from '../api/generated/schema';
import { familyStoryPath, type StoryEntry } from '../memories/storyPath';

type StoryYear = components['schemas']['FamilyStoryYear'];

const ENTRY_CLASS =
  'inline-flex min-h-12 snap-start items-center rounded-full px-4 text-body whitespace-nowrap transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary';

/**
 * The strip of years of the family story (design-guidelines.md §7, mvp.md §20, OQ-064): one entry
 * per year with its number of Memories, oldest first, then the undated Memories when there are any.
 * Only the strip scrolls sideways, by swipe or by keyboard (arrows, Home, End); it opens on the
 * current entry, otherwise on the most recent year. The current entry is marked by a thicker
 * border, bold underlined text and `aria-current`, not by its color only (§9).
 */
export function YearStrip({
  familyId,
  years,
  undatedMemoryCount,
  current,
}: {
  familyId: string;
  years: StoryYear[];
  undatedMemoryCount: number;
  current?: StoryEntry;
}) {
  const { t } = useTranslation('memory');
  const list = useRef<HTMLUListElement>(null);
  const entries: { entry: StoryEntry; label: string }[] = years.map(({ year, memoryCount }) => ({
    entry: year,
    // Digits only, never a thousands separator (localization-and-kinship-labels.md §1).
    label: t('story.entry', { year: String(year), count: memoryCount }),
  }));
  if (undatedMemoryCount > 0) {
    entries.push({
      entry: 'undated',
      label: t('story.undatedEntry', { count: undatedMemoryCount }),
    });
  }
  const currentIndex = entries.findIndex(({ entry }) => entry === current);
  const shown = currentIndex >= 0 ? currentIndex : Math.max(years.length - 1, 0);

  // Scrolls the strip itself, never the page, to show the opening entry in its middle.
  useLayoutEffect(() => {
    const strip = list.current;
    const item = strip?.children[shown];
    if (!strip || !(item instanceof HTMLElement)) return;
    strip.scrollLeft = item.offsetLeft - (strip.clientWidth - item.offsetWidth) / 2;
  }, [shown, entries.length]);

  const onKeyDown = (event: KeyboardEvent<HTMLUListElement>) => {
    const links = Array.from(event.currentTarget.querySelectorAll('a'));
    const index = links.findIndex((link) => link === document.activeElement);
    if (index < 0) return;
    const target = {
      ArrowLeft: index - 1,
      ArrowRight: index + 1,
      Home: 0,
      End: links.length - 1,
    }[event.key];
    if (target === undefined) return;
    event.preventDefault();
    links[Math.min(Math.max(target, 0), links.length - 1)]?.focus();
  };

  return (
    <nav aria-label={t('story.strip')} className="min-w-0">
      <ul
        ref={list}
        onKeyDown={onKeyDown}
        className="relative flex w-full snap-x gap-2 overflow-x-auto overscroll-x-contain p-1"
      >
        {entries.map(({ entry, label }) => {
          const isCurrent = entry === current;
          return (
            <li key={entry} className="shrink-0">
              <Link
                to={familyStoryPath(familyId, entry)}
                aria-current={isCurrent ? 'page' : undefined}
                className={`${ENTRY_CLASS} ${
                  isCurrent
                    ? 'border-2 border-primary bg-surface font-bold text-primary underline decoration-2 underline-offset-4'
                    : 'border border-border bg-surface text-text hover:border-primary'
                }`}
              >
                {label}
              </Link>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}
