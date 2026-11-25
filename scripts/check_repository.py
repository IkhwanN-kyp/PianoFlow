#!/usr/bin/env python3
from pathlib import Path
import subprocess,re
root=Path(__file__).resolve().parents[1]
paths=subprocess.check_output(['git','ls-files','-z'],cwd=root).decode().split('\0')
for name in paths:
 if not name:continue
 path=Path(name)
 if path.suffix.lower() in {'.jks','.keystore','.p12','.pem','.key'} or path.name=='signing.json':raise SystemExit('Private key/config tracked: '+name)
 if name.endswith(('.java','.py','.sh','.yml','.yaml','.gradle','.md','.json','.properties')):
  data=(root/path).read_text(errors='ignore')
  if re.search(r'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----|gh[pousr]_[A-Za-z0-9]{30,}',data):raise SystemExit('Potential secret tracked: '+name)
print('PASS repository secret-file check')
