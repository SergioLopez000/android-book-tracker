#!/usr/bin/env python3
"""Build the weekly client-demo deck from the Sngular template and a week JSON.

Usage:
    python build_deck.py WEEK.json OUT.pptx [--template PATH]

The template defaults to $WEEKLY_DEMO_TEMPLATE or
~/Desktop/libros-leidos-demos/assets/sngular-template.pptx (kept outside the
public repo). All week content comes from the JSON; this file only knows which
template slide and which template shape hosts each piece of content.

Inline markup in any text value:  **bold** (Outfit SemiBold)  [[highlight]]
(white text on the brand-blue highlight, the template's own emphasis style).
"""
import argparse
import copy
import json
import os
import re
import sys

from lxml import etree
from pptx import Presentation
from pptx.chart.data import CategoryChartData
from pptx.dml.color import RGBColor
from pptx.enum.chart import XL_CHART_TYPE, XL_LABEL_POSITION
from pptx.enum.shapes import MSO_CONNECTOR, MSO_SHAPE
from pptx.enum.text import MSO_ANCHOR, PP_ALIGN
from pptx.oxml.ns import qn
from pptx.util import Emu, Inches, Pt

NAVY, BLUE, LIGHT, WHITE = "061B2B", "0070F6", "F2F3F4", "FFFFFF"
MUTED = "5B6B78"  # navy at reduced weight, for chart axes on white
FONT, FONT_LIGHT, FONT_BOLD = "Outfit", "Outfit Light", "Outfit SemiBold"
DEFAULT_TEMPLATE = os.path.expanduser(
    os.environ.get("WEEKLY_DEMO_TEMPLATE", "~/Desktop/libros-leidos-demos/assets/sngular-template.pptx"))

# Template slide numbers (1-based, in the original template) used per deck slide.
SRC = {
    "cover": 9, "summary": 51, "demo": 40, "quality": 54, "process": 43,
    "board": 40, "delivered": 53, "risks": 55, "next": 24, "closing": 60,
}
ORDER = ["cover", "summary", "demo", "quality", "process", "board", "delivered", "risks", "next", "closing"]


# --------------------------------------------------------------------------- helpers
def shape_by_id(slide, sid):
    for sh in slide.shapes:
        if sh.shape_id == sid:
            return sh
    raise KeyError(f"shape id {sid} not found on slide (template changed?)")


def remove_shape(shape):
    el = shape._element
    el.getparent().remove(el)


def place(shape, x, y, w, h):
    shape.left, shape.top, shape.width, shape.height = Inches(x), Inches(y), Inches(w), Inches(h)


def _color_el(color):
    fill = etree.SubElement(etree.Element("dummy"), qn("a:solidFill"))
    if color in ("dk1", "lt1", "dk2", "lt2"):
        etree.SubElement(fill, qn("a:schemeClr"), val=color)
    else:
        etree.SubElement(fill, qn("a:srgbClr"), val=color)
    return fill


def make_rpr(base, color=None, highlight=False, font=None, size=None):
    rpr = copy.deepcopy(base) if base is not None else etree.Element(qn("a:rPr"))
    rpr.set("lang", "es-ES")
    for tag in ("a:solidFill", "a:highlight", "a:latin", "a:ea", "a:cs", "a:sym"):
        for el in rpr.findall(qn(tag)):
            rpr.remove(el)
    if size:
        rpr.set("sz", str(int(size * 100)))
    if highlight:
        rpr.append(_color_el("lt1"))
        hl = etree.SubElement(rpr, qn("a:highlight"))
        etree.SubElement(hl, qn("a:schemeClr"), val="dk2")
    elif color:
        rpr.append(_color_el(color))
    else:
        old = base.find(qn("a:solidFill")) if base is not None else None
        if old is not None:
            rpr.append(copy.deepcopy(old))
    face = font or (base.find(qn("a:latin")).get("typeface") if base is not None and base.find(qn("a:latin")) is not None else None)
    if face:
        for tag in ("a:latin", "a:ea", "a:cs", "a:sym"):
            etree.SubElement(rpr, qn(tag), typeface=face)
    return rpr


TOKEN = re.compile(r"(\*\*.+?\*\*|\[\[.+?\]\])")


def set_text(shape, paras, color=None, size=None, font=None, space_after=None, align=None):
    """Replace a shape's paragraphs, keeping the template's paragraph and run formatting."""
    if isinstance(paras, str):
        paras = [paras]
    body = shape.text_frame._txBody
    old = body.findall(qn("a:p"))
    ppr = old[0].find(qn("a:pPr")) if old else None
    base = None
    for r in body.iter(qn("a:r")):
        rp = r.find(qn("a:rPr"))
        if rp is not None and rp.find(qn("a:highlight")) is None:
            base = rp
            break
    if base is None:
        epr = body.find(".//" + qn("a:endParaRPr"))
        base = copy.deepcopy(epr) if epr is not None else None
        if base is not None:
            base.tag = qn("a:rPr")
    base = copy.deepcopy(base) if base is not None else None
    for p in old:
        body.remove(p)
    for text in paras:
        p = etree.SubElement(body, qn("a:p"))
        if ppr is not None:
            np_ = copy.deepcopy(ppr)
            if space_after is not None:
                for el in np_.findall(qn("a:spcAft")):
                    np_.remove(el)
                sa = etree.Element(qn("a:spcAft"))
                etree.SubElement(sa, qn("a:spcPts"), val=str(int(space_after * 100)))
                anchor = np_.find(qn("a:buClr")) or np_.find(qn("a:buSzPts")) or np_.find(qn("a:buFont")) or np_.find(qn("a:buNone"))
                if anchor is not None:
                    anchor.addprevious(sa)
                else:
                    np_.append(sa)
            if align:
                np_.set("algn", align)
            p.append(np_)
        for seg in TOKEN.split(text):
            if not seg:
                continue
            if seg.startswith("**"):
                rpr = make_rpr(base, color=color, font=FONT_BOLD, size=size)
                seg = seg[2:-2]
            elif seg.startswith("[["):
                rpr = make_rpr(base, highlight=True, font=font, size=size)
                seg = seg[2:-2]
            else:
                rpr = make_rpr(base, color=color, font=font, size=size)
            r = etree.SubElement(p, qn("a:r"))
            r.append(rpr)
            etree.SubElement(r, qn("a:t")).text = seg


def add_textbox(slide, x, y, w, h, paras, color=NAVY, size=12, font=FONT_LIGHT, align="l",
                anchor=MSO_ANCHOR.TOP, space_after=0, name=None):
    tb = slide.shapes.add_textbox(Inches(x), Inches(y), Inches(w), Inches(h))
    if name:
        tb.name = name
    tf = tb.text_frame
    tf.word_wrap = True
    tf.margin_left = tf.margin_right = tf.margin_top = tf.margin_bottom = 0
    tf.vertical_anchor = anchor
    base = etree.Element(qn("a:rPr"))
    base.set("sz", str(int(size * 100)))
    base.append(_color_el(color))
    for tag in ("a:latin", "a:ea", "a:cs", "a:sym"):
        etree.SubElement(base, qn(tag), typeface=font)
    body = tf._txBody
    for p in body.findall(qn("a:p")):
        body.remove(p)
    for text in ([paras] if isinstance(paras, str) else paras):
        p = etree.SubElement(body, qn("a:p"))
        ppr = etree.SubElement(p, qn("a:pPr"), algn=align)
        sa = etree.SubElement(ppr, qn("a:spcAft"))
        etree.SubElement(sa, qn("a:spcPts"), val=str(int(space_after * 100)))
        for seg in TOKEN.split(text):
            if not seg:
                continue
            if seg.startswith("**"):
                rpr, seg = make_rpr(base, font=FONT_BOLD), seg[2:-2]
            elif seg.startswith("[["):
                rpr, seg = make_rpr(base, highlight=True), seg[2:-2]
            else:
                rpr = make_rpr(base)
            r = etree.SubElement(p, qn("a:r"))
            r.append(rpr)
            etree.SubElement(r, qn("a:t")).text = seg
    return tb


def cursor(slide, x, y, h, w=0.045, color=BLUE, name="Cursor"):
    """The brand cursor: a thin vertical blue bar beside a key element."""
    bar = slide.shapes.add_shape(MSO_SHAPE.RECTANGLE, Inches(x), Inches(y), Inches(w), Inches(h))
    bar.name = name
    bar.fill.solid()
    bar.fill.fore_color.rgb = RGBColor.from_string(color)
    bar.line.fill.background()
    bar.shadow.inherit = False
    return bar


def box(slide, x, y, w, h, fill=None, line=None, line_w=1.0, name=None, shape=MSO_SHAPE.RECTANGLE):
    s = slide.shapes.add_shape(shape, Inches(x), Inches(y), Inches(w), Inches(h))
    if name:
        s.name = name
    if fill:
        s.fill.solid()
        s.fill.fore_color.rgb = RGBColor.from_string(fill)
    else:
        s.fill.background()
    if line:
        s.line.color.rgb = RGBColor.from_string(line)
        s.line.width = Pt(line_w)
    else:
        s.line.fill.background()
    s.shadow.inherit = False
    s.text_frame.text = ""
    return s


def arrow(slide, x1, y1, x2, y2, color=WHITE, name="Flecha"):
    c = slide.shapes.add_connector(MSO_CONNECTOR.STRAIGHT, Inches(x1), Inches(y1), Inches(x2), Inches(y2))
    c.name = name
    c.line.color.rgb = RGBColor.from_string(color)
    c.line.width = Pt(1.5)
    ln = c.line._get_or_add_ln()
    etree.SubElement(ln, qn("a:tailEnd"), type="triangle", w="med", len="med")
    return c


def duplicate_slide(prs, src):
    """Append a copy of `src` (same layout, deep-copied shapes, image rels re-linked)."""
    dst = prs.slides.add_slide(src.slide_layout)
    tree = dst.shapes._spTree
    for el in list(tree):
        if el.tag not in (qn("p:nvGrpSpPr"), qn("p:grpSpPr")):
            tree.remove(el)
    rid_map = {}
    for rel in src.part.rels.values():
        if "image" in rel.reltype or "media" in rel.reltype:
            rid_map[rel.rId] = dst.part.relate_to(rel._target, rel.reltype)
    for el in src.shapes._spTree:
        if el.tag in (qn("p:nvGrpSpPr"), qn("p:grpSpPr")):
            continue
        new = copy.deepcopy(el)
        for node in new.iter():
            for attr in (qn("r:embed"), qn("r:link"), qn("r:id")):
                if node.get(attr) in rid_map:
                    node.set(attr, rid_map[node.get(attr)])
        tree.append(new)
    return dst


def set_notes(slide, text):
    if text:
        slide.notes_slide.notes_text_frame.text = text


def style_chart(chart, size=10, color=NAVY):
    chart.font.name = FONT_LIGHT
    chart.font.size = Pt(size)
    chart.font.color.rgb = RGBColor.from_string(color)
    chart.has_legend = False


def bar_chart(slide, x, y, w, h, title, cats, vals, horizontal=False, highlight=None, name="Grafico"):
    data = CategoryChartData()
    data.categories = cats
    data.add_series(title, vals)
    kind = XL_CHART_TYPE.BAR_CLUSTERED if horizontal else XL_CHART_TYPE.COLUMN_CLUSTERED
    gf = slide.shapes.add_chart(kind, Inches(x), Inches(y), Inches(w), Inches(h), data)
    gf.name = name
    ch = gf.chart
    style_chart(ch)
    ch.has_title = True
    ch.chart_title.text_frame.text = title
    tr = ch.chart_title.text_frame.paragraphs[0].runs[0]
    tr.font.size, tr.font.name, tr.font.bold = Pt(12), FONT, False
    tr.font.color.rgb = RGBColor.from_string(NAVY)
    plot = ch.plots[0]
    plot.gap_width = 60
    plot.has_data_labels = True
    dl = plot.data_labels
    dl.position = XL_LABEL_POSITION.OUTSIDE_END
    dl.font.size, dl.font.name = Pt(12), FONT
    dl.font.color.rgb = RGBColor.from_string(NAVY)
    dl.number_format, dl.number_format_is_linked = "0", False
    series = plot.series[0]
    for i, _ in enumerate(vals):
        pt = series.points[i]
        pt.format.fill.solid()
        pt.format.fill.fore_color.rgb = RGBColor.from_string(BLUE if highlight is not None and i == highlight else NAVY)
    va, ca = ch.value_axis, ch.category_axis
    va.visible = False
    va.has_major_gridlines = False
    va.maximum_scale = max(vals) * 1.18 if vals and max(vals) > 0 else 1
    va.minimum_scale = 0
    ca.has_major_gridlines = False
    ca.format.line.color.rgb = RGBColor.from_string("C9CED3")
    ca.tick_labels.font.size = Pt(10)
    ca.tick_labels.font.color.rgb = RGBColor.from_string(NAVY)
    if horizontal:
        ca.reverse_order = True
    return gf


def contain(img_w, img_h, box_w, box_h):
    r = min(box_w / img_w, box_h / img_h)
    return img_w * r, img_h * r


# --------------------------------------------------------------------------- slides
def fill_cover(s, d):
    title = shape_by_id(s, 1122)
    place(title, 1.28, 2.0, 7.2, 1.2)
    set_text(title, d["cover"]["title"])
    sub = shape_by_id(s, 1123)
    place(sub, 0.5, 4.3, 6.0, 0.45)
    set_text(sub, [d["cover"]["subtitle"]])


def fill_summary(s, d):
    sm = d["summary"]
    set_text(shape_by_id(s, 1673), sm["headline"])
    pairs = [(1674, 1675), (1676, 1677), (1678, 1679), (1680, 1681)]
    items = sm["takeaways"][:4]
    top, step = 0.85, 1.45 if len(items) <= 3 else 1.05
    for i, (tid, xid) in enumerate(pairs):
        t, x = shape_by_id(s, tid), shape_by_id(s, xid)
        if i >= len(items):
            remove_shape(t); remove_shape(x); continue
        y = top + i * step
        place(t, 6.46, y, 3.04, 0.32)
        place(x, 6.46, y + 0.36, 3.04, 0.7)
        set_text(t, items[i]["title"])
        set_text(x, items[i]["text"])


def fill_demo(s, d, base_dir):
    dm = d["demo"]
    set_text(shape_by_id(s, 1526), dm["title"])
    shape_by_id(s, 1526).width = Inches(8.2)
    screens = [sc for sc in dm.get("screens", []) if os.path.exists(os.path.join(base_dir, sc["image"]))]
    if screens:
        n = len(screens[:3])
        bw, bh, gap, y0 = 2.45, 2.55, 0.45, 1.30
        for i, sc in enumerate(screens[:3]):
            path = os.path.join(base_dir, sc["image"])
            x = 0.62 + i * (bw + gap)
            pic = s.shapes.add_picture(path, Inches(x), Inches(y0))
            pic.name = f"Captura {i + 1}"
            iw, ih = pic.image.size
            ratio = ih / iw
            top_crop = sc.get("crop_top", 0.05 if ratio > 1.6 else 0.0)
            if sc.get("crop_px"):
                # Explicit crop box in source pixels: [left, top, right, bottom].
                l, t, r, b = sc["crop_px"]
                pic.crop_left, pic.crop_top = l / iw, t / ih
                pic.crop_right, pic.crop_bottom = 1 - r / iw, 1 - b / ih
                w, h = contain(r - l, b - t, bw, bh)
            # Tall phone screenshots: keep the top block of the screen (where the content is).
            elif ratio > bh / bw:
                visible = (bh / bw) / ratio
                pic.crop_top = top_crop
                pic.crop_bottom = max(0.0, 1 - top_crop - visible)
                w, h = bw, bh
            else:
                w, h = contain(iw, ih, bw, bh)
            pic.left, pic.top, pic.width, pic.height = Inches(x), Inches(y0), Inches(w), Inches(h)
            pic.line.color.rgb = RGBColor.from_string("C9CED3")
            pic.line.width = Pt(0.75)
            add_textbox(s, x, y0 + bh + 0.15, bw, 0.55, sc["caption"], size=11, name=f"Pie captura {i + 1}")
        cursor(s, 0.50, y0, bh)
    else:
        scope = dm.get("scope", [])
        for i, item in enumerate(scope[:4]):
            col, row = i % 2, i // 2
            x, y = 0.62 + col * 4.4, 1.45 + row * 1.25
            cursor(s, x - 0.12, y, 0.9, name=f"Cursor {i + 1}") if i == 0 else None
            add_textbox(s, x, y, 3.9, 1.0, [f"**{item['title']}**", item["text"]], size=12, space_after=4)
        add_textbox(s, 0.62, 4.15, 8.3, 0.4, dm.get("note", ""), size=11, color=BLUE)


def fill_quality(s, d):
    q = d["quality"]
    remove_shape(shape_by_id(s, 1714))  # stray duplicate title text box in the template
    set_text(shape_by_id(s, 1715), q["title"])
    shape_by_id(s, 1715).width = Inches(8.2)
    slots = [(1725, 1723, 1716), (1726, 1727, 1717), (1719, 1724, 1718), (1721, 1722, 1720)]
    for i, (nid, tid, xid) in enumerate(slots):
        n, t, x = shape_by_id(s, nid), shape_by_id(s, tid), shape_by_id(s, xid)
        if i >= len(q["stats"]):
            for sh in (n, t, x):
                remove_shape(sh)
            continue
        st = q["stats"][i]
        x0 = 0.5 if i % 2 == 0 else 5.06
        y0 = 1.30 if i < 2 else 3.05
        place(n, x0, y0, 3.9, 0.62)
        place(t, x0, y0 + 0.68, 3.9, 0.3)
        place(x, x0, y0 + 1.0, 3.9, 0.62)
        set_text(n, st["value"], size=36)
        set_text(t, st["label"], font=FONT_BOLD, color=NAVY)
        set_text(x, st["detail"], color=NAVY)


def fill_process(s, d):
    p = d["process"]
    set_text(shape_by_id(s, 1550), p["title"])
    shape_by_id(s, 1550).width = Inches(8.2)
    steps = p["steps"]
    n = len(steps)
    gap = 0.38
    total_w = 9.0
    bw = (total_w - gap * (n - 1)) / n
    y, h = 1.50, 1.35
    for i, st in enumerate(steps):
        x = 0.5 + i * (bw + gap)
        if st.get("kind") == "gate":
            g = box(s, x, y + 0.25, bw, h - 0.5, fill=BLUE, name=f"Puerta {i + 1}", shape=MSO_SHAPE.ROUNDED_RECTANGLE)
            g.adjustments[0] = 0.5
            add_textbox(s, x + 0.12, y + 0.25, bw - 0.24, h - 0.5, [f"**{st['title']}**", st["text"]], color=WHITE,
                        size=11, align="ctr", anchor=MSO_ANCHOR.MIDDLE, space_after=2, name=f"Texto puerta {i + 1}")
        else:
            box(s, x, y, bw, h, line=WHITE, line_w=1.0, name=f"Anillo {i + 1}")
            add_textbox(s, x + 0.15, y + 0.15, bw - 0.3, 0.25, st.get("kicker", ""), color=LIGHT, size=9,
                        name=f"Etiqueta {i + 1}")
            add_textbox(s, x + 0.15, y + 0.42, bw - 0.3, 1.0, [f"**{st['title']}**", st["text"]], color=WHITE,
                        size=11, space_after=3, name=f"Texto anillo {i + 1}")
        if i < n - 1:
            arrow(s, x + bw + 0.04, y + h / 2, x + bw + gap - 0.04, y + h / 2, name=f"Flecha {i + 1}")
    facts = p.get("facts", [])
    fw = (total_w - 0.3 * (len(facts) - 1)) / max(1, len(facts))
    for i, f in enumerate(facts):
        x = 0.5 + i * (fw + 0.3)
        if i == 0:
            cursor(s, x, 3.30, 0.7)
        add_textbox(s, x + 0.15, 3.30, fw - 0.15, 0.85, [f"**{f['title']}**", f["text"]], color=WHITE, size=11,
                    space_after=2, name=f"Dato {i + 1}")


def fill_board(s, d):
    b = d["board"]
    set_text(shape_by_id(s, 1526), b["title"])
    shape_by_id(s, 1526).width = Inches(8.2)
    st = b["status"]
    cats = list(st.keys())
    vals = [int(v) for v in st.values()]
    hl = cats.index(b["highlight_status"]) if b.get("highlight_status") in cats else None
    bar_chart(s, 0.45, 1.25, 4.1, 3.45, b["status_title"], cats, vals, highlight=hl, name="Grafico tablero")
    ms = b["milestones"]
    mcats, mvals = list(ms.keys()), [int(v) for v in ms.values()]
    mhl = mcats.index(b["highlight_milestone"]) if b.get("highlight_milestone") in mcats else None
    bar_chart(s, 4.95, 1.25, 4.6, 3.45, b["milestones_title"], mcats, mvals, horizontal=True, highlight=mhl,
              name="Grafico hitos")


def fill_delivered(s, d):
    dl = d["delivered"]
    set_text(shape_by_id(s, 1707), dl["title"])
    shape_by_id(s, 1707).width = Inches(8.2)
    slots = [(1703, 1699), (1705, 1700), (1704, 1701), (1706, 1702)]  # reading order: TL, TR, BL, BR
    for i, (tid, lid) in enumerate(slots):
        t, ln = shape_by_id(s, tid), shape_by_id(s, lid)
        if i >= len(dl["items"]):
            remove_shape(t); remove_shape(ln); continue
        it = dl["items"][i]
        x0 = 0.5 if i % 2 == 0 else 5.06
        y0 = 1.55 if i < 2 else 3.20
        place(ln, x0, y0, 0, 0.75)
        place(t, x0 + 0.17, y0, 3.95, 1.25)
        set_text(t, [f"**{it['title']}**", it["text"]], color="dk1", space_after=3)


def fill_risks(s, d):
    r = d["risks"]
    set_text(shape_by_id(s, 1733), r["title"])
    shape_by_id(s, 1733).width = Inches(8.2)
    slots = [(1735, 1742, 1734), (1737, 1743, 1736), (1739, 1744, 1738), (1741, 1745, 1740)]
    for i, (nid, tid, xid) in enumerate(slots):
        n, t, x = shape_by_id(s, nid), shape_by_id(s, tid), shape_by_id(s, xid)
        if i >= len(r["items"]):
            for sh in (n, t, x):
                remove_shape(sh)
            continue
        it = r["items"][i]
        set_text(n, f"{i + 1:02d}")
        set_text(t, it["title"])
        set_text(x, it["text"])
        t.width = Inches(3.6)
        x.width = Inches(3.6)


def fill_next(s, d):
    nx = d["next"]
    t = shape_by_id(s, 1286)
    place(t, 2.03, 2.0, 3.2, 1.6)
    set_text(t, nx["title"])
    body = shape_by_id(s, 1287)
    place(body, 5.70, 1.35, 3.6, 3.0)
    set_text(body, [f"**{it['ref']}** {it['text']}" for it in nx["items"]], size=13, space_after=12)


def fill_closing(s, d):
    set_text(shape_by_id(s, 1806), d["closing"]["text"])


# --------------------------------------------------------------------------- main
def build(week_path, out_path, template):
    with open(week_path, encoding="utf-8") as f:
        d = json.load(f)
    base_dir = os.path.dirname(os.path.abspath(week_path))
    prs = Presentation(template)
    slides = list(prs.slides)
    src = {k: slides[v - 1] for k, v in SRC.items()}
    used, deck = set(), {}
    for key in ORDER:
        sl = src[key]
        if id(sl) in used:
            sl = duplicate_slide(prs, sl)
        used.add(id(sl))
        deck[key] = sl
    # Reorder <p:sldIdLst> to the deck order and drop every other template slide.
    lst = prs.slides._sldIdLst
    by_rid = {sid.rId: sid for sid in lst}
    rid_of = {}
    for rel in prs.part.rels.values():
        if rel.reltype.endswith("/slide"):
            rid_of[id(rel._target)] = rel.rId
    keep_rids = [rid_of[id(deck[k].part)] for k in ORDER]
    for sid in list(lst):
        lst.remove(sid)
    for rid in keep_rids:
        lst.append(by_rid[rid])
    for rid in [r for r in list(by_rid) if r not in keep_rids]:
        prs.part.drop_rel(rid)

    fill_cover(deck["cover"], d)
    fill_summary(deck["summary"], d)
    fill_demo(deck["demo"], d, base_dir)
    fill_quality(deck["quality"], d)
    fill_process(deck["process"], d)
    fill_board(deck["board"], d)
    fill_delivered(deck["delivered"], d)
    fill_risks(deck["risks"], d)
    fill_next(deck["next"], d)
    fill_closing(deck["closing"], d)
    for key in ORDER:
        set_notes(deck[key], d.get("notes", {}).get(key, ""))
    prs.core_properties.title = d["cover"]["title"].replace("**", "").replace("[[", "").replace("]]", "")
    prs.core_properties.author = d.get("author", "Sngular")
    prs.save(out_path)
    print(out_path)


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("week_json")
    ap.add_argument("out_pptx")
    ap.add_argument("--template", default=DEFAULT_TEMPLATE)
    a = ap.parse_args()
    if not os.path.exists(a.template):
        sys.exit(f"template not found: {a.template} (set WEEKLY_DEMO_TEMPLATE or --template)")
    build(a.week_json, a.out_pptx, a.template)
