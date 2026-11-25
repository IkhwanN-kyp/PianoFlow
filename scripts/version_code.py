#!/usr/bin/env python3
"""Monotonic Android version codes for SemVer alpha.N, beta.N, rc.N and stable."""
import re,sys
version=sys.argv[1].removeprefix('v')
m=re.fullmatch(r'(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-(alpha|beta|rc)\.(0|[1-9]\d*))?',version)
if not m:raise SystemExit('Use MAJOR.MINOR.PATCH[-alpha.N|-beta.N|-rc.N]')
major,minor,patch=map(int,m.group(1,2,3))
if major>20 or minor>99 or patch>99:raise SystemExit('Version components exceed Android version-code budget')
stage={'alpha':1000,'beta':2000,'rc':3000,None:9000}[m.group(4)];iteration=int(m.group(5) or '0')
if iteration>999:raise SystemExit('Prerelease iteration must be <=999')
code=major*100_000_000+minor*1_000_000+patch*10_000+stage+iteration
if code>2_100_000_000:raise SystemExit('Version code exceeds Android limit')
print(code)
