---
name: weekly-demo
description: "Trigger: weekly demo, demo semanal, presentación semanal, client demo. Build the Spanish weekly client-demo .pptx from the Sngular template and GitHub data."
license: MIT
metadata:
  author: "sergio.lopezm"
  version: "1.0"
---

## Activation Contract

Load when asked to prepare, update or regenerate the weekly client demo deck for Libros Leídos.

## Hard Rules

- Build ONLY from the Sngular template at `~/Desktop/libros-leidos-demos/assets/sngular-template.pptx` via `assets/build_deck.py`; never restyle by hand. Follow `references/brand.md`.
- Never copy the template, decks, screenshots or brand assets into this repo (it is public). Outputs go to `~/Desktop/libros-leidos-demos/`.
- Every number in the deck MUST come from `collect.sh` output or a verifiable repo source (test report, `openspec/`). Never invent data or fake screenshots.
- Deck copy in neutral professional Spanish; few words per slide; speaker notes on every slide.
- Do not commit or push unless the user asks.

## Decision Gates

| Situation | Action |
|-----------|--------|
| Emulator/app capture fails or exceeds ~15 min | Leave `demo.screens` empty; the slide shows `demo.scope` + `demo.note` |
| A list has more than 4 items | Keep the 4 most relevant; move the rest to notes |
| Template slide or shape ids changed | Update `SRC`/shape ids in `build_deck.py`, then rebuild |

## Execution Steps

1. `assets/collect.sh [YYYY-MM-DD Monday] > ~/Desktop/libros-leidos-demos/<YYYY>-W<NN>-github.json`.
2. Optional screenshots: boot AVD `Phone_Vitaly` headless, `./gradlew :app:installDebug`, launch `com.sergiolopez.rincondelectura`, `adb exec-out screencap -p`; save under `~/Desktop/libros-leidos-demos/<YYYY>-W<NN>-screens/`; `adb emu kill`.
3. Copy last week's `<YYYY>-W<NN>-demo-semanal.json` and rewrite every value from step 1 data (schema: `references/deck-structure.md`).
4. Build: `python3 assets/build_deck.py <week>.json <week>.pptx` (venv with `assets/requirements.txt`).
5. QA: `validate.py <deck> --original <template>` from the pptx skill; `assets/render_qa.sh <deck>` and inspect every slide image; fix JSON (shorter copy) or crops, rebuild.
6. Report the deck path, data sources and QA results.

## Output Contract

Return: deck path, week JSON path, screenshots captured (yes/no), validate result, visual QA fixes, open gaps.

## References

- `references/brand.md` — template-first brand rules.
- `references/deck-structure.md` — slides, template mapping, JSON fields.
- `assets/collect.sh`, `assets/build_deck.py`, `assets/render_qa.sh`, `assets/requirements.txt`.
