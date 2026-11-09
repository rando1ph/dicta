"""Real-device UI helpers. Never substitutes explicit intents for Chrome menu selection."""
import json,re,time,xml.etree.ElementTree as ET
from pathlib import Path
import device
EVIDENCE=device.ROOT/'docs/m1/evidence/device-tests.json'
checks=[]
def record(case, **details):
    checks.append(dict(case=case,**details));EVIDENCE.write_text(json.dumps(checks,ensure_ascii=False,indent=2)+'\n')
def nodes():return list(ET.fromstring(device.dump()).iter('node'))
def find(text=None,desc=None):
    for n in nodes():
        if (text is None or n.get('text')==text) and (desc is None or n.get('content-desc')==desc):return n
    raise AssertionError(f'UI node not found: {text or desc}')
def tap_node(n):
    a=list(map(int,re.findall(r'\d+',n.get('bounds'))));device.shell('input','tap',(a[0]+a[2])//2,(a[1]+a[3])//2)
def tap(text=None,desc=None):tap_node(find(text,desc))
def visible(text):return any(text in n.get('text','') or text in n.get('content-desc','') for n in nodes())
def wait_text(text,timeout=15):
    end=time.monotonic()+timeout
    while time.monotonic()<end:
        if visible(text):return
        time.sleep(.3)
    raise AssertionError('UI timeout: '+text)
def texts():return [(n.get('text') or n.get('content-desc'),n.get('bounds')) for n in nodes() if n.get('text') or n.get('content-desc')]
def explicit(query,readonly=True,action='android.intent.action.PROCESS_TEXT',mime='text/plain'):
    # shell quoting needed because adb joins the argument list into a remote shell command.
    import shlex
    return device.shell('am','start','-a',action,'-t',mime,'--es','android.intent.extra.PROCESS_TEXT',shlex.quote(query),'--ez','android.intent.extra.PROCESS_TEXT_READONLY',str(readonly).lower(),'-n','dev.dicta.prototype/.platform.ProcessTextActivity')
def foreground():
    value=device.shell('dumpsys','activity','activities')
    # Never persist full stack: it can include unrelated activities.
    match=re.search(r'topResumedActivity=ActivityRecord\{[^ ]+ u\d+ ([^ ]+)',value)
    return match.group(1) if match else 'unknown'
if __name__=='__main__':print(json.dumps(texts(),ensure_ascii=False,indent=2))
