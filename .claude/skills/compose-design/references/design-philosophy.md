# Design philosophy (adapted for Jetpack Compose)

Adapted from Anthropic's `frontend-design` skill (Apache 2.0). See `../NOTICE`.

## Stance

Act as the design lead of a studio known for giving every client a distinct visual identity. The client has rejected templated proposals and pays for a point of view: make deliberate, opinionated choices about palette, typography, and layout specific to this app, and take aesthetic risk when justified.

## Ground the design in the subject

The subject is a personal record of finished books and movies: covers and posters, ratings, short reviews, dates. Draw visual choices from that world (print, editorial, cinema, shelves, tickets, marginalia) and build with realistic content, never lorem ipsum.

## Principles

- **First screen.** The list is what the user sees first. Open with the most characteristic thing of the subject (covers/posters, the act of finishing). A big number with a small label and a gradient accent is the default treatment; use it only if it is truly best.
- **Typography carries personality.** One or two families; if two, clearly distinct. Choose them deliberately (downloadable Google Fonts via `androidx.compose.ui:ui-text-google-fonts` or bundled `res/font`), not the default family. Define a full `Typography` scale with intentional weights and letter spacing. Serif body text gets slightly more line height.
- **Avoid typographic tells:** accenting a single word in a title by color/italic/bold; all-caps labels; unnecessary labels above content.
- **Structure is information.** Dividers, outlines, numbering, and labels must encode meaning. Numbered markers only for real sequences.
- **Motion.** Non-user-triggered motion only to draw attention, and at most one orchestrated moment (e.g. a shared-element cover transition from list to detail). No fade-and-slide on every item. Motion answering a user action (expand, save, delete) is welcome when it shows what changed. Respect the system animator scale (reduced motion).
- **Shapes and elevation.** Vary shape by hierarchy; do not apply one corner radius and one shadow to everything.

## Generic tells to avoid unless the brief asks for them

1. Warm cream background (~#F4F1EA) with high-contrast serif and terracotta accent (~#D97757).
2. Near-black background with one acid-green or vermilion accent.
3. Broadsheet layout with hairline rules, zero radius, dense columns.
4. Card kit: identical rounded cards, one radius everywhere, the same soft grey shadow, gradient washes as decoration.
5. Template chrome: tracked-out all-caps eyebrows, meta strings joined with middle dots ("A · B · C"), "WORD — fragment" labels, tinted near-black (#111) as black, monospace for small data labels, "→" appended to button text.
6. Android-specific: default Material 3 baseline purple, untouched dynamic color as the whole identity, stock `TopAppBar` + FAB + `Card` list with no adaptation.

## Process

1. Plan a compact token system: 4–6 named hex colors (light and dark variants), typefaces and roles, layout concept in one sentence plus ASCII wireframe, alignment rule, 2–3 principles.
2. Review the plan: if any part matches what a similar generic prompt would produce, revise it and state what changed and why.
3. Build following the revised plan.

## Restraint and self-critique

Spend boldness in one place; keep the rest disciplined. Quality floor, without announcing it: works on small and large phones, font scale up to 200%, TalkBack labels, AA contrast in light and dark, reduced motion respected. Review with `@Preview` or screenshots. Before finishing, remove one accessory.

## Copywriting

- Words exist to make the app easier to use. Name things as users understand them.
- Active voice; buttons say exactly what happens ("Save book", not "Submit"). Keep the same verb through the flow ("Delete" → "Deleted").
- Errors explain what happened and how to fix it; they do not apologize and are never vague.
- Empty states invite an action ("Add the first book you finished").
- Sentence case, plain verbs, no filler, one job per text element.
