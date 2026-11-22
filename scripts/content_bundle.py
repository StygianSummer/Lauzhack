"""Bundle the JSON files under content/ into one file per language for bulk editing, and split them back.

Usage (from the repo root):
    python scripts/content_bundle.py merge   # content/**/*.json -> content.bundle.<lang>.json
    python scripts/content_bundle.py split   # content.bundle.*.json -> content/**/*.json

Files named *.<lang>.json go to content.bundle.<lang>.json (en, fr, es, ...).
Files without a language suffix (e.g. resources/resources.json) go to
content.bundle.common.json.

Each bundle is an object keyed by the file's path relative to content/
(e.g. "items/email/pl-l1-scam.fr.json"), each value being that file's JSON.
`split` only rewrites files whose parsed content actually changed, keeps each
file's original line endings, and creates new files for new keys. It never
deletes files: removing a key from a bundle leaves the file in place.
"""

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CONTENT = ROOT / "content"
LANG_RE = re.compile(r"\.([a-z]{2})\.json$")


def bundle_path(group: str) -> Path:
    return ROOT / f"content.bundle.{group}.json"


def dump(data) -> str:
    return json.dumps(data, indent=2, ensure_ascii=False) + "\n"


def merge() -> None:
    groups: dict[str, dict] = {}
    for f in sorted(CONTENT.rglob("*.json")):
        key = f.relative_to(CONTENT).as_posix()
        m = LANG_RE.search(f.name)
        group = m.group(1) if m else "common"
        groups.setdefault(group, {})[key] = json.loads(f.read_text(encoding="utf-8"))
    for group, bundle in sorted(groups.items()):
        out = bundle_path(group)
        out.write_text(dump(bundle), encoding="utf-8", newline="\n")
        print(f"Wrote {len(bundle):3d} files into {out.name}")


def split() -> None:
    bundles = sorted(ROOT.glob("content.bundle.*.json"))
    if not bundles:
        sys.exit("No content.bundle.*.json found; run `merge` first.")
    changed = created = total = 0
    for path in bundles:
        bundle = json.loads(path.read_text(encoding="utf-8"))
        for key, data in bundle.items():
            total += 1
            target = (CONTENT / key).resolve()
            if CONTENT.resolve() not in target.parents:
                sys.exit(f"Refusing to write outside content/: {key}")
            newline = "\n"
            if target.exists():
                raw = target.read_bytes()
                if json.loads(raw.decode("utf-8")) == data:
                    continue
                newline = "\r\n" if b"\r\n" in raw else "\n"
                changed += 1
                status = "updated"
            else:
                target.parent.mkdir(parents=True, exist_ok=True)
                created += 1
                status = "created"
            target.write_text(dump(data), encoding="utf-8", newline=newline)
            print(f"  {status}: {key}  (from {path.name})")
    print(f"Split done: {changed} updated, {created} created, "
          f"{total - changed - created} unchanged.")


if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else ""
    if cmd == "merge":
        merge()
    elif cmd == "split":
        split()
    else:
        sys.exit(__doc__)
