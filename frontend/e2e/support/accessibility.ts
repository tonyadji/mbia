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

/**
 * The rest of design-guidelines.md §9 on the current screen (PR-57): the page is in the screen's
 * language, every visible text is at least the caption size (13 px) with sufficient contrast
 * against its background (WCAG AA: 4.5:1, 3:1 for large text), and the keyboard reaches every
 * control of the screen, or of its open dialog, each showing where the focus is.
 */
export async function expectReadableScreen(page: Page, language: string) {
  expect(await page.evaluate<string>('document.documentElement.lang')).toBe(language);
  expect(
    await page.evaluate<string[]>(`(() => {
    const problems = [];
    const channels = (color) => (color.match(/[\\d.]+/g) || ['0', '0', '0', '0']).map(Number);
    const luminance = ([r, g, b]) => {
      const linear = (c) => { c /= 255; return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4; };
      return 0.2126 * linear(r) + 0.7152 * linear(g) + 0.0722 * linear(b);
    };
    const background = (element) => {
      for (let node = element; node; node = node.parentElement) {
        const [r, g, b, a = 1] = channels(getComputedStyle(node).backgroundColor);
        if (a > 0) return [r, g, b];
      }
      return [255, 255, 255];
    };
    const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
    const seen = new Set();
    for (let node = walker.nextNode(); node; node = walker.nextNode()) {
      const element = node.parentElement;
      if (!element || seen.has(element) || !node.textContent.trim()) continue;
      seen.add(element);
      const box = element.getBoundingClientRect();
      const style = getComputedStyle(element);
      if (box.width === 0 || box.height === 0 || style.visibility === 'hidden') continue;
      // WCAG exempts inactive controls.
      if (element.closest('[disabled], [aria-disabled="true"]')) continue;
      const label = element.outerHTML.slice(0, 100);
      const size = parseFloat(style.fontSize);
      if (size < 13) problems.push('text under 13 px: ' + label);
      const [r, g, b, a = 1] = channels(style.color);
      const back = background(element);
      const fore = [r, g, b].map((c, i) => c * a + back[i] * (1 - a));
      const [light, dark] = [luminance(fore), luminance(back)].sort((x, y) => y - x);
      const ratio = (light + 0.05) / (dark + 0.05);
      const large = size >= 24 || (size >= 18.66 && Number(style.fontWeight) >= 700);
      if (ratio < (large ? 3 : 4.5)) problems.push('contrast ' + ratio.toFixed(2) + ': ' + label);
    }
    return problems;
  })()`),
  ).toEqual([]);
  await expectKeyboardFocus(page);
}

async function expectKeyboardFocus(page: Page) {
  const count = await page.evaluate<number>(`(() => {
    const scope = document.querySelector('dialog[open], [role="dialog"]') || document.body;
    let index = 0;
    for (const element of scope.querySelectorAll(
        'button, a[href], input:not([type="hidden"]), select, textarea, summary')) {
      const box = element.getBoundingClientRect();
      const inactive = element.disabled || element.closest('[inert]') ||
        (element.type === 'radio' && !element.checked);
      if (box.width === 0 || box.height === 0 || inactive) continue;
      element.setAttribute('data-keyboard-check', String(index++));
    }
    document.activeElement?.blur();
    return index;
  })()`);
  const reached = new Set<string>();
  const unseen: string[] = [];
  for (let press = 0; press < count * 2 + 4 && reached.size < count; press++) {
    await page.keyboard.press('Tab');
    const focused = await page.evaluate<{
      index: string | null;
      label: string;
      visible: boolean;
    }>(`(() => {
      const element = document.activeElement;
      const style = getComputedStyle(element);
      return {
        index: element.getAttribute('data-keyboard-check'),
        label: element.outerHTML.slice(0, 100),
        visible: (style.outlineStyle !== 'none' && parseFloat(style.outlineWidth) > 0) ||
          style.boxShadow !== 'none',
      };
    })()`);
    if (focused.index === null) continue;
    reached.add(focused.index);
    if (!focused.visible) unseen.push('no visible focus: ' + focused.label);
  }
  const unreached = await page.evaluate<string[]>(`(() => {
    const reached = new Set(${JSON.stringify([...reached])});
    const labels = [];
    for (const element of document.querySelectorAll('[data-keyboard-check]')) {
      if (!reached.has(element.getAttribute('data-keyboard-check'))) {
        labels.push('not reached by the keyboard: ' + element.outerHTML.slice(0, 100));
      }
      element.removeAttribute('data-keyboard-check');
    }
    return labels;
  })()`);
  expect([...new Set(unseen), ...unreached]).toEqual([]);
}
