"""mermaid 图校验与修正：模型经常把围栏写歪、甚至不写围栏，先归一化再校验，宁可丢图也不让客户端渲染报错。"""

import re

FENCE_RE = re.compile(r"```([A-Za-z0-9_+\-]*)\n(.*?)```", re.S)
ALLOWED_HEADS = ("flowchart", "graph", "sequenceDiagram")
HEAD_RE = re.compile(r"^[\s_*`>\-\u3000]*((?:flowchart|graph|sequenceDiagram))\b(.*)$")
MAX_LINES = 18
BAD_TYPES = ("mindmap", "classDiagram", "stateDiagram", "gantt", "pie", "journey", "timeline")
BODY_KEYWORDS = (
    "subgraph",
    "end",
    "style",
    "classdef",
    "class ",
    "click",
    "linkstyle",
    "direction",
)


def _first_line(block: str) -> str:
    return next((line.strip() for line in block.splitlines() if line.strip()), "")


def _looks_like_diagram(block: str) -> bool:
    return bool(HEAD_RE.match(_first_line(block)))


def _is_diagram_body_line(line: str) -> bool:
    text = line.strip()
    if not text:
        return False
    if text.startswith(BODY_KEYWORDS):
        return True
    return "-->" in text or "->" in text


def _clean_head(line: str) -> str:
    head = HEAD_RE.match(line.strip())
    if not head:
        return line.strip()
    return (head.group(1) + head.group(2)).strip()


def _wrap_bare_diagrams(md: str) -> str:
    """模型有时整段不写围栏，直接把 flowchart 画在正文里；这里补回围栏，否则手机端只能看到裸文本。"""
    lines = md.split("\n")
    out: list[str] = []
    index = 0
    in_fence = False
    while index < len(lines):
        line = lines[index]
        if line.strip().startswith("```"):
            in_fence = not in_fence
            out.append(line)
            index += 1
            continue
        if not in_fence and line.strip() and HEAD_RE.match(line.strip()):
            body: list[str] = []
            cursor = index + 1
            while cursor < len(lines):
                nxt = lines[cursor]
                if nxt.strip().startswith("```") or not _is_diagram_body_line(nxt):
                    break
                body.append(nxt.strip())
                cursor += 1
            if body:
                out.append("```" + "mermaid")
                out.append(_clean_head(line))
                out.extend(body)
                out.append("```")
                index = cursor
                continue
        out.append(line)
        index += 1
    return "\n".join(out)


def normalize(md: str) -> str:
    """把「没有 mermaid 标记但内容是流程图」「首行带杂字符」的围栏改写成标准写法。"""
    md = _wrap_bare_diagrams(md)

    def repl(match):
        tag = match.group(1).lower()
        block = match.group(2)
        if tag not in ("", "mermaid") or not _looks_like_diagram(block):
            return match.group(0)
        lines = block.splitlines()
        for idx, line in enumerate(lines):
            if not line.strip():
                continue
            lines[idx] = _clean_head(line)
            break
        body = "\n".join(lines).strip()
        return "```" + "mermaid\n" + body + "\n```"

    return FENCE_RE.sub(repl, md)


def extract_blocks(md: str) -> list:
    return [block for tag, block in FENCE_RE.findall(md) if tag.lower() == "mermaid"]


def validate_block(src: str) -> list:
    errors = []
    lines = [line.strip() for line in src.splitlines() if line.strip()]
    if not lines:
        return ["图为空"]
    if not lines[0].startswith(ALLOWED_HEADS):
        errors.append("第一行必须是 flowchart 或 sequenceDiagram，实际是：" + lines[0][:24])
    if len(lines) > MAX_LINES:
        errors.append("图共 %d 行，超过 %d 行" % (len(lines), MAX_LINES))

    body = "\n".join(lines)
    if body.count(chr(34)) % 2 != 0:
        errors.append("双引号不成对")
    for left, right in (("[", "]"), ("(", ")"), ("{", "}")):
        if body.count(left) != body.count(right):
            errors.append(left + right + " 括号不配对")

    lowered = body.lower()
    if "<br" in lowered or "<div" in lowered:
        errors.append("不要在图里用 HTML 标签")
    for bad in BAD_TYPES:
        if lines[0].startswith(bad):
            errors.append("不支持的图类型：" + bad)
    return errors


def validate_all(md: str) -> list:
    problems = []
    for idx, block in enumerate(extract_blocks(md), start=1):
        for err in validate_block(block):
            problems.append("第 %d 张图：%s" % (idx, err))
    return problems


def drop_invalid_blocks(md: str) -> tuple:
    """把所有校验不过的 mermaid 图整块删掉，返回 (清理后的 markdown, 是否删过)。"""
    dropped = False

    def repl(match):
        nonlocal dropped
        tag = match.group(1).lower()
        if tag == "mermaid" and validate_block(match.group(2)):
            dropped = True
            return ""
        return match.group(0)

    cleaned = FENCE_RE.sub(repl, md)
    if dropped:
        cleaned = re.sub(r"\n{3,}", "\n\n", cleaned).strip() + "\n"
    return cleaned, dropped
