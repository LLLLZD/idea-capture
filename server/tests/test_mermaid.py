"""mermaid 归一化与校验的回归用例：直接 python tests/test_mermaid.py 跑。"""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app import mermaid  # noqa: E402


def test_bare_diagram_gets_fenced():
    md = """整体长这样：

_flowchart TD
  A[开始] --> B[转写]
  B -->|失败| C[提示重录]

## 下一节
"""
    out = mermaid.normalize(md)
    assert "```" + "mermaid" in out, out
    assert "flowchart TD" in out and "_flowchart" not in out, out
    blocks = mermaid.extract_blocks(out)
    assert len(blocks) == 1, blocks
    assert mermaid.validate_block(blocks[0]) == [], mermaid.validate_block(blocks[0])


def test_bare_diagram_stops_at_prose():
    md = """一段正文。

flowchart LR
  A --> B
这句话是正文，不是图。
"""
    out = mermaid.normalize(md)
    assert "这句话是正文，不是图。" in out
    assert mermaid.extract_blocks(out) and "A --> B" in mermaid.extract_blocks(out)[0]


def test_existing_mermaid_fence_untouched():
    md = "```mermaid\nflowchart TD\n  A --> B\n```\n"
    out = mermaid.normalize(md)
    assert mermaid.validate_block(mermaid.extract_blocks(out)[0]) == []


def test_drop_invalid_blocks_removes_bad_graph():
    md = "前文\n\n```mermaid\nmindmap\n  root\n```\n\n后文\n"
    cleaned, dropped = mermaid.drop_invalid_blocks(md)
    assert dropped is True
    assert "mindmap" not in cleaned
    assert "前文" in cleaned and "后文" in cleaned


def test_bare_prose_without_arrows_untouched():
    md = "graph 这个词在正文里出现，但没有箭头。\n"
    out = mermaid.normalize(md)
    assert "```" + "mermaid" not in out, out


if __name__ == "__main__":
    failures = 0
    for name, fn in sorted(globals().items()):
        if name.startswith("test_") and callable(fn):
            try:
                fn()
                print("PASS", name)
            except AssertionError as exc:
                failures += 1
                print("FAIL", name, "->", exc)
    raise SystemExit(1 if failures else 0)
