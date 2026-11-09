"""Exact credential scan, plus token-shape scan on M0 text. Never prints secret values."""
import io
import re
import subprocess
import zipfile
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
key=(Path.home()/'.config/dicta/deepseek_api_key').read_bytes().strip()
if not key:
    raise SystemExit('FAIL: external credential is empty')
paths=subprocess.check_output(['git','ls-files','--cached','--others','--exclude-standard','-z'],cwd=ROOT).split(b'\0')
failures=[]
checked=0
for raw in paths:
    if not raw: continue
    path=ROOT/raw.decode()
    if not path.is_file(): continue
    data=path.read_bytes();checked+=1
    if key in data: failures.append(str(path.relative_to(ROOT)))
    if path.suffix=='.zip':
        with zipfile.ZipFile(io.BytesIO(data)) as archive:
            for name in archive.namelist():
                if key in archive.read(name): failures.append(str(path.relative_to(ROOT))+':archive-member')
    # The unchanged Stitch export contains a clearly fictional mock key. New M0 files must not.
    if not str(path.relative_to(ROOT)).startswith('design/'):
        if re.search(rb'\bsk-[A-Za-z0-9_-]{16,}',data): failures.append(str(path.relative_to(ROOT))+':token-shape')
# Include all existing historical blobs; output only hashes/count, never their contents.
objects=subprocess.check_output(['git','rev-list','--objects','--all'],cwd=ROOT).splitlines()
blobs=0
for row in objects:
    sha=row.split(b' ')[0].decode()
    kind=subprocess.check_output(['git','cat-file','-t',sha],cwd=ROOT).strip()
    if kind==b'blob':
        blobs+=1
        if key in subprocess.check_output(['git','cat-file','blob',sha],cwd=ROOT): failures.append('historical-blob:'+sha)
if failures:
    raise SystemExit('FAIL: sensitive content found in '+', '.join(failures))
print(f'PASS: {checked} repository files (including ZIP members), {blobs} historical blobs; exact key absent; no token-shaped key in M0 files.')
