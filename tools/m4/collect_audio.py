"""Collect only our TTS fixtures, validate PCM, and create a UK-then-US listening sample."""
import sys,json,wave,array,math,hashlib
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools/m1'))
import device
art=ROOT/'artifacts/m4';evidence=ROOT/'docs/m4/evidence'
evidence.joinpath('tts-device.json').write_bytes(device.adb('exec-out','run-as','dev.dicta.prototype','cat','files/m4-tts/report.json',binary=True))
rows=[];pcm=[];formats=[]
for country in ('GB','US'):
    path=art/('TTS-'+country+'.wav')
    path.write_bytes(device.adb('exec-out','run-as','dev.dicta.prototype','cat','files/m4-tts/'+country+'.wav',binary=True))
    with wave.open(str(path),'rb') as audio:
        params=audio.getparams();data=audio.readframes(audio.getnframes())
        assert audio.getsampwidth()==2 and audio.getnchannels()==1
        samples=array.array('h',data)
        peak=max(abs(x) for x in samples);rms=math.sqrt(sum(x*x for x in samples)/len(samples));seconds=audio.getnframes()/audio.getframerate()
        assert peak>500 and rms>100 and 1<seconds<15
        rows.append({'country':country,'bytes':path.stat().st_size,'sampleRate':audio.getframerate(),'channels':audio.getnchannels(),'durationSeconds':round(seconds,3),'peak16bit':peak,'rms16bit':round(rms,2),'nonZeroFraction':round(sum(x!=0 for x in samples)/len(samples),4),'sha256':hashlib.sha256(path.read_bytes()).hexdigest()})
        pcm.append(data);formats.append(params[:3])
assert formats[0]==formats[1]
with wave.open(str(art/'Dicta-UK-then-US.wav'),'wb') as output:
    output.setparams(params);output.writeframes(pcm[0]+b'\0'*(params.framerate*params.nchannels*params.sampwidth//2)+pcm[1])
evidence.joinpath('audio-analysis.json').write_text(json.dumps(rows,indent=2)+'\n');print(json.dumps(rows,indent=2))
