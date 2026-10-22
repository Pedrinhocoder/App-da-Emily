#!/usr/bin/env python3
"""Run Android tools using the cloud proxy and verified local toolchain."""
import os
from pathlib import Path
import shutil
import subprocess
import sys
from urllib.parse import urlsplit

base = Path('/workspace/toolchains')
env = os.environ.copy()
env.update(ANDROID_HOME=str(base/'android-sdk'), ANDROID_SDK_ROOT=str(base/'android-sdk'),
           ANDROID_USER_HOME=str(base/'android-user'), ANDROID_AVD_HOME=str(base/'android-user/avd'),
           ANDROID_EMULATOR_HOME=str(base/'android-user'),
           GRADLE_USER_HOME=str(base/'gradle-home'))
trust = base/'java-cacerts'
if not trust.exists():
    java = Path(shutil.which('java')).resolve()
    shutil.copyfile(java.parent.parent/'lib/security/cacerts', trust)
    cert = env.get('CODEX_PROXY_CERT') or env.get('SSL_CERT_FILE')
    if cert:
        subprocess.run(['keytool', '-importcert', '-noprompt', '-alias', 'cloud-session-ca',
                        '-file', cert, '-keystore', str(trust), '-storepass', 'changeit'], check=True)
java_home = base/'java-user-home'
java_home.mkdir(exist_ok=True)
options = [f'-Duser.home={java_home}', f'-Djavax.net.ssl.trustStore={trust}', '-Djavax.net.ssl.trustStorePassword=changeit']
for scheme in ('http', 'https'):
    proxy = env.get(scheme.upper()+'_PROXY') or env.get(scheme+'_proxy')
    if proxy:
        p = urlsplit(proxy)
        options.extend([f'-D{scheme}.proxyHost={p.hostname}', f'-D{scheme}.proxyPort={p.port or 8080}'])
options.append('-Dhttp.nonProxyHosts=localhost|127.*|[::1]')
env['JAVA_TOOL_OPTIONS'] = ' '.join([env.get('JAVA_TOOL_OPTIONS', ''), *options])
tools = {
    'sdkmanager': base/'android-sdk/cmdline-tools/12.0/bin/sdkmanager',
    'avdmanager': base/'android-sdk/cmdline-tools/12.0/bin/avdmanager',
    'gradle': base/'gradle-8.7/bin/gradle',
    'adb': base/'android-sdk/platform-tools/adb',
    'emulator': base/'android-sdk/emulator/emulator',
}
if len(sys.argv) < 2 or sys.argv[1] not in tools:
    sys.exit('Usage: android-cloud.py {'+'|'.join(tools)+'} [arguments]')
tool = tools[sys.argv[1]]
args = sys.argv[2:]
if sys.argv[1] == 'sdkmanager':
    args = ['--sdk_root='+str(base/'android-sdk'), *args]
elif sys.argv[1] == 'gradle':
    args = ['--no-daemon', '--max-workers=2', *args]
result = subprocess.run([str(tool), *args], env=env,
                        cwd=(Path(__file__).resolve().parent.parent/'android'
                             if sys.argv[1] == 'gradle' else None))
sys.exit(result.returncode)
