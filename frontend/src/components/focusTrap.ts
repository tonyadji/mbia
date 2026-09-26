const FOCUSABLE =
  'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), summary, [tabindex]:not([tabindex="-1"])';

/**
 * Keeps Tab and Shift+Tab inside an open dialog (`aria-modal`): from the last control Tab goes back
 * to the first, and the reverse (design-guidelines.md §9, keyboard support).
 */
export function trapTab(event: KeyboardEvent, panel: HTMLElement | null) {
  if (event.key !== 'Tab' || panel === null) return;
  const controls = Array.from(panel.querySelectorAll<HTMLElement>(FOCUSABLE)).filter(
    (control) => !control.closest('[hidden]'),
  );
  const first = controls[0];
  const last = controls[controls.length - 1];
  if (first === undefined || last === undefined) {
    event.preventDefault();
    return;
  }
  const active = document.activeElement;
  if (event.shiftKey && (active === first || active === panel || !panel.contains(active))) {
    event.preventDefault();
    last.focus();
  } else if (!event.shiftKey && (active === last || !panel.contains(active))) {
    event.preventDefault();
    first.focus();
  }
}
