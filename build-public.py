# python · build-private.py · BLACK local APK · Windows · JDK17 + Android SDK
from pathlib import Path
import subprocess, zipfile, hashlib, json, secrets, os, shutil, xml.etree.ElementTree as ET
root=Path(__file__).resolve().parent
repo=root/'Black-Android-preparador'; tools=root/'toolchain'; sdk=tools/'sdk'; bt=sdk/'build-tools/35.0.0'
jdk=next((tools/'java').glob('jdk*')); java=jdk/'bin/java.exe'; javac=jdk/'bin/javac.exe'; keytool=jdk/'bin/keytool.exe'
android=sdk/'platforms/android-34/android.jar'
work=root/'public-build'; work.mkdir(exist_ok=True)
verification=root/'verification-public'; verification.mkdir(exist_ok=True)
output=root/'BLACK-Preparador-1.5.0.apk'
if output.exists():raise RuntimeError('Refusing to overwrite the previous APK')
log=(verification/'build-and-signature.txt').open('w',encoding='utf-8')
def run(args,env=None):
    print('RUN',Path(str(args[0])).name,flush=True)
    result=subprocess.run([str(a) for a in args],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,env=env)
    text=result.stdout.decode('utf-8',errors='replace');log.write(text);log.flush()
    if result.returncode:print(text,flush=True);raise RuntimeError('Command failed: '+str(args[0]))
    return text
run([java,'-version'])
run([bt/'aapt2.exe','version'])
# Validate provenance before reusing the supplied engine libraries.
original=root/'private-input/Black-1.5.0.apk'
assert hashlib.sha256(original.read_bytes()).hexdigest()=='102340f238a97ef18a28c9a2e0e2ec47c3e7a982ab1b3845cd65305cad07d28a'
original_signature=run([java,'-jar',bt/'lib/apksigner.jar','verify','--verbose','--print-certs',original])
print('Original APK signature verified',flush=True)
with zipfile.ZipFile(original) as apk:
 for name in apk.namelist():
  if name.startswith('lib/') and name.endswith('.so'):
   target=repo/'app/src/main/jniLibs'/Path(name).relative_to('lib')
   target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(apk.read(name))
# aapt2 performs the resource/manifest stage normally invoked by AGP.
resourcezip=work/'resources.zip'
run([bt/'aapt2.exe','compile','--dir',repo/'app/src/main/res','-o',resourcezip])
ns='http://schemas.android.com/apk/res/android';ET.register_namespace('android',ns)
manifest=ET.parse(repo/'app/src/main/AndroidManifest.xml');manifest.getroot().set('package','com.racas.black')
manifestpath=work/'AndroidManifest.xml';manifest.write(manifestpath,encoding='utf-8',xml_declaration=True)
gen=work/'generated';gen.mkdir(exist_ok=True)
resourceapk=work/'resources.apk'
run([bt/'aapt2.exe','link','-o',resourceapk,'-I',android,'--manifest',manifestpath,'--java',gen,
 '--min-sdk-version','29','--target-sdk-version','34','--version-code','15003','--version-name','1.5.0-local.3',
 '--rename-manifest-package','com.local.black.downloads','--auto-add-overlay','-R',resourcezip])
classes=work/'classes';classes.mkdir(exist_ok=True)
sources=list((repo/'app/src/main/java').rglob('*.java'))+list((repo/'third_party/SDL/android-project/app/src/main/java').rglob('*.java'))+list(gen.rglob('*.java'))
sourcearg=work/'sources.txt';sourcearg.write_text('\n'.join('"'+p.as_posix()+'"' for p in sources),encoding='utf-8')
run([javac,'-encoding','UTF-8','-source','17','-target','17','-classpath',android,'-d',classes,'@'+str(sourcearg)])
jar=work/'classes.jar'
with zipfile.ZipFile(jar,'w',zipfile.ZIP_DEFLATED) as z:
 for p in classes.rglob('*.class'):z.write(p,p.relative_to(classes).as_posix())
dex=work/'dex';dex.mkdir(exist_ok=True)
run([java,'-cp',bt/'lib/d8.jar','com.android.tools.r8.D8','--release','--min-api','29','--lib',android,'--output',dex,jar])
unsigned=work/'unsigned.apk'
shutil.copyfile(resourceapk,unsigned)
with zipfile.ZipFile(unsigned,'a') as z:
 for p in dex.glob('*.dex'):z.write(p,p.name,compress_type=zipfile.ZIP_DEFLATED)
 for p in (repo/'app/src/main/jniLibs').rglob('*.so'):
  z.write(p,'lib/'+p.relative_to(repo/'app/src/main/jniLibs').as_posix(),compress_type=zipfile.ZIP_STORED)
aligned=work/'aligned.apk'
run([bt/'zipalign.exe','-P','16','-f','4',unsigned,aligned])
# A private key stored outside the deliverable ZIP keeps future local upgrades possible.
signing=root/'private-signing';signing.mkdir(exist_ok=True)
passwordfile=signing/'credentials.json'
if not passwordfile.exists():passwordfile.write_text(json.dumps({'password':secrets.token_urlsafe(36)}),encoding='utf-8')
env=dict(os.environ);env['BLACK_LOCAL_PASSWORD']=json.loads(passwordfile.read_text())['password']
keystore=signing/'black-local.p12'
if not keystore.exists():
 run([keytool,'-genkeypair','-keystore',keystore,'-storetype','PKCS12','-storepass:env','BLACK_LOCAL_PASSWORD',
  '-alias','black-local','-keyalg','RSA','-keysize','3072','-validity','10000','-dname','CN=BLACK Local Private,O=Local,C=BR'],env)
run([java,'-jar',bt/'lib/apksigner.jar','sign','--ks',keystore,'--ks-pass','env:BLACK_LOCAL_PASSWORD','--ks-key-alias','black-local',
 '--v1-signing-enabled','false','--v2-signing-enabled','true','--v3-signing-enabled','true','--v4-signing-enabled','false','--out',output,aligned],env)
localsig=run([java,'-jar',bt/'lib/apksigner.jar','verify','--verbose','--print-certs',output])
import re
cert=lambda t:re.search(r'Signer #1 certificate SHA-256 digest: ([0-9a-f]+)',t).group(1)
assert cert(original_signature)!=cert(localsig),'Local key unexpectedly matches official key'
assert 'Verified using v3 scheme (APK Signature Scheme v3): true' in localsig
run([bt/'zipalign.exe','-c','-P','16','4',output])
badging=run([bt/'aapt2.exe','dump','badging',output]);(verification/'apk-badging.txt').write_text(badging,encoding='utf-8')
assert "package: name='com.local.black.downloads'" in badging
assert "application-label:'BLACK'" in badging
assert "launchable-activity: name='com.racas.black.PreparationActivity'" in badging
assert 'WRITE_EXTERNAL_STORAGE' not in badging
assert 'MANAGE_EXTERNAL_STORAGE' not in badging
assert 'READ_EXTERNAL_STORAGE' not in badging
assert 'versionCode=\'15003\'' in badging
with zipfile.ZipFile(original) as old,zipfile.ZipFile(output) as new:
 libs=[n for n in old.namelist() if n.startswith('lib/') and n.endswith('.so')]
 for n in libs:assert old.read(n)==new.read(n),'Changed native library: '+n
 old_icons={old.read(n) for n in old.namelist() if n.startswith('res/') and n.endswith('.png')}
 new_icons={new.read(n) for n in new.namelist() if n.startswith('res/') and n.endswith('.png')}
 assert old_icons==new_icons,'Launcher raster assets differ from official APK'
 assert not any(n.lower().endswith('.iso') for n in new.namelist())
 assert sum(i.file_size for i in new.infolist())<200_000_000
print('PASS: source compilation, DEX, package, signature, 16 KB alignment, unchanged native libraries and separate ISO',flush=True)
# Test the exact importer validation method on the host JVM, including the supplied ISO.
harness=root/'verification/ImportValidatorCheck.java';testclasses=work/'test-classes';testclasses.mkdir(exist_ok=True)
run([javac,'-encoding','UTF-8','-classpath',str(android)+os.pathsep+str(classes),'-d',testclasses,harness])
check=run([java,'-cp',os.pathsep.join(map(str,[testclasses,classes,android])),'com.racas.black.ImportValidatorCheck',root/'private-input/BLACK.iso'])
print(check,flush=True)
downloadcheck=root/'verification/DownloadCheck.java'
run([javac,'-encoding','UTF-8','-classpath',str(android)+os.pathsep+str(classes),'-d',testclasses,downloadcheck])
check=run([java,'-cp',os.pathsep.join(map(str,[testclasses,classes,android])),'com.racas.black.DownloadCheck'])
print(check,flush=True)
assert not (repo/'app/src/main/java/com/racas/black/UpdateChecker.java').exists()
# No installs or device changes: only list any connected Android devices.
adb=sdk/'platform-tools/adb.exe'
if adb.exists():
 devices=run([adb,'devices','-l']);print(devices,flush=True)
(verification/'local-apk-sha256.txt').write_text(hashlib.sha256(output.read_bytes()).hexdigest()+'  '+output.name+'\n',encoding='utf-8')
log.close()
print('COMPILED AND SIGNED:',output,flush=True)
