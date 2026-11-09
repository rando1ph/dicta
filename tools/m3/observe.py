"""Guarded observations for this M3 run; local DB copies never leave artifacts/."""
import sys,json,hashlib,io,tarfile,sqlite3,time
import xml.etree.ElementTree as ET
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools/m1'))
import device,smoke
ART=ROOT/'artifacts/m3'
ART.mkdir(exist_ok=True)
def wait_text(text,timeout=15):
    deadline=time.monotonic()+timeout
    while time.monotonic()<deadline:
        try:
            if smoke.visible(text):return
        except (RuntimeError, ET.ParseError):
            pass # The Activity may not have produced a UI hierarchy immediately after launch.
        time.sleep(.3)
    raise AssertionError('UI timeout: '+text)

def capture(name):
    foreground=smoke.foreground()
    assert foreground.startswith(('dev.dicta.prototype/','com.android.chrome/'))
    if foreground.startswith('dev.dicta.prototype/'):
        assert not smoke.visible('API Key'),'Never capture BYOK Settings'
    else:assert smoke.visible('127.0.0.1:8766'),'Only the controlled Chrome page may be captured'
    p=ART/(name+'.png');p.write_bytes(device.adb('exec-out','screencap','-p',binary=True))
    print(p)
    return p

def state(label):
    directory=ART/label;directory.mkdir(exist_ok=True)
    raw=device.adb('exec-out','run-as','dev.dicta.prototype','tar','-cf','-','databases',binary=True)
    with tarfile.open(fileobj=io.BytesIO(raw)) as archive:
        for member in archive.getmembers():
            if member.isfile() and member.name.startswith('databases/') and '..' not in Path(member.name).parts:
                path=directory/Path(member.name).name
                path.write_bytes(archive.extractfile(member).read())
    db=sqlite3.connect(directory/'dicta.db')
    result={'counts':{table:db.execute('SELECT COUNT(*) FROM '+table).fetchone()[0] for table in ['history','bookmarks','daily','profiles']},
        'daily':db.execute('SELECT slot,entryId,refreshCounter FROM daily ORDER BY slot').fetchall(),
        'activeProfile':db.execute("SELECT value FROM preferences WHERE name='activeProfile'").fetchone(),
        'testHistory':db.execute("SELECT history.id,entryId,source FROM history JOIN entries ON entryId=entries.id WHERE entries.title IN ('luminescence','petrichor') ORDER BY history.id").fetchall(),
        'testBookmarks':db.execute("SELECT entryId FROM bookmarks JOIN entries ON entryId=entries.id WHERE entries.title IN ('luminescence','petrichor') ORDER BY entryId").fetchall()}
    db.close()
    cipher=device.adb('exec-out','run-as','dev.dicta.prototype','cat','no_backup/provider.enc',binary=True)
    result['credentialCiphertextSha256']=hashlib.sha256(cipher).hexdigest()
    (ART/(label+'.json')).write_text(json.dumps(result,indent=2)+'\n')
    print(json.dumps({'counts':result['counts'],'testHistoryCount':len(result['testHistory']),'testBookmarkCount':len(result['testBookmarks'])}))
    return result

if __name__=='__main__':
    if sys.argv[1]=='capture':capture(sys.argv[2])
    elif sys.argv[1]=='state':state(sys.argv[2])
    elif sys.argv[1]=='texts':print(json.dumps(smoke.texts(),ensure_ascii=False,indent=2))
