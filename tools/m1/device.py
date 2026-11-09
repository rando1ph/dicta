"""Narrow M1 device helper: checks identity before any operation. No serial in evidence."""
from pathlib import Path
import subprocess, re, json, sys
ROOT=Path(__file__).resolve().parents[2]
ART=ROOT/'artifacts/m1'
def run(*args, binary=False):
    try:
        return subprocess.check_output(['adb',*map(str,args)],text=not binary,timeout=30,stderr=subprocess.PIPE)
    except (subprocess.CalledProcessError, subprocess.TimeoutExpired):
        raise RuntimeError('ADB operation failed or timed out; device identity and arguments are omitted.') from None
rows=[s.split() for s in run('devices','-l').splitlines()[1:] if s.strip()]
if len(rows)!=1 or rows[0][1]!='device': raise SystemExit('Expected one authorized device')
SERIAL=rows[0][0]
def adb(*args,binary=False):return run('-s',SERIAL,*args,binary=binary)
def shell(*args):return adb('shell',*args).strip()
identity={k:shell('getprop',k) for k in ('ro.product.manufacturer','ro.product.model','ro.lineage.device')}
if identity != {'ro.product.manufacturer':'OnePlus','ro.product.model':'ONEPLUS A6013','ro.lineage.device':'fajita'}:raise SystemExit('Device identity mismatch')
def dump():
    shell('rm','-f','/data/local/tmp/dicta-m1-window.xml')
    shell('uiautomator','dump','/data/local/tmp/dicta-m1-window.xml')
    return shell('cat','/data/local/tmp/dicta-m1-window.xml')
def capture(name):
    # Call only while our app or controlled test page is visible; review locally before uploading.
    ART.mkdir(parents=True,exist_ok=True)
    p=ART/(name+'.png');p.write_bytes(adb('exec-out','screencap','-p',binary=True));return p
if __name__=='__main__':
    cmd=sys.argv[1]
    if cmd=='dump': print(dump())
    elif cmd=='capture': print(capture(sys.argv[2]))
    elif cmd=='tap':shell('input','tap',*sys.argv[2:])
    elif cmd=='key':shell('input','keyevent',sys.argv[2])
    elif cmd=='swipe':shell('input','swipe',*sys.argv[2:])
    elif cmd=='text':shell('input','text',sys.argv[2])
    elif cmd=='shell':print(shell(*sys.argv[2:]))
