---
name: compose-design
description: "Trigger: UI design, redesign, theme, new screen, visual polish, Compose component. Give the Android Compose UI a distinctive, intentional look plus project conventions."
license: Apache-2.0 (derived from anthropics/skills frontend-design; see NOTICE)
metadata:
  author: "sergio.lopezm"
  version: "1.0"
---

## Activation Contract

Load when creating or changing any Compose screen, component, or theme under `app/src/main/java/com/librosleidos/ui/`, or when asked to improve the app's visual design.

## Hard Rules

- Design for this app's subject (a personal log of books and movies the user finished), never a generic template.
- `MaterialTheme` is the technical vehicle only: define custom `ColorScheme`, `Typography`, and `Shapes` in `ui/theme/`. NEVER ship the default purple baseline, default Roboto-only type, or dynamic color as the identity.
- Never hardcode colors, text styles, or dimensions in screens; read them from the theme or spacing tokens.
- Avoid the generic tells listed in `references/design-philosophy.md`.
- Every change MUST satisfy `references/project-conventions.md` (test tags, states, previews, accessibility).
- Spend boldness in one place per screen; keep the rest quiet.

## Decision Gates

| Situation | Action |
|-----------|--------|
| No design direction exists yet | Run the plan pass and write the token plan before any code |
| Theme already defined | Reuse its tokens; extend only with a stated reason |
| User brief pins a look | Follow the brief exactly, even if it is a listed tell |
| Roborazzi is configured | Add or update screenshot tests for each changed screen |
| Roborazzi is not configured | Report it as a gap; do not add the dependency unasked |

## Execution Steps

1. Read `references/design-philosophy.md` and the current `ui/theme/` files.
2. Plan: 4–6 named hex colors (light and dark), typefaces and roles, layout concept with ASCII wireframe, 2–3 principles.
3. Review the plan against the subject; revise any part that a generic prompt would also produce, and say what changed.
4. Build: theme tokens first, then atoms in `ui/components/`, then Screens. Keep Route (stateful) and Screen (stateless) split.
5. Self-critique with previews or screenshots; remove one decoration before finishing.

## Output Contract

Return: the token plan, what was revised in review and why, files changed, and any convention gap (missing test tag, state, or screenshot test).

## References

- `references/design-philosophy.md` — adapted design principles, generic tells, copywriting rules.
- `references/project-conventions.md` — atomic design, states, test tags, accessibility, screenshot tests.
- `NOTICE` and `LICENSE.txt` — attribution and Apache 2.0 terms.
