import { useId, useState } from 'react';
import { Link } from 'react-router';
import { buttonClassName } from '../components/Button';

export interface RelativeChoiceGroup {
  /** Shown above the choices; a group without heading shows its choices only. */
  heading?: string;
  choices: { label: string; to: string }[];
}

/**
 * "Add a relative" (family-tree-ux.md §9.1): a button that reveals the human choices, each opening
 * Add Relative (SCREEN-004) with the relationship already chosen.
 */
export function AddRelativeMenu({
  label,
  groups,
}: {
  label: string;
  groups: RelativeChoiceGroup[];
}) {
  const [open, setOpen] = useState(false);
  const panelId = useId();

  return (
    <div className="flex flex-col gap-3">
      <button
        type="button"
        aria-expanded={open}
        aria-controls={panelId}
        onClick={() => {
          setOpen((value) => !value);
        }}
        className={buttonClassName('secondary', 'sm:w-auto sm:self-start')}
      >
        {label}
      </button>
      <div
        id={panelId}
        hidden={!open}
        className="flex flex-col gap-4 rounded-xl border border-border bg-surface p-4"
      >
        {groups.map((group, index) => (
          <div key={group.heading ?? index} className="flex flex-col gap-2">
            {group.heading && <p className="text-caption text-text-muted">{group.heading}</p>}
            <ul className="flex flex-wrap gap-2">
              {group.choices.map((choice) => (
                <li key={choice.to}>
                  <Link
                    to={choice.to}
                    className="inline-flex min-h-12 items-center rounded-full border border-primary px-4 text-body font-semibold text-primary hover:bg-background focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
                  >
                    {choice.label}
                  </Link>
                </li>
              ))}
            </ul>
          </div>
        ))}
      </div>
    </div>
  );
}
