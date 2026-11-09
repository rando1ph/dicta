"""One-shot RSA-OAEP ciphertext provisioning. Only the test APK accepts the payload.
No key bytes in command arguments, output, clipboard, or plaintext files on the device.
"""
import sys, time, subprocess
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'m1'))
import device
from cryptography.hazmat.primitives import serialization, hashes
from cryptography.hazmat.primitives.asymmetric import padding
package='dev.dicta.prototype'
process=subprocess.Popen(['adb','-s',device.SERIAL,'shell','am','instrument','-w','-e','class',package+'.SecureProvisionTest','-e','dictaProvision','true',package+'.test/androidx.test.runner.AndroidJUnitRunner'],stdout=subprocess.PIPE,stderr=subprocess.PIPE)
try:
    for _ in range(100):
        try:
            public=device.adb('exec-out','run-as',package,'cat','files/m2-provision.pub',binary=True)
            parsed=serialization.load_der_public_key(public)
            break
        except (RuntimeError,ValueError): pass
        time.sleep(.2)
    else: raise RuntimeError('Provisioning public key unavailable')
    key=(Path.home()/'.config/dicta/deepseek_api_key').read_bytes().strip()
    encrypted=parsed.encrypt(key,padding.OAEP(mgf=padding.MGF1(hashes.SHA256()),algorithm=hashes.SHA256(),label=None))
    subprocess.run(['adb','-s',device.SERIAL,'shell','run-as',package,'sh','-c',"'cat > files/m2-provision.enc'"],input=encrypted,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,check=True,timeout=15)
    output,_=process.communicate(timeout=65)
    if process.returncode or b'OK (1 test)' not in output: raise RuntimeError('Provisioning test failed; no credential data is printed')
    print('PASS: ephemeral RSA-OAEP provisioning; Android Keystore protected credential saved.')
finally:
    if process.poll() is None: process.terminate()
    device.shell('run-as',package,'rm','-f','files/m2-provision.pub','files/m2-provision.enc')
