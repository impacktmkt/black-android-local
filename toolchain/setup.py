import urllib.request, pathlib, concurrent.futures, zipfile, hashlib, subprocess, xml.etree.ElementTree as ET
def download(url,p):
    head=subprocess.check_output(["curl.exe","-fILsS","--connect-timeout","15","--max-time","40",url]).decode()
    import re
    lengths=re.findall(r"[Cc]ontent-[Ll]ength: (\d+)",head)
    size=int(lengths[-1]); chunk=1<<20
    pieces=p.parent/(p.name+'.parts');pieces.mkdir(exist_ok=True)
    def fetch(start):
        end=min(size-1,start+chunk-1);out=pieces/str(start)
        if not out.exists() or out.stat().st_size!=end-start+1:
            subprocess.run(["curl.exe","-fLsS","--connect-timeout","15","--max-time","40","--retry","2","--range",f"{start}-{end}","-o",str(out),url],check=True)
        assert out.stat().st_size==end-start+1,(out,out.stat().st_size,end-start+1)
        if (start//chunk)%16==0 or end+1==size: print(p.name+': '+str(end+1)+'/'+str(size),flush=True)
        return out
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as ex:
        files=list(ex.map(fetch,range(0,size,chunk)))
    with p.open('wb') as out:
        for f in files:out.write(f.read_bytes())
root=pathlib.Path('black/toolchain').resolve()
def jdk():
    url='https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse'
    p=root/'jdk.zip'
    print('Downloading JDK 17',flush=True)
    download(url,p)
    with zipfile.ZipFile(p) as z:z.extractall(root/'java')
    print('JDK ready',flush=True)
def sdk():
    base='https://dl.google.com/android/repository/'
    data=urllib.request.urlopen(base+'repository2-1.xml').read()
    (root/'repository.xml').write_bytes(data)
    doc=ET.fromstring(data)
    packages={e.attrib['path']:e for e in doc.findall('remotePackage')}
    for name,dest in [('platforms;android-34',root/'sdk/platforms/android-34'),('build-tools;35.0.0',root/'sdk/build-tools/35.0.0'),('platform-tools',root/'sdk/platform-tools')]:
        e=packages[name]
        a=next(a for a in e.findall('archives/archive') if a.findtext('host-os') in (None,'windows'))
        url=a.findtext('complete/url'); checksum=a.findtext('complete/checksum')
        p=root/(name.replace(';','-')+'.zip')
        print('Downloading '+name,flush=True)
        download(base+url,p)
        assert hashlib.sha1(p.read_bytes()).hexdigest()==checksum
        with zipfile.ZipFile(p) as z:
            prefix=z.namelist()[0].split('/')[0]+'/'
            dest.mkdir(parents=True,exist_ok=True)
            for i in z.infolist():
                rel=i.filename[len(prefix):]
                if not rel:continue
                out=dest/rel
                if i.is_dir():out.mkdir(parents=True,exist_ok=True)
                else:
                    out.parent.mkdir(parents=True,exist_ok=True);out.write_bytes(z.read(i))
        print('Ready '+name,flush=True)
with concurrent.futures.ThreadPoolExecutor() as ex:
    list(ex.map(lambda f:f(),[jdk,sdk]))
