#!/usr/bin/env python3
"""Verify pinned binaries, bank, optional model and corresponding source archives before building."""
from pathlib import Path
import hashlib,json,sys
root=Path(__file__).resolve().parents[1]
records=json.loads((root/'third_party/provenance.json').read_text())
for record in records:
 path=root/record['path']
 if not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest()!=record['sha256']:raise SystemExit('Vendor checksum mismatch: '+record['path'])
for record in json.loads((root/'third_party/sources/provenance.json').read_text()):
 path=root/'third_party/sources'/record['file']
 if hashlib.sha256(path.read_bytes()).hexdigest()!=record['sha256']:raise SystemExit('Source checksum mismatch: '+record['file'])
print('PASS vendor hashes:',len(records),'assets/binaries and corresponding source archives')
