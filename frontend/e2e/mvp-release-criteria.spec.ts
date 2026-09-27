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
    // Covered in Phase 2 by phase-2-journey.spec.ts; joins this journey when the next steps exist.
  });
  test.fixme('4. add parents', () => {
    // Covered in Phase 2 by phase-2-journey.spec.ts; joins this journey when the next steps exist.
  });
  test.fixme('5. view tree', () => {
    // Covered in Phase 2 by phase-2-journey.spec.ts; joins this journey when the next steps exist.
  });
  test.fixme('6. add grandparent', () => {
    // Covered in Phase 2 by phase-2-journey.spec.ts; joins this journey when the next steps exist.
  });
  test.fixme('7. see derived kinship', () => {
    // Covered in Phase 2 by phase-2-journey.spec.ts; joins this journey when the next steps exist.
  });
  test.fixme('8. add photo and Story', () => {
    // Story covered in Phase 3 by phase-3-journey.spec.ts; the photo of a Memory waits for OQ-042
    // (phase-3-family-memories.md §3.1). Joins this journey when both exist.
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
    // Family level covered in Phase 1 by family-isolation.spec.ts; Persons, Memories and media covered by the API
    // tests (MemoryAndMediaFamilyIsolationApiTest in Phase 3). Joins this journey with the invited relative.
  });
});
