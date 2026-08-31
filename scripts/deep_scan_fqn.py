#!/usr/bin/env python3
import os
import re

PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC_DIR = os.path.join(PROJECT_ROOT, "app", "src")

# Check for all package-like references with dots in code bodies
# Any occurrence of `word.word.word` where the first word is a known package prefix
PACKAGE_ROOTS = [
    "android", "androidx", "java", "javax", "kotlin", "kotlinx",
    "com", "org", "io", "net", "sun"
]

pattern = re.compile(r'\b(' + '|'.join(PACKAGE_ROOTS) + r')\.[a-zA-Z0-9_.]+')

for root, _, files in os.walk(SRC_DIR):
    for f in files:
        if not f.endswith(".kt") and not f.endswith(".java"):
            continue
        path = os.path.join(root, f)
        with open(path, "r", encoding="utf-8") as file:
            lines = file.readlines()
        in_comment = False
        for i, line in enumerate(lines, 1):
            s = line.strip()
            if "/*" in s and "*/" in s:
                pass
            elif "/*" in s:
                in_comment = True
                continue
            elif "*/" in s:
                in_comment = False
                continue
            if in_comment or s.startswith("//"):
                continue
            if s.startswith("package ") or s.startswith("import "):
                continue
            
            # Strip strings
            code_line = re.sub(r'"(\\.|[^"\\])*"', '""', line)
            code_line = re.sub(r'//.*', '', code_line)
            
            # Find any package pattern
            matches = pattern.findall(code_line)
            # Find full match text
            for match in pattern.finditer(code_line):
                matched_str = match.group(0)
                # Ignore things like AndroidR or variables named com/org if not dotted package
                print(f"{os.path.relpath(path, PROJECT_ROOT)}:{i} -> {matched_str}")
