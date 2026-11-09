"""Identity guarded in-place upgrade. Never uninstall, clear data, or provision a key."""
import sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools/m1'))
import device
for p in ['app/build/outputs/apk/debug/app-debug.apk','app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk']:
    result=device.adb('install','--no-streaming','-r','-t',ROOT/p)
    assert 'Success' in result
print('In-place APK upgrades installed on verified OnePlus A6013 / fajita; existing data retained.')
