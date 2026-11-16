# Deck structure and data contract

The week JSON (`<YYYY>-W<NN>-demo-semanal.json`, next to the deck) holds ALL content.
`build_deck.py` maps each key to a template slide (number in the original template).
Text values accept `**bold**` and `[[highlight]]`.

| # | Slide | JSON key | Template slide | Data source |
|---|-------|----------|----------------|-------------|
| 1 | Portada (navy) | `cover.title`, `cover.subtitle` | 9 | ISO week + Mon–Fri dates from `collect.sh` |
| 2 | Resumen ejecutivo | `summary.headline`, `summary.takeaways[≤4]{title,text}` | 51 | Synthesis of slides 4–9, business language |
| 3 | Demo de la app | `demo.title`, `demo.screens[≤3]{image,caption,crop_px?}`, fallback `demo.scope[≤4]{title,text}` + `demo.note` | 40 | Emulator screenshots (paths relative to the JSON); `crop_px` = `[l,t,r,b]` source pixels |
| 4 | Calidad | `quality.title`, `quality.stats[≤4]{value,label,detail}` | 54 | `./gradlew test` report, `openspec/specs/**` (requirement/scenario counts), archived verify reports |
| 5 | Proceso y QA | `process.title`, `process.steps[]{kicker,title,text,kind?:"gate"}`, `process.facts[≤3]{title,text}` | 43 | `.github/workflows/`, branch protection, environments (`gh api`) |
| 6 | Estado del tablero | `board.status{}`, `board.milestones{}`, titles, `highlight_status`, `highlight_milestone` | 40 (duplicated) | `collect.sh`: `board_status`, `open_issues_by_milestone` (native bar charts) |
| 7 | Entregado esta semana | `delivered.title`, `delivered.items[≤4]{title,text}` | 53 | `collect.sh`: `closed_issues`, `merged_prs` |
| 8 | Riesgos y decisiones | `risks.title`, `risks.items[≤4]{title,text}` | 55 | Open issues labelled risk/decision, team notes |
| 9 | Próxima semana | `next.title`, `next.items[≤4]{ref,text}` | 24 | Next milestone's open issues |
| 10 | Cierre (navy) | `closing.text` | 60 | Fixed: `¿[[Preguntas]]?` |

Also required: `notes.<key>` (Spanish speaker notes per slide), `week{year,iso_week,start,end}`.

## Copy limits (Outfit at template sizes)

- Titles ≤ 50 chars. Stat `value` ≤ 5 chars. Item titles ≤ 35 chars, texts ≤ 90 chars.
- Summary headline ≤ 70 chars. Process step texts ≤ 45 chars.
- If QA shows wrapping into a neighbour, shorten the copy before touching geometry.
