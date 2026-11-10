import json
from tools.check_android_compatibility import check_local, text_sha256


def fixture(tmp_path):
    core = "a" * 40
    (tmp_path / "source-basis.json").write_text(json.dumps({"nativeSourceCommit": core}))
    (tmp_path / "runtime.h").write_bytes(b"host version 4\n")
    (tmp_path / "hooks.txt").write_bytes(b"timing\nframe\n")
    lock = {"nativeCoreCommit": core, "files": {
        "runtime": {"runtime.h": text_sha256(tmp_path / "runtime.h")},
        "hooks": {"hooks.txt": text_sha256(tmp_path / "hooks.txt")}}}
    return lock


def test_matching_sources_are_read_only_and_line_endings_are_portable(tmp_path):
    lock = fixture(tmp_path)
    (tmp_path / "runtime.h").write_bytes(b"host version 4\r\n")
    before = {p.name: p.read_bytes() for p in tmp_path.iterdir()}
    assert check_local(tmp_path, lock)["compatibleWithRecordedSources"]
    assert before == {p.name: p.read_bytes() for p in tmp_path.iterdir()}


def test_runtime_change_cannot_silently_reuse_the_old_library(tmp_path):
    lock = fixture(tmp_path)
    (tmp_path / "runtime.h").write_text("host version 5\n")
    result = check_local(tmp_path, lock)
    assert not result["compatibleWithRecordedSources"]
    assert result["errors"][0]["kind"] == "runtime"


def test_added_hook_requires_review_even_if_runtime_abi_is_unchanged(tmp_path):
    lock = fixture(tmp_path)
    (tmp_path / "hooks.txt").write_text("timing\nframe\nnew hook\n")
    assert check_local(tmp_path, lock)["errors"][0]["file"] == "hooks.txt"


def test_new_core_pin_requires_review(tmp_path):
    lock = fixture(tmp_path)
    (tmp_path / "source-basis.json").write_text(json.dumps({"nativeSourceCommit": "b" * 40}))
    assert check_local(tmp_path, lock)["errors"][0]["kind"] == "core"


def test_changed_checkout_is_detected_even_with_stale_metadata(tmp_path):
    lock = fixture(tmp_path)
    assert check_local(tmp_path, lock, "b" * 40)["errors"][0]["kind"] == "nativeCheckout"


def test_matching_actual_checkout_and_metadata_are_accepted(tmp_path):
    lock = fixture(tmp_path)
    assert check_local(tmp_path, lock, "a" * 40)["compatibleWithRecordedSources"]


def test_missing_contract_file_is_reported(tmp_path):
    lock = fixture(tmp_path)
    (tmp_path / "hooks.txt").unlink()
    assert not check_local(tmp_path, lock)["compatibleWithRecordedSources"]


def test_manifest_cannot_read_outside_the_repository(tmp_path):
    lock = fixture(tmp_path)
    lock["files"]["runtime"]["../outside-file"] = "0" * 64
    assert any(error["kind"] == "path" for error in check_local(tmp_path, lock)["errors"])
