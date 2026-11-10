#!/usr/bin/env python3
"""Read-only checks for the pinned Android core, recompiler and game-library contract."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import urllib.parse
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
LOCK = "config/android-compatibility.json"


def text_sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes().replace(b"\r\n", b"\n")).hexdigest()


def check_local(root: Path, lock: dict, native_checkout_commit: str | None = None) -> dict:
    """A changed contract requires review/rebuild; never silently refresh its hashes."""
    errors = []
    basis = json.loads((root / "source-basis.json").read_text(encoding="utf-8"))
    if basis.get("nativeSourceCommit") != lock["nativeCoreCommit"]:
        errors.append({"kind": "core", "message": "Pinned native core changed; rebuild and test the engine/library pair."})
    if native_checkout_commit is not None and native_checkout_commit != lock["nativeCoreCommit"]:
        errors.append({"kind": "nativeCheckout", "message": "The actual native checkout differs from the recorded core."})
    for group, files in lock["files"].items():
        for name, expected in files.items():
            path = (root / name).resolve()
            if not path.is_relative_to(root.resolve()):
                errors.append({"kind": "path", "file": name, "message": "Contract path escapes the repository."})
                continue
            if not path.is_file():
                errors.append({"kind": group, "file": name, "message": "Required source file is missing."})
            elif text_sha256(path) != expected:
                errors.append({"kind": group, "file": name, "message": "Source changed; review compatibility before reusing generated code."})
    return {"compatibleWithRecordedSources": not errors, "errors": errors,
            "nativeCoreCommit": lock["nativeCoreCommit"],
            "checkedFiles": sum(len(group) for group in lock["files"].values())}


def latest_commit(repository: str, reference: str) -> str:
    repo = urllib.parse.quote(repository, safe="/")
    ref = urllib.parse.quote(reference, safe="")
    request = urllib.request.Request(f"https://api.github.com/repos/{repo}/commits/{ref}",
        headers={"Accept": "application/vnd.github+json", "User-Agent": "MH4U-compatibility-check"})
    with urllib.request.urlopen(request, timeout=20) as response:
        return json.load(response)["sha"]


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--latest", action="store_true", help="also report public upstream tips; does not fetch/merge/install")
    args = parser.parse_args(argv)
    lock = json.loads((ROOT / LOCK).read_text(encoding="utf-8"))
    checkout = ROOT / ".local/azahar"
    core_head = None
    if (checkout / ".git").exists():
        core_head = subprocess.check_output(["git", "-c", f"safe.directory={checkout}",
            "-C", str(checkout), "rev-parse", "HEAD"], text=True).strip()
    result = check_local(ROOT, lock, core_head)
    result["nativeCheckoutCommit"] = core_head
    if args.latest:
        result["upstream"] = []
        for source in lock["upstream"]:
            entry = dict(source)
            try:
                entry["latestCommit"] = latest_commit(source["repository"], source["reference"])
                entry["differsFromBaseline"] = entry["latestCommit"] != source["baselineCommit"]
            except Exception as error:
                entry["lookupError"] = str(error)
            result["upstream"].append(entry)
    print(json.dumps(result, indent=2))
    return 0 if result["compatibleWithRecordedSources"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
