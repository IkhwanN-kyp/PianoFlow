#!/usr/bin/env python3
import re,sys
m=re.match(r'v?(\d+)\.(\d+)\.(\d+)',sys.argv[1]);major,minor,patch=map(int,m.groups());patch+=1
if patch>99:minor+=1;patch=0
if minor>99:major+=1;minor=0
print(f'{major}.{minor}.{patch}-alpha.0')
