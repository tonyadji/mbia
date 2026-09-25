import { test } from '@playwright/test';

/**
 * North star: the MVP release journey of mbia-specs/product/mvp.md §28, one step per test, in order. Each step is
 * `test.fixme` until the phase that delivers it replaces it with a real test; the MVP is ready when none is left.
 */
test.describe('MVP release criteria (mvp.md §28)', () => {
  test.describe.configure({ mode: 'serial' });

  test.fixme('1. sign up', () => {
    // Covered in Phase 1 by first-journey.spec.ts; joins this journey when the next steps exist.
  });
  test.fixme('2. create Family', () => {
    // Covered in Phase 1 by first-journey.spec.ts; joins this journey when the next steps exist.
  });
  test.fixme('3. create own Person', () => {
    // Phase 2+: Persons.
  });
  test.fixme('4. add parents', () => {
    // Phase 2+: relationships.
  });
  test.fixme('5. view tree', () => {
    // Phase 2+: tree.
  });
  test.fixme('6. add grandparent', () => {
    // Phase 2+: relationships.
  });
  test.fixme('7. see derived kinship', () => {
    // Phase 2+: kinship engine.
  });
  test.fixme('8. add photo and Story', () => {
    // Phase 2+: memories and media.
  });
  test.fixme('9. invite relative', () => {
    // Phase 2+: invitations.
  });
  test.fixme('10. relative joins', () => {
    // Phase 2+: invitations.
  });
  test.fixme('11. relative links themselves to existing Person', () => {
    // Phase 2+: claim Person.
  });
  test.fixme('12. relative contributes', () => {
    // Phase 2+: members and roles.
  });
  test.fixme('Cross-Family access must fail', () => {
    // Family level covered in Phase 1 by family-isolation.spec.ts; extended to Persons and Memories later.
  });
});
