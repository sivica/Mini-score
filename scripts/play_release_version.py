"""Assign repeatable, increasing Play versions to GitHub release workflow runs."""
import argparse
import re
from pathlib import Path

MAX_VERSION_CODE = 2_100_000_000


def release_version(run_number, offset, gradle_source, version_name=""):
    if not re.fullmatch(r"[1-9][0-9]*", run_number):
        raise ValueError("Workflow run number must be a positive integer")
    if not re.fullmatch(r"0|[1-9][0-9]*", offset):
        raise ValueError("PLAY_VERSION_CODE_OFFSET must be a non-negative integer")
    base_code = re.search(r"versionCode\s*=.*?\?:\s*([0-9]+)", gradle_source)
    base_name = re.search(r'versionName\s*=.*?\?:\s*"([^"\n]+)"', gradle_source)
    if not base_code or not base_name:
        raise ValueError("App version defaults could not be read from app/build.gradle.kts")
    code = int(offset) + int(run_number)
    if not int(base_code[1]) < code <= MAX_VERSION_CODE:
        raise ValueError("Automatic code must exceed the app's base code and be at most 2100000000; adjust PLAY_VERSION_CODE_OFFSET")
    name = version_name or f"{base_name[1]}.{int(run_number)}"
    if not name.strip() or len(name) > 50 or any(ord(char) < 32 or ord(char) == 127 for char in name):
        raise ValueError("Version name must contain 1–50 characters without control characters")
    return code, name


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run-number", required=True)
    parser.add_argument("--offset", default="1000")
    parser.add_argument("--version-name", default="")
    parser.add_argument("--gradle-file", type=Path, default=Path("app/build.gradle.kts"))
    args = parser.parse_args()
    try:
        code, name = release_version(args.run_number, args.offset, args.gradle_file.read_text(), args.version_name)
    except ValueError as exc:
        parser.error(str(exc))
    print(f"version_code={code}\nversion_name={name}")


if __name__ == "__main__":
    main()
