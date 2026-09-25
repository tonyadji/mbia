import { useTranslation } from 'react-i18next';
import { NavLink } from 'react-router';

/**
 * Primary navigation inside a Family (family-tree-ux.md §4, design-guidelines.md §7). Only `Home`
 * exists for now: Tree, Memories and Members arrive with their features.
 */
export function NavigationBar({ familyId }: { familyId: string }) {
  const { t } = useTranslation('family');

  return (
    <nav
      aria-label={t('navigation.label')}
      className="fixed inset-x-0 bottom-0 border-t border-border bg-surface pb-[env(safe-area-inset-bottom)]"
    >
      <ul className="mx-auto flex w-full max-w-5xl justify-around px-4 sm:px-8">
        <li>
          <NavLink
            to={`/families/${familyId}`}
            end
            className="flex min-h-16 min-w-16 flex-col items-center justify-center gap-1 text-caption text-text-muted focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary aria-[current=page]:font-semibold aria-[current=page]:text-primary"
          >
            <HomeIcon />
            {t('navigation.home')}
          </NavLink>
        </li>
      </ul>
    </nav>
  );
}

function HomeIcon() {
  return (
    <svg aria-hidden="true" viewBox="0 0 24 24" className="size-6" fill="currentColor">
      <path d="M12 3.2 2.5 11a1 1 0 0 0 1.3 1.5l.7-.6V20a1 1 0 0 0 1 1h4.5v-5.5h4V21h4.5a1 1 0 0 0 1-1v-8.1l.7.6a1 1 0 0 0 1.3-1.5L12 3.2Z" />
    </svg>
  );
}
