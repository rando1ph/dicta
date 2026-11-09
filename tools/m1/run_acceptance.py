"""Recorded M1 acceptance on 1080x2340 fajita; inspect host/menu before reusing coordinates."""
import sys,time,json
from pathlib import Path
import device,smoke

def note(name,**extra):
    smoke.record(name,result='pass',**extra);print(name,flush=True)
def close():smoke.tap(desc='关闭 Dicta');smoke.wait_text('A quiet reading room')
def menu(capture=False):
    assert smoke.foreground().startswith('com.android.chrome/')
    # Grounded in the local test page nodes and screenshots; not generic screen scraping.
    assert smoke.find(text='ephemeral').get('bounds')=='[75,681][1004,796]'
    device.shell('input','swipe','220','735','220','735','800');time.sleep(.5)
    device.shell('input','tap','920','590');time.sleep(.5)
    if capture:device.capture('M1-final-01-chrome-selection-menu')
    device.shell('input','tap','804','458')
    smoke.wait_text('Dicta · Popup');smoke.wait_text('短暂的；转瞬即逝的')
    assert smoke.foreground()=='dev.dicta.prototype/.platform.ProcessTextActivity'

# Begin on the already opened controlled Chrome page, no browser data resets.
smoke.wait_text('A quiet reading room')
checkpoint=smoke.find(text='ephemeral').get('bounds')
smoke.explicit('ephemeral');smoke.wait_text('短暂的；转瞬即逝的');close()
note('explicit-intent-diagnostic',separateFromChromeMenu=True)
menu(capture=True);device.capture('M1-final-02-chrome-popup');close()
assert smoke.find(text='ephemeral').get('bounds')==checkpoint
note('chrome-real-long-press-menu-popup-close',readerBoundsBefore=checkpoint,readerBoundsAfter=checkpoint)
device.capture('M1-final-03-return-to-chrome')
menu();device.shell('input','keyevent','KEYCODE_BACK');smoke.wait_text('A quiet reading room');note('chrome-popup-system-back')
menu();device.shell('input','tap','20','400');smoke.wait_text('A quiet reading room');note('chrome-popup-outside-dismiss')
for query,expected in [('luminous','发光的；明亮的'),('qzxvplmnonce','qzxvplmnonce'),('unavailableword','M1 暂无此词'),('', '最多 100'),('x'*101,'最多 100'),(':error','演示失败'),(':timeout','演示查询超时')]:
    smoke.explicit(query);smoke.wait_text(expected);close();note('popup-'+(query if len(query)<30 and query else ('empty' if not query else 'overlong')))
smoke.explicit(':slow');smoke.wait_text('正在载入演示词条');close();time.sleep(9)
assert smoke.foreground().startswith('com.android.chrome/')
note('cancel-slow-no-late-popup')
smoke.explicit('ephemeral',readonly=False);smoke.wait_text('短暂的');close()
assert smoke.visible('ephemeral remains unchanged');note('editable-source-no-replacement-explicit')
# Main UI navigation and a real editable Home lookup.
device.shell('am','start','-n','dev.dicta.prototype/.platform.MainActivity');smoke.wait_text('DICTA AI LEXICON')
smoke.tap(text='Search a word / 查词…');device.shell('input','text','ephemeral');smoke.tap(desc='查询');smoke.wait_text('短暂的；转瞬即逝的')
device.capture('M1-final-04-home-query');note('home-user-input-ephemeral-shared-renderer')
for page,expected,name in [('History','查询历史','M1-final-05-history'),('Vocabulary','生词本','M1-final-06-vocabulary'),('Settings','Fake Provider','M1-final-07-settings')]:
    smoke.tap(text=page);smoke.wait_text(expected);device.capture(name);note('navigation-'+page.lower())
print('ALL DEVICE SMOKE CHECKS PASSED',flush=True)
