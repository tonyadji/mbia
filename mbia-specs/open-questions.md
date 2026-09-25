# Open Questions

Specification gaps found during implementation. Agents add entries here instead of inventing product behavior (see `AGENTS.md` §1).

When a question is answered, update the relevant spec, then move the entry to **Resolved** with a link to the change.

## Template

```markdown
### OQ-### — Short title

- **Raised by / date:**
- **Context:** where the gap was found (spec section, use case, screen)
- **Question:**
- **Options:** A / B / …
- **Recommendation:**
- **Blocking:** what cannot be built until answered
```

## Open

*(none)*

## Resolved

### OQ-001 — JUnit major version with Spring Boot 4.1

- **Raised by / date:** coding agent (PR-02), 2026-09-25
- **Context:** `technical/stack.md` lists "JUnit 5", but Spring Boot 4.1.1 manages JUnit Jupiter 6.0.3 in its dependency BOM.
- **Question:** force JUnit 5 against the Boot BOM, or use the version Boot manages?
- **Options:** A — use JUnit 6 as managed by Spring Boot / B — override to JUnit 5.x.
- **Recommendation:** A; overriding the Boot BOM risks incompatibilities with Boot test support and Testcontainers 2.
- **Answer:** A (human, 2026-09-25). `technical/stack.md` now reads "JUnit Jupiter (version managed by Spring Boot)"; changed in PR-02.
