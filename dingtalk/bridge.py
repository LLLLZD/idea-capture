#!/usr/bin/env python3
"""Turn each finished Speakr summary into a DingTalk doc, then send me the link.

Runs on the NAS host (not in a container) because it shells out to the official
dws CLI, which holds the personal DingTalk login in ~/.dws. No enterprise app,
no group robot: the doc lands in My Docs / IDEA and the link arrives as a
one-to-one chat message.

Speakr webhooks cannot be used from the same box: delivery passes a SSRF guard
that rejects loopback and private addresses (src/services/webhook_dispatch.py),
so this polls the read API instead.

Usage:
    python3 bridge.py               # poll forever
    python3 bridge.py --once        # one tick, exit
    python3 bridge.py --publish ID  # publish one recording now (deploy check)
    python3 bridge.py --selftest    # assert text building, no network
"""
import hashlib
import json
import os
import subprocess
import sys
import tempfile
import time
import urllib.parse
import urllib.request
from datetime import datetime, timedelta, timezone

BASE = os.environ.get("SPEAKR_BASE", "http://127.0.0.1:8899").rstrip("/")
SITE = os.environ.get("SPEAKR_SITE", "https://capture.example.com")
TOKEN = os.environ.get("SPEAKR_TOKEN", "")
DWS_BIN = os.environ.get("DWS_BIN", os.path.expanduser("~/.local/bin/dws"))
DWS_USER_ID = os.environ.get("DWS_USER_ID", "")
DWS_FOLDER_ID = os.environ.get("DWS_FOLDER_ID", "")
DOC_PREFIX = os.environ.get("DWS_DOC_PREFIX", "IDEA 报告")
POLL = int(os.environ.get("POLL_SECONDS", "30"))
STATE_PATH = os.path.expanduser(os.environ.get("STATE_PATH", "~/bridge/state/state.json"))
LOOKBACK_SECONDS = int(os.environ.get("LOOKBACK_SECONDS", "300"))
MAX_STATE_ENTRIES = 500


def speakr(path):
    req = urllib.request.Request(BASE + path, headers={"Authorization": "Bearer " + TOKEN})
    with urllib.request.urlopen(req, timeout=60) as resp:
        return json.loads(resp.read().decode() or "{}")


def json_from(text):
    """dws prints one JSON document on stdout, sometimes behind log noise."""
    decoder = json.JSONDecoder()
    index = text.find("{")
    while index != -1:
        try:
            obj, _ = decoder.raw_decode(text[index:])
            return obj
        except ValueError:
            index = text.find("{", index + 1)
    return {}


def find_key(node, key):
    """Depth-first lookup: dws nests results differently per command."""
    if isinstance(node, dict):
        if key in node:
            return node[key]
        for value in node.values():
            found = find_key(value, key)
            if found is not None:
                return found
    elif isinstance(node, list):
        for value in node:
            found = find_key(value, key)
            if found is not None:
                return found
    return None


def dws(args, timeout=180):
    cmd = [DWS_BIN] + args
    env = dict(os.environ)
    env["PATH"] = os.path.dirname(DWS_BIN) + ":" + env.get("PATH", "")
    proc = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout, env=env)
    if proc.returncode != 0:
        raise RuntimeError("dws %s failed: %s" % (args[0], (proc.stderr or proc.stdout).strip()[:400]))
    data = json_from(proc.stdout)
    if isinstance(data, dict) and data.get("success") is False:
        raise RuntimeError("dws %s reported: %s" % (args[0], data.get("message") or json.dumps(data)[:200]))
    return data


def doc_name(title):
    return "%s · %s" % (DOC_PREFIX, (title or "未命名").strip())


def message_text(title, doc_url):
    return "**%s**\n\n[在钉钉文档里打开](%s)" % (doc_name(title), doc_url)


def publish(rec):
    """Create the doc, then send its link as a one-to-one message."""
    rec_id = rec["id"]
    title = rec.get("title") or "未命名"
    summary = (rec.get("summary") or "").strip()
    body = summary + "\n\n---\n\n来源：Speakr " + SITE + "/recording/" + str(rec_id) + "\n"
    handle = tempfile.NamedTemporaryFile("w", suffix=".md", delete=False, encoding="utf-8")
    try:
        handle.write(body)
        handle.close()
        created = dws(["doc", "create", "--name", doc_name(title), "--folder", DWS_FOLDER_ID,
                       "--content-file", handle.name, "-f", "json"])
    finally:
        os.unlink(handle.name)
    node_id = find_key(created, "nodeId")
    if not node_id:
        raise RuntimeError("doc create returned no nodeId: %s" % json.dumps(created)[:300])
    info = dws(["doc", "info", "--node", node_id, "-f", "json"])
    doc_url = find_key(info, "docUrl") or ("https://alidocs.dingtalk.com/i/nodes/" + node_id)
    dws(["chat", "message", "send", "--user", DWS_USER_ID, "--title", DOC_PREFIX,
         "--content", message_text(title, doc_url), "-f", "json"])
    return node_id, doc_url


def load_state():
    try:
        with open(STATE_PATH) as fh:
            state = json.load(fh)
    except (OSError, ValueError):
        state = {}
    state.setdefault("published", {})
    return state


def save_state(state):
    merged = dict(load_state()["published"])
    merged.update(state["published"])
    state["published"] = merged
    if len(state["published"]) > MAX_STATE_ENTRIES:
        keep = sorted(state["published"].items(), key=lambda kv: kv[1])[-MAX_STATE_ENTRIES:]
        state["published"] = dict(keep)
    os.makedirs(os.path.dirname(STATE_PATH), exist_ok=True)
    tmp = STATE_PATH + ".tmp"
    with open(tmp, "w") as fh:
        json.dump(state, fh)
    os.replace(tmp, STATE_PATH)


def summary_key(rec_id, summary):
    return "%s:%s" % (rec_id, hashlib.sha1(summary.encode()).hexdigest()[:12])


def tick(state):
    since = (datetime.now(timezone.utc) - timedelta(seconds=LOOKBACK_SECONDS)).strftime("%Y-%m-%dT%H:%M:%SZ")
    listing = speakr("/api/v1/recordings?status=completed&per_page=50&updated_since=" + urllib.parse.quote(since))
    published = 0
    for item in listing.get("recordings", []):
        rec_id = item.get("id")
        if not rec_id:
            continue
        detail = speakr("/api/v1/recordings/%s?include=summary" % rec_id)
        summary = (detail.get("summary") or "").strip()
        if not summary:
            continue
        key = summary_key(rec_id, summary)
        if key in state["published"]:
            continue
        try:
            node_id, doc_url = publish(detail)
        except Exception as exc:
            print("publish failed for %s: %s" % (rec_id, exc), flush=True)
            continue
        state["published"][key] = int(time.time())
        save_state(state)
        published += 1
        print("published recording %s -> %s" % (rec_id, doc_url), flush=True)
    return published


def selftest():
    assert doc_name("陈泾路 2") == "IDEA 报告 · 陈泾路 2"
    text = message_text("陈泾路 2", "https://alidocs.dingtalk.com/i/nodes/abc")
    assert "IDEA 报告" in text and "](https://alidocs.dingtalk.com/i/nodes/abc)" in text
    assert json_from("log noise\n{\"success\": true, \"nodeId\": \"N1\"}")["nodeId"] == "N1"
    assert find_key({"data": {"result": {"docUrl": "u"}}}, "docUrl") == "u"
    assert find_key({"a": 1}, "docUrl") is None
    assert summary_key(1, "x") != summary_key(2, "x")
    print("selftest ok")


def main(argv):
    if "--selftest" in argv:
        selftest()
        return 0
    missing = [n for n, v in (("SPEAKR_TOKEN", TOKEN), ("DWS_USER_ID", DWS_USER_ID),
                              ("DWS_FOLDER_ID", DWS_FOLDER_ID)) if not v]
    if missing:
        print("missing config: " + ", ".join(missing), file=sys.stderr)
        return 2
    state = load_state()
    if "--publish" in argv:
        rec_id = argv[argv.index("--publish") + 1]
        detail = speakr("/api/v1/recordings/%s?include=summary" % rec_id)
        if not (detail.get("summary") or "").strip():
            print("recording %s has no summary yet" % rec_id, file=sys.stderr)
            return 1
        node_id, doc_url = publish(detail)
        state["published"][summary_key(rec_id, detail["summary"].strip())] = int(time.time())
        save_state(state)
        print(doc_url)
        return 0
    if "--once" in argv:
        print("published %d" % tick(state))
        return 0
    print("bridging %s -> DingTalk docs (%s) every %ds" % (BASE, DOC_PREFIX, POLL), flush=True)
    while True:
        try:
            tick(state)
        except Exception as exc:
            print("tick failed: %s" % exc, flush=True)
        time.sleep(POLL)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
