#!/usr/bin/env python3
"""Steer the opt-in Compose review fixture; every click goes through the actual UI."""
import argparse
import shlex
import subprocess
import time

parser = argparse.ArgumentParser()
parser.add_argument("action", choices=["home", "daily", "query", "history", "history_detail", "vocabulary", "vocabulary_detail", "settings", "models", "actions", "light", "dark"])
parser.add_argument("--serial", required=True)
args = parser.parse_args()
if not args.serial.startswith("emulator-"):
    parser.error("Only an explicitly named emulator is allowed")

def adb(command):
    return subprocess.run(["adb", "-s", args.serial, "shell", command], capture_output=True, text=True, check=True).stdout.strip()

if adb("getprop ro.kernel.qemu") != "1":
    parser.error("The named device is not an emulator")
adb("run-as dev.randolf.dicta rm -f files/t07-review-done")
adb("run-as dev.randolf.dicta sh -c " + shlex.quote("printf %s " + args.action + " > files/t07-review-action"))
for _ in range(100):
    result = subprocess.run(["adb", "-s", args.serial, "shell", "run-as dev.randolf.dicta cat files/t07-review-done"], capture_output=True, text=True)
    if result.returncode == 0:
        if result.stdout.strip() != args.action:
            raise SystemExit(result.stdout.strip())
        print("UI action completed:", args.action)
        break
    time.sleep(.2)
else:
    raise SystemExit("Review fixture did not complete the UI action")
