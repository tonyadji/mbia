import { expect, type Page } from '@playwright/test';

/**
 * Minimum accessibility checks of design-guidelines.md §9 on the current screen: every visible
 * control has an accessible name, and every control that is not a link inside running text is a
 * comfortable touch target (at least 44 px high). The expression runs in the page: the e2e tsconfig
 * has no DOM types.
 */
export async function expectAccessibleControls(page: Page) {
  const problems = await page.evaluate<string[]>(`(() => {
    const problems = [];
    const text = (value) => (value || '').replace(/\\s+/g, ' ').trim();
    const nameOf = (element) =>
      text(element.getAttribute('aria-label')) ||
      text((element.getAttribute('aria-labelledby') || '').split(' ')
        .map((id) => document.getElementById(id)?.textContent || '').join(' ')) ||
      text(element.labels ? Array.from(element.labels).map((label) => label.textContent).join(' ') : '') ||
      text(element.textContent) ||
      text(element.getAttribute('title')) ||
      text(element.querySelector('img[alt]')?.getAttribute('alt'));
    const controls = document.querySelectorAll(
      'button, a[href], input:not([type="hidden"]), select, textarea, summary');
    for (const element of controls) {
      const box = element.getBoundingClientRect();
      if (box.width === 0 || box.height === 0 || getComputedStyle(element).visibility === 'hidden') continue;
      const label = element.outerHTML.slice(0, 120);
      if (!nameOf(element)) problems.push('no accessible name: ' + label);
      const inText = element.tagName === 'A' && element.closest('p, li p, dd');
      const choice = element.type === 'checkbox' || element.type === 'radio';
      // A checkbox or radio is reached through its label, which is the target.
      const target = choice ? element.closest('label') || element : element;
      if (!inText && target.getBoundingClientRect().height < 44) {
        problems.push('touch target under 44 px: ' + label);
      }
    }
    return problems;
  })()`);
  expect(problems).toEqual([]);
}
