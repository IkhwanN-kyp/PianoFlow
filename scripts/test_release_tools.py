#!/usr/bin/env python3
"""Regression checks for actual changelog sections and monotonic Android release codes."""
import subprocess,sys
from pathlib import Path
root=Path(__file__).resolve().parents[1]
def run(script,version):return subprocess.check_output([sys.executable,str(root/'scripts'/script),version],text=True)
beta1=run('release_notes.py','0.2.0-beta.1');beta2=run('release_notes.py','0.2.0-beta.2')
assert 'FluidSynth SF2 audio' in beta1
assert 'Preserve MIDI track IDs' in beta2
assert 'Rename Arus Piano' not in beta2
assert int(run('version_code.py','0.2.0-beta.2'))>int(run('version_code.py','0.2.0-beta.1'))
assert int(run('version_code.py','0.2.0'))>int(run('version_code.py','0.2.0-rc.999'))
assert int(run('version_code.py','0.2.1-alpha.0'))>int(run('version_code.py','0.2.0'))
print('PASS: 6 release-tool regressions (changelog section boundaries and version-code ordering)')
