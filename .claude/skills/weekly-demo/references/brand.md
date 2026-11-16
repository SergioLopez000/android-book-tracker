# Brand rules (Sngular Brand Book 2025)

## Source of truth

The official Sngular slides template is the source of truth:
`~/Desktop/libros-leidos-demos/assets/sngular-template.pptx` (copy of
"Guías y Modelos de Slides Editables - Sngular 2025.pptx"; kept outside the public repo).
Its layouts already carry the logo, the `S|` isotype, the copyright footer, slide numbers,
the cursor elements, the theme colors and the Outfit fonts (embedded). Reuse its slides;
add shapes only where a slide has no slot (screenshots, charts, the process diagram).

## Colors (theme slots in the template)

| Use | Color | Theme slot |
|-----|-------|-----------|
| Navy (text on light, dark backgrounds) | `061B2B` | `dk1` |
| Blue (cursor, highlights, key numbers) | `0070F6` | `dk2` |
| Navy Light (light backgrounds) | `F2F3F4` | `lt2` |
| White | `FFFFFF` | `lt1` |
| Accents: Red `FF0059`, Green `B3E500`, Purple `9747FF` | 5% max | `accent1..3` |

- Primary colors are 95% of the deck. Accents only for icons, small lines, tiny highlights;
  never for titles, paragraphs or filled text boxes.
- Titles: navy on light, white on navy. Paragraphs: navy on white, white on navy.

## Typography

- Template fonts: Outfit (titles), Outfit Light (body), Outfit SemiBold (emphasis). They are
  embedded in the template; do not swap fonts.
- Emphasis styles from the template: `**bold**` = Outfit SemiBold; `[[text]]` = white text on
  blue highlight (use at most once per slide).
- QA renders need Outfit available: `assets/render_qa.sh` loads static instances from
  `~/Desktop/libros-leidos-demos/assets/qa-fonts/`. Without them LibreOffice substitutes a serif
  font and fit checks are meaningless.

## Layout

- Breadcrumb-free titles at the template's title position (x 0.5", y 0.74"); keep it fixed.
- Generous margins (0.5" sides); content between y 1.25" and 4.8"; footer zone is the layout's.
- Cursor = thin vertical blue bar left of a key element. One per slide at most, plus the
  template's own cursors (cover, dividers, closing).
- Cover and closing on navy; content slides alternate white and navy for rhythm.
- No stock photos; app screenshots only when real.
