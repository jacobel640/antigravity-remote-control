#!/usr/bin/env python3
"""
Static checker for Antigravity Coding Standards:
- No Fully Qualified Names Inline (e.g. android.os.Bundle, java.util.List, androidx.security.crypto...)
- Always use explicit imports
- No duplicate imports
"""

import os
import re
import sys

PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC_DIR = os.path.join(PROJECT_ROOT, "app", "src")

KNOWN_PREFIXES = [
    r"android\.",
    r"androidx\.",
    r"java\.",
    r"javax\.",
    r"kotlin\.",
    r"kotlinx\.",
    r"org\.junit\.",
    r"org\.robolectric\.",
    r"org\.mockito\.",
    r"io\.mockk\.",
    r"com\.antigravity\.",
    r"com\.google\."
]

FQN_PATTERN = re.compile(r'(?<![a-zA-Z0-9_])(?:' + '|'.join(KNOWN_PREFIXES) + r')[a-zA-Z0-9_.]+')

def check_file(file_path):
    violations = []
    with open(file_path, "r", encoding="utf-8") as f:
        lines = f.readlines()

    imports = []
    in_multiline_comment = False

    for idx, raw_line in enumerate(lines, start=1):
        line = raw_line.strip()

        # Handle comments
        if "/*" in line and "*/" in line:
            # Single line block comment
            pass
        elif "/*" in line:
            in_multiline_comment = True
            continue
        elif "*/" in line:
            in_multiline_comment = False
            continue
        elif in_multiline_comment or line.startswith("//"):
            continue

        # Check for imports / package declarations
        if line.startswith("package "):
            continue
        if line.startswith("import "):
            import_stmt = line[7:].split(";")[0].strip()
            if import_stmt in imports:
                violations.append((idx, f"Duplicate import: {import_stmt}"))
            else:
                imports.append(import_stmt)
            continue

        # Strip string literals to avoid false positives on URLs/log strings
        stripped_code = re.sub(r'".*?"', '""', raw_line)
        stripped_code = re.sub(r'//.*', '', stripped_code)

        # Check for inline FQNs
        matches = FQN_PATTERN.findall(stripped_code)
        for m in matches:
            # Allow R.id, R.string, etc. and ignore non-class namespaces if any
            # Also allow standard annotation names if imported
            if m.startswith("android.R."):
                # android.R is an inline resource class reference if not imported
                violations.append((idx, f"Inline FQN found: {m} (should import android.R)"))
            else:
                violations.append((idx, f"Inline FQN found: {m}"))

    return violations

def main():
    total_violations = 0
    checked_files = 0

    for root, _, files in os.walk(SRC_DIR):
        for file in files:
            if file.endswith(".kt") or file.endswith(".java"):
                checked_files += 1
                fpath = os.path.join(root, file)
                v = check_file(fpath)
                if v:
                    rel_path = os.path.relpath(fpath, PROJECT_ROOT)
                    print(f"\n[VIOLATION] {rel_path}:")
                    for line_no, msg in v:
                        print(f"  Line {line_no}: {msg}")
                    total_violations += len(v)

    print(f"\nChecked {checked_files} source files.")
    if total_violations == 0:
        print("[SUCCESS] Zero coding standard / inline FQN violations found!")
        sys.exit(0)
    else:
        print(f"[FAILURE] Found {total_violations} violation(s).")
        sys.exit(1)

if __name__ == "__main__":
    main()
