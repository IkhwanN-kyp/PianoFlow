#!/usr/bin/env python3
"""Validate a signed release and generate the updater's public metadata. Never reads signing secrets."""
from pathlib import Path
import hashlib,json,os,re,shutil,subprocess,sys
root=Path(__file__).resolve().parents[1];version=sys.argv[1].removeprefix('v');code=int(subprocess.check_output([sys.executable,str(root/'scripts/version_code.py'),version],text=True));out=root/'dist';out.mkdir(exist_ok=True)
sdk=Path(os.environ.get('ANDROID_HOME',os.environ.get('ANDROID_SDK_ROOT','')));tools=sdk/'build-tools/35.0.0'
apk=root/'app/build/outputs/apk/release/app-release.apk'
report=subprocess.check_output([str(tools/'apksigner'),'verify','--verbose','--print-certs',str(apk)],text=True)
cert=re.search(r'Signer #1 certificate SHA-256 digest: ([0-9a-f]+)',report).group(1)
expected='eda0f798078f5eb48b7237301a2e4261544639d333625ab6c4b265f55862636d'
if cert!=expected:raise SystemExit('Wrong signing identity')
badging=subprocess.check_output([str(tools/'aapt'),'dump','badging',str(apk)],text=True)
if "name='id.arus.piano'" not in badging or f"versionCode='{code}'" not in badging or f"versionName='{version}'" not in badging:raise SystemExit('Release package/version mismatch')
if 'application-debuggable' in badging:raise SystemExit('Refusing to publish a debuggable release')
name=f'PianoFlow-{version}-arm64.apk';dest=out/name;shutil.copyfile(apk,dest);sha=hashlib.sha256(dest.read_bytes()).hexdigest()
manifest={'schema':1,'packageId':'id.arus.piano','versionName':version,'versionCode':code,'apk':name,'size':dest.stat().st_size,'sha256':sha,'signerSha256':cert,'minSdk':24,'abi':'arm64-v8a'}
(out/'update.json').write_text(json.dumps(manifest,indent=2)+'\n');(out/(name+'.sha256')).write_text(sha+'  '+name+'\n');(out/'signature-verification.txt').write_text(report)
shutil.copyfile(root/'modules/basic-pitch.tflite',out/'basic-pitch.tflite');shutil.copyfile(root/'app/src/main/assets/licenses/BasicPitch-Apache-2.0.txt',out/'basic-pitch-LICENSE.txt')
with open(out/f'PianoFlow-{version}-source.zip','wb') as f:subprocess.run(['git','archive','--format=zip','--prefix=PianoFlow/','HEAD'],cwd=root,stdout=f,check=True)
import zipfile
with zipfile.ZipFile(out/f'PianoFlow-{version}-native-sources.zip','w',zipfile.ZIP_DEFLATED) as z:
 for path in (root/'third_party/sources').iterdir():
  if path.is_file():z.write(path,Path('native-sources')/path.name)
 for path in (root/'app/src/main/assets/licenses').iterdir():z.write(path,Path('licenses')/path.name)
print('Signed release and updater metadata prepared:',version,'code',code,'sha256',sha)
