#!/usr/bin/env python3
"""Build the stage-one PRD DOCX from the maintained Markdown source."""

from __future__ import annotations

import re
from pathlib import Path

from docx import Document
from docx.enum.section import WD_SECTION
from docx.enum.table import WD_ALIGN_VERTICAL, WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_BREAK, WD_LINE_SPACING
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.oxml.table import CT_Tbl
from docx.oxml.text.paragraph import CT_P
from docx.opc.constants import RELATIONSHIP_TYPE as RT
from docx.shared import Inches, Pt, RGBColor
from docx.table import Table
from docx.text.paragraph import Paragraph
from docx.text.run import Run


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "docs" / "01_requirements" / "湖北工程学院新技术学院AI数字人校园导览系统_需求规格说明书_PRD.md"
OUTPUT = ROOT / "docs" / "01_requirements" / "湖北工程学院新技术学院AI数字人校园导览系统_需求规格说明书_PRD.docx"

INK = RGBColor(0x1A, 0x1A, 0x1A)
MUTED = RGBColor(0x59, 0x59, 0x59)
NAVY = "17365D"
PALE_BLUE = "EAF1F8"
WHITE = "FFFFFF"
LIGHT_GRAY = "D9D9D9"
BODY_FONT = "Microsoft YaHei"
MONO_FONT = "Consolas"


def set_cell_shading(cell, fill: str) -> None:
    tc_pr = cell._tc.get_or_add_tcPr()
    shd = tc_pr.find(qn("w:shd"))
    if shd is None:
        shd = OxmlElement("w:shd")
        tc_pr.append(shd)
    shd.set(qn("w:fill"), fill)


def set_cell_margins(cell, top=70, start=90, bottom=70, end=90) -> None:
    tc_pr = cell._tc.get_or_add_tcPr()
    tc_mar = tc_pr.first_child_found_in("w:tcMar")
    if tc_mar is None:
        tc_mar = OxmlElement("w:tcMar")
        tc_pr.append(tc_mar)
    for edge, value in (("top", top), ("start", start), ("bottom", bottom), ("end", end)):
        node = tc_mar.find(qn(f"w:{edge}"))
        if node is None:
            node = OxmlElement(f"w:{edge}")
            tc_mar.append(node)
        node.set(qn("w:w"), str(value))
        node.set(qn("w:type"), "dxa")


def set_cell_borders(cell, color=LIGHT_GRAY, size="6") -> None:
    tc_pr = cell._tc.get_or_add_tcPr()
    borders = tc_pr.first_child_found_in("w:tcBorders")
    if borders is None:
        borders = OxmlElement("w:tcBorders")
        tc_pr.append(borders)
    for edge in ("top", "start", "bottom", "end", "insideH", "insideV"):
        node = borders.find(qn(f"w:{edge}"))
        if node is None:
            node = OxmlElement(f"w:{edge}")
            borders.append(node)
        node.set(qn("w:val"), "single")
        node.set(qn("w:sz"), size)
        node.set(qn("w:space"), "0")
        node.set(qn("w:color"), color)


def set_cell_width(cell, inches: float) -> None:
    width = Inches(inches)
    cell.width = width
    tc_pr = cell._tc.get_or_add_tcPr()
    tc_w = tc_pr.find(qn("w:tcW"))
    if tc_w is None:
        tc_w = OxmlElement("w:tcW")
        tc_pr.append(tc_w)
    tc_w.set(qn("w:w"), str(int(width.twips)))
    tc_w.set(qn("w:type"), "dxa")


def set_repeat_table_header(row) -> None:
    tr_pr = row._tr.get_or_add_trPr()
    repeat = OxmlElement("w:tblHeader")
    repeat.set(qn("w:val"), "true")
    tr_pr.append(repeat)


def set_row_cant_split(row) -> None:
    tr_pr = row._tr.get_or_add_trPr()
    if tr_pr.find(qn("w:cantSplit")) is None:
        node = OxmlElement("w:cantSplit")
        node.set(qn("w:val"), "true")
        tr_pr.append(node)


def set_fixed_table_layout(table: Table) -> None:
    table.autofit = False
    tbl_pr = table._tbl.tblPr
    layout = tbl_pr.find(qn("w:tblLayout"))
    if layout is None:
        layout = OxmlElement("w:tblLayout")
        tbl_pr.append(layout)
    layout.set(qn("w:type"), "fixed")


def set_run_font(run: Run, name=BODY_FONT, size: float | None = None, bold=None, color=None) -> None:
    run.font.name = name
    run._element.get_or_add_rPr()
    r_fonts = run._element.rPr.rFonts
    if r_fonts is None:
        r_fonts = OxmlElement("w:rFonts")
        run._element.rPr.insert(0, r_fonts)
    r_fonts.set(qn("w:ascii"), name)
    r_fonts.set(qn("w:hAnsi"), name)
    r_fonts.set(qn("w:eastAsia"), name)
    if size is not None:
        run.font.size = Pt(size)
    if bold is not None:
        run.bold = bold
    if color is not None:
        run.font.color.rgb = color


def set_paragraph_spacing(paragraph: Paragraph, before=0, after=6, line=1.25) -> None:
    fmt = paragraph.paragraph_format
    fmt.space_before = Pt(before)
    fmt.space_after = Pt(after)
    fmt.line_spacing_rule = WD_LINE_SPACING.MULTIPLE
    fmt.line_spacing = line


def add_hyperlink(paragraph: Paragraph, text: str, url: str) -> Run:
    part = paragraph.part
    r_id = part.relate_to(url, RT.HYPERLINK, is_external=True)
    hyperlink = OxmlElement("w:hyperlink")
    hyperlink.set(qn("r:id"), r_id)
    new_run = OxmlElement("w:r")
    r_pr = OxmlElement("w:rPr")
    color = OxmlElement("w:color")
    color.set(qn("w:val"), "1F4E79")
    r_pr.append(color)
    underline = OxmlElement("w:u")
    underline.set(qn("w:val"), "single")
    r_pr.append(underline)
    r_fonts = OxmlElement("w:rFonts")
    for attr in ("ascii", "hAnsi", "eastAsia"):
        r_fonts.set(qn(f"w:{attr}"), BODY_FONT)
    r_pr.append(r_fonts)
    new_run.append(r_pr)
    text_node = OxmlElement("w:t")
    text_node.text = text
    new_run.append(text_node)
    hyperlink.append(new_run)
    paragraph._p.append(hyperlink)
    return Run(new_run, paragraph)


def add_inline(paragraph: Paragraph, text: str, bold=False, size: float | None = None, color=None) -> None:
    """Add simple Markdown inline formatting: bold, code, and links."""
    pattern = re.compile(r"(\*\*[^*]+\*\*|`[^`]+`|\[[^\]]+\]\([^)]+\)|https?://[^\s)）]+)")
    cursor = 0
    for match in pattern.finditer(text):
        if match.start() > cursor:
            run = paragraph.add_run(text[cursor : match.start()])
            set_run_font(run, size=size, bold=bold, color=color)
        token = match.group(0)
        if token.startswith("**") and token.endswith("**"):
            run = paragraph.add_run(token[2:-2])
            set_run_font(run, size=size, bold=True, color=color)
        elif token.startswith("`") and token.endswith("`"):
            run = paragraph.add_run(token[1:-1])
            set_run_font(run, name=MONO_FONT, size=(size or 9.0) - 0.5, bold=bold, color=color)
        elif token.startswith("[") and "](" in token:
            label, url = re.match(r"\[([^\]]+)\]\(([^)]+)\)", token).groups()
            add_hyperlink(paragraph, label, url)
        else:
            link_text = token.rstrip(".,;，。；")
            trailing = token[len(link_text) :]
            add_hyperlink(paragraph, link_text, link_text)
            if trailing:
                run = paragraph.add_run(trailing)
                set_run_font(run, size=size, bold=bold, color=color)
        cursor = match.end()
    if cursor < len(text):
        run = paragraph.add_run(text[cursor:])
        set_run_font(run, size=size, bold=bold, color=color)


def style_document(doc: Document) -> None:
    section = doc.sections[0]
    section.page_width = Inches(8.5)
    section.page_height = Inches(11)
    section.top_margin = Inches(0.75)
    section.bottom_margin = Inches(0.72)
    section.left_margin = Inches(0.8)
    section.right_margin = Inches(0.8)
    section.header_distance = Inches(0.3)
    section.footer_distance = Inches(0.35)

    styles = doc.styles
    normal = styles["Normal"]
    normal.font.name = BODY_FONT
    normal._element.rPr.rFonts.set(qn("w:eastAsia"), BODY_FONT)
    normal.font.size = Pt(10.5)
    normal.font.color.rgb = INK
    normal.paragraph_format.space_after = Pt(6)
    normal.paragraph_format.line_spacing = 1.25

    for name, size, before, after in (
        ("Title", 24, 0, 16),
        ("Heading 1", 16, 16, 8),
        ("Heading 2", 13, 12, 5),
        ("Heading 3", 11.5, 10, 4),
        ("Heading 4", 10.5, 8, 3),
    ):
        style = styles[name]
        style.font.name = BODY_FONT
        style._element.rPr.rFonts.set(qn("w:eastAsia"), BODY_FONT)
        style.font.size = Pt(size)
        style.font.bold = True
        style.font.color.rgb = RGBColor(0, 0, 0)
        style.paragraph_format.space_before = Pt(before)
        style.paragraph_format.space_after = Pt(after)
        style.paragraph_format.keep_with_next = True
        style.paragraph_format.line_spacing = 1.12

    title_style = styles["Title"]
    title_p_pr = title_style.element.get_or_add_pPr()
    title_border = title_p_pr.find(qn("w:pBdr"))
    if title_border is not None:
        title_p_pr.remove(title_border)

    for name in ("List Bullet", "List Number"):
        style = styles[name]
        style.font.name = BODY_FONT
        style._element.rPr.rFonts.set(qn("w:eastAsia"), BODY_FONT)
        style.font.size = Pt(10.5)
        style.paragraph_format.space_after = Pt(3)
        style.paragraph_format.line_spacing = 1.2


def add_cover(doc: Document, title: str) -> None:
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.LEFT
    p.paragraph_format.space_before = Pt(44)
    p.paragraph_format.space_after = Pt(8)
    run = p.add_run("需求分析阶段成果")
    set_run_font(run, size=10, bold=True, color=MUTED)

    p = doc.add_paragraph(style="Title")
    p.alignment = WD_ALIGN_PARAGRAPH.LEFT
    p.paragraph_format.space_before = Pt(30)
    p.paragraph_format.space_after = Pt(16)
    run = p.add_run(title)
    set_run_font(run, size=24, bold=True, color=RGBColor(0, 0, 0))
    title_p_pr = p._p.get_or_add_pPr()
    title_border = title_p_pr.find(qn("w:pBdr"))
    if title_border is not None:
        title_p_pr.remove(title_border)

    p = doc.add_paragraph()
    p.paragraph_format.space_after = Pt(50)
    run = p.add_run("产品需求文档 PRD")
    set_run_font(run, size=14, bold=False, color=MUTED)

    metadata = [
        ("目标院校", "湖北工程学院新技术学院"),
        ("项目形态", "Web 端轻量化校园数字孪生导览平台"),
        ("文档版本", "V1.0 待评审"),
        ("编制日期", "2026 年 11 月 3 日"),
        ("阶段边界", "需求分析，不进入概要设计与编码"),
    ]
    for label, value in metadata:
        paragraph = doc.add_paragraph()
        paragraph.paragraph_format.left_indent = Inches(0.02)
        paragraph.paragraph_format.space_after = Pt(7)
        paragraph.paragraph_format.line_spacing = 1.1
        run = paragraph.add_run(f"{label}    ")
        set_run_font(run, size=10, bold=True, color=MUTED)
        run = paragraph.add_run(value)
        set_run_font(run, size=10, bold=False, color=INK)

    p = doc.add_paragraph()
    p.paragraph_format.space_before = Pt(90)
    run = p.add_run("需求基线候选稿")
    set_run_font(run, size=10, bold=True, color=MUTED)
    p = doc.add_paragraph()
    run = p.add_run("本文件用于第一阶段评审。确认范围、指标和待定事项后，方可进入概要设计。")
    set_run_font(run, size=9.5, color=MUTED)
    set_paragraph_spacing(p, 3, 0, 1.15)

    doc.add_page_break()


def add_navigation(doc: Document, headings: list[str]) -> None:
    heading = doc.add_paragraph("文档导航", style="Heading 1")
    heading.paragraph_format.space_before = Pt(0)
    desc = doc.add_paragraph()
    add_inline(desc, "正文按需求分析的自然顺序组织。后续阶段将沿用本文件的编号和术语。", size=10.5, color=MUTED)
    table = doc.add_table(rows=1, cols=2)
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    set_fixed_table_layout(table)
    for cell, text, width in zip(table.rows[0].cells, ("章节", "标题"), (0.75, 5.55)):
        set_cell_width(cell, width)
        set_cell_shading(cell, NAVY)
        set_cell_margins(cell)
        set_cell_borders(cell)
        cell.vertical_alignment = WD_ALIGN_VERTICAL.CENTER
        p = cell.paragraphs[0]
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        run = p.add_run(text)
        set_run_font(run, size=9, bold=True, color=RGBColor(255, 255, 255))
    set_repeat_table_header(table.rows[0])

    for idx, heading_text in enumerate(headings):
        match = re.match(r"(\d+)\.\s+(.+)", heading_text)
        number, title = match.groups() if match else ("", heading_text)
        row = table.add_row()
        set_row_cant_split(row)
        if idx % 2 == 1:
            for cell in row.cells:
                set_cell_shading(cell, PALE_BLUE)
        for col, (text, width) in enumerate(((number, 0.75), (title, 5.55))):
            cell = row.cells[col]
            set_cell_width(cell, width)
            set_cell_margins(cell)
            set_cell_borders(cell)
            cell.vertical_alignment = WD_ALIGN_VERTICAL.CENTER
            p = cell.paragraphs[0]
            p.alignment = WD_ALIGN_PARAGRAPH.CENTER if col == 0 else WD_ALIGN_PARAGRAPH.LEFT
            set_paragraph_spacing(p, 0, 1, 1.1)
            run = p.add_run(text)
            set_run_font(run, size=9, bold=(col == 0), color=INK)

    doc.add_page_break()


def add_footer(doc: Document) -> None:
    section = doc.sections[0]
    footer = section.footer
    p = footer.paragraphs[0]
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    run = p.add_run("需求规格说明书  |  第 ")
    set_run_font(run, size=8.5, color=MUTED)
    fld_char_1 = OxmlElement("w:fldChar")
    fld_char_1.set(qn("w:fldCharType"), "begin")
    instr = OxmlElement("w:instrText")
    instr.set(qn("xml:space"), "preserve")
    instr.text = " PAGE "
    fld_char_2 = OxmlElement("w:fldChar")
    fld_char_2.set(qn("w:fldCharType"), "end")
    run._r.append(fld_char_1)
    run._r.append(instr)
    run._r.append(fld_char_2)
    run = p.add_run(" 页")
    set_run_font(run, size=8.5, color=MUTED)


def split_markdown_row(line: str) -> list[str]:
    text = line.strip()
    if text.startswith("|"):
        text = text[1:]
    if text.endswith("|"):
        text = text[:-1]
    return [cell.strip().replace("<br>", "\n") for cell in text.split("|")]


def is_separator_row(cells: list[str]) -> bool:
    return bool(cells) and all(re.fullmatch(r":?-{3,}:?", cell.strip()) for cell in cells)


def add_table(doc: Document, raw_lines: list[str]) -> None:
    rows: list[list[str]] = []
    for line in raw_lines:
        cells = split_markdown_row(line)
        if not is_separator_row(cells):
            rows.append(cells)
    if not rows:
        return
    col_count = max(len(row) for row in rows)
    normalized = [row + [""] * (col_count - len(row)) for row in rows]
    table = doc.add_table(rows=0, cols=col_count)
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    set_fixed_table_layout(table)

    available = 6.9
    if col_count == 2:
        weights = [1.2, 5.7]
    else:
        weights = []
        for col in range(col_count):
            lengths = sorted(len(row[col]) for row in normalized[1:] or normalized)
            p90 = lengths[min(len(lengths) - 1, int(len(lengths) * 0.9))]
            weights.append(max(10, min(70, p90)))
        min_weight = 0.68 if col_count >= 6 else 0.82
        normalized_weights = [max(min_weight, available * w / sum(weights)) for w in weights]
        ratio = available / sum(normalized_weights)
        weights = [w * ratio for w in normalized_weights]
    if col_count == 2:
        total = sum(weights)
        weights = [available * w / total for w in weights]
    elif re.match(r"^(?:FR|NFR|AC|UC|BR|INTENT|G|OI)-", normalized[0][0].strip()):
        first_width = 1.06 if col_count >= 5 else 1.0
        remaining_weight = sum(weights[1:])
        weights = [
            first_width,
            *[
                value * (available - first_width) / remaining_weight
                for value in weights[1:]
            ],
        ]

    font_size = 8.2 if col_count >= 7 else 8.6 if col_count >= 5 else 9.0 if col_count >= 3 else 9.4
    for r_idx, row_values in enumerate(normalized):
        row = table.add_row()
        set_row_cant_split(row)
        if r_idx == 0:
            set_repeat_table_header(row)
        for c_idx, value in enumerate(row_values):
            cell = row.cells[c_idx]
            set_cell_width(cell, weights[c_idx])
            set_cell_margins(cell, 70, 85, 70, 85)
            set_cell_borders(cell)
            cell.vertical_alignment = WD_ALIGN_VERTICAL.CENTER
            if r_idx == 0:
                set_cell_shading(cell, NAVY)
            elif r_idx % 2 == 0:
                set_cell_shading(cell, PALE_BLUE)
            p = cell.paragraphs[0]
            p.alignment = WD_ALIGN_PARAGRAPH.CENTER if len(value) <= 10 else WD_ALIGN_PARAGRAPH.LEFT
            set_paragraph_spacing(p, 0, 1, 1.08)
            if r_idx == 0:
                p.paragraph_format.keep_with_next = True
            add_inline(
                p,
                value,
                bold=(r_idx == 0),
                size=font_size,
                color=RGBColor(255, 255, 255) if r_idx == 0 else INK,
            )
    spacer = doc.add_paragraph()
    spacer.paragraph_format.space_after = Pt(2)


def add_paragraph_block(doc: Document, lines: list[str]) -> None:
    if not lines:
        return
    text = " ".join(line.strip() for line in lines)
    if text.startswith("> "):
        text = text[2:]
        p = doc.add_paragraph()
        p.paragraph_format.left_indent = Inches(0.22)
        p.paragraph_format.right_indent = Inches(0.15)
        set_paragraph_spacing(p, 2, 7, 1.25)
        add_inline(p, text, size=10.3, color=MUTED)
        return
    p = doc.add_paragraph()
    add_inline(p, text)


def add_numbered_item(doc: Document, text: str) -> None:
    p = doc.add_paragraph()
    p.paragraph_format.left_indent = Inches(0.2)
    p.paragraph_format.first_line_indent = Inches(-0.2)
    set_paragraph_spacing(p, 0, 3, 1.2)
    add_inline(p, text)


def add_bullet_item(doc: Document, text: str) -> None:
    p = doc.add_paragraph(style="List Bullet")
    add_inline(p, text)


def build() -> None:
    markdown = SOURCE.read_text(encoding="utf-8")
    lines = markdown.splitlines()
    title = lines[0][2:].strip() if lines and lines[0].startswith("# ") else "需求规格说明书"
    headings = [
        match.group(1)
        for line in lines
        if (match := re.match(r"^## (\d+\. .+)$", line))
    ]

    doc = Document()
    style_document(doc)
    props = doc.core_properties
    props.title = title
    props.subject = "基于 AI 数字人的校园导览系统需求规格说明书"
    props.author = "毕业设计项目组"
    props.keywords = "PRD, 校园导览, 数字人, Three.js, RAG, Spring Boot"
    props.comments = "第一阶段需求分析成果，待项目负责人评审。"

    add_cover(doc, title)
    add_navigation(doc, headings)
    add_footer(doc)

    i = next(
        (idx for idx, line in enumerate(lines) if line.startswith("## ")),
        len(lines),
    )

    while i < len(lines):
        line = lines[i]
        stripped = line.strip()
        if not stripped:
            i += 1
            continue
        if stripped.startswith("# "):
            i += 1
            continue
        if stripped.startswith("#### "):
            p = doc.add_paragraph(stripped[5:], style="Heading 3")
            set_paragraph_spacing(p, 10, 4, 1.12)
            i += 1
            continue
        if stripped.startswith("### "):
            p = doc.add_paragraph(stripped[4:], style="Heading 2")
            set_paragraph_spacing(p, 12, 5, 1.12)
            i += 1
            continue
        if stripped.startswith("## "):
            p = doc.add_paragraph(stripped[3:], style="Heading 1")
            set_paragraph_spacing(p, 16, 8, 1.12)
            i += 1
            continue
        if stripped.startswith("|"):
            batch = []
            while i < len(lines) and lines[i].strip().startswith("|"):
                batch.append(lines[i])
                i += 1
            if doc.paragraphs:
                doc.paragraphs[-1].paragraph_format.keep_with_next = True
            add_table(doc, batch)
            continue
        if re.match(r"^\d+\.\s+", stripped):
            add_numbered_item(doc, stripped)
            i += 1
            continue
        if stripped.startswith("- "):
            add_bullet_item(doc, stripped[2:])
            i += 1
            continue
        if stripped.startswith(">"):
            add_paragraph_block(doc, [stripped])
            i += 1
            continue

        batch = [stripped]
        i += 1
        while i < len(lines):
            nxt = lines[i].strip()
            if not nxt or nxt.startswith(("#", "|", ">")) or re.match(r"^\d+\.\s+", nxt) or nxt.startswith("- "):
                break
            batch.append(nxt)
            i += 1
        add_paragraph_block(doc, batch)

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    doc.save(OUTPUT)
    print(OUTPUT)


if __name__ == "__main__":
    build()
