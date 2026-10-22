#!/usr/bin/env bash
set -euo pipefail
cd /workspace/App-da-Emily
mkdir -p /home/agent/.android
python3 - <<'PY'
import hashlib
from pathlib import Path
import shutil
import urllib.request
import zipfile
base = Path('/workspace/toolchains')
downloads = base/'downloads'
downloads.mkdir(parents=True, exist_ok=True)
items = [
 ('https://services.gradle.org/distributions/gradle-8.7-bin.zip', 'sha256',
  '544c35d6bd849ae8a5ed0bcea39ba677dc40f49df7d1835561582da2009b961d', base/'gradle-8.7/bin/gradle', base),
 ('https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip', 'sha1',
  'd313adb7aedccf6cf0cfca51ec180f0059f5f8f8', base/'android-sdk/cmdline-tools/12.0/bin/sdkmanager',
  base/'android-sdk/cmdline-tools/extract-12.0')]
for url, algorithm, expected, executable, target in items:
    if executable.is_file():
        continue
    archive = downloads/url.rsplit('/', 1)[-1]
    if not archive.is_file():
        with urllib.request.urlopen(url, timeout=120) as response, archive.open('wb') as output:
            shutil.copyfileobj(response, output)
    with archive.open('rb') as stream:
        actual = hashlib.file_digest(stream, algorithm).hexdigest()
    if actual != expected:
        raise SystemExit(f'Checksum inválido: {archive.name}; não foi extraído.')
    with zipfile.ZipFile(archive) as z:
        z.extractall(target)
    if 'commandlinetools' in archive.name:
        (target/'cmdline-tools').rename(target.parent/'12.0')
        target.rmdir()
    for f in executable.parent.iterdir():
        if f.is_file():
            f.chmod(f.stat().st_mode | 0o111)
PY
python3 - <<'PY'
import subprocess
# Supply the SDK license answers without a shell pipeline hiding the tool exit code.
subprocess.run(['python3', 'scripts/android-cloud.py', 'sdkmanager', '--licenses'],
               input='y\n'*100, text=True, check=True)
PY
python3 scripts/android-cloud.py sdkmanager 'platforms;android-35' 'build-tools;34.0.0' 'platform-tools' 'emulator' 'system-images;android-35;aosp_atd;x86_64'
python3 - <<'PY'
from pathlib import Path
import subprocess
if not Path('/workspace/toolchains/android-user/avd/AmorCuida35ATD.ini').exists():
    subprocess.run(['python3', 'scripts/android-cloud.py', 'avdmanager', 'create', 'avd',
                    '--name', 'AmorCuida35ATD', '--package', 'system-images;android-35;aosp_atd;x86_64',
                    '--device', 'pixel_2'], input='no\n', text=True, check=True)
    # A smaller display keeps software rendering practical in the cloud.
    config = Path('/workspace/toolchains/android-user/avd/AmorCuida35ATD.avd/config.ini')
    settings = dict(line.split('=', 1) for line in config.read_text().splitlines() if '=' in line)
    settings.update({'hw.lcd.width': '540', 'hw.lcd.height': '960', 'hw.lcd.density': '240'})
    config.write_text(''.join(f'{key}={value}\n' for key, value in settings.items()))
PY
python3 scripts/android-cloud.py gradle :app:assembleDebug
