# Domain docs

This repository uses one domain context: `GLOSSARY.md` at the root and decisions in `docs/adr/`.

## Before exploring domain behavior

1. Read `GLOSSARY.md` for the project's terms.
2. Read ADRs relevant to the area being investigated or changed.
3. Use the defined terms in specifications, tickets, code discussions, and tests.
4. Surface any conflict with an ADR explicitly, identifying the decision and proposed change.

If a referenced domain document is absent, proceed without creating a placeholder. Use domain-modeling when a term or decision is actually resolved; create glossary entries and ADRs lazily.

When a concept is missing from the glossary, check whether an existing term applies. Record a genuine terminology gap for domain-modeling.
