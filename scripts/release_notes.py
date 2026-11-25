#!/usr/bin/env python3
from pathlib import Path
import re,sys
root=Path(__file__).resolve().parents[1];version=sys.argv[1];text=(root/'CHANGELOG.md').read_text();match=re.search(r'^## \['+re.escape(version)+r'\][^\n]*\n(.*?)(?=^## \[|\Z)',text,re.M|re.S)
if not match:raise SystemExit('Add this version to CHANGELOG.md before release')
print(match.group(1).strip());print('\nInstall the ARM64 APK. Keep the existing installation to retain data. See README and verification docs for physical-device limitations. Optional Basic Pitch model: basic-pitch.tflite. LGPL native corresponding source and license archive included.')
