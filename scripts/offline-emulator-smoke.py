#!/usr/bin/env python3
"""Exercise real Camera2/MediaCodec with DEBUG-only tones on an emulator.

Build/install app-debug.apk first. Usage: python3 scripts/offline-emulator-smoke.py
--serial emulator-5554 --output /private/tmp/stereo-offline-smoke
No physical device is touched. Physical Bluetooth validation remains separate.
"""
import argparse
import os
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser()
parser.add_argument('--serial', required=True)
parser.add_argument('--output', required=True)
parser.add_argument('--adb', default=os.path.expanduser('~/Library/Android/sdk/platform-tools/adb'))
args = parser.parse_args()
if not args.serial.startswith('emulator-'):
    parser.error('This synthetic harness is restricted to an emulator serial.')
out = Path(args.output)
out.mkdir(parents=True, exist_ok=True)
pkg = 'app.stereobridge.debug'
component = pkg + '/app.stereobridge.MainActivity'


def adb(*parts, check=True):
    result = subprocess.run([args.adb, '-s', args.serial, *parts], text=True,
                            stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=30)
    if check and result.returncode:
        raise RuntimeError(result.stdout)
    return result.stdout


def nodes():
    adb('shell', 'rm', '-f', '/data/local/tmp/stereo-ui.xml')
    output = adb('shell', 'uiautomator', 'dump', '/data/local/tmp/stereo-ui.xml')
    data = adb('shell', 'cat', '/data/local/tmp/stereo-ui.xml', check=False)
    if not data.lstrip().startswith('<?xml'):
        (out / 'ui-dump-error.txt').write_text(output + data)
        return []
    return list(ET.fromstring(data).iter('node'))


def wait_text(text, timeout=25):
    end = time.monotonic() + timeout
    while time.monotonic() < end:
        tree = nodes()
        if any(n.get('text') == text for n in tree):
            return tree
        time.sleep(.2)
    raise RuntimeError('UI did not reach ' + repr(text) + ': ' +
                       repr([n.get('text') for n in tree if n.get('text')]))


def tap(text):
    tree = wait_text(text)
    node = next(n for n in tree if n.get('text') == text and n.get('clickable') == 'true')
    if node.get('enabled') != 'true':
        raise RuntimeError('Button disabled: ' + text)
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', node.get('bounds')))
    adb('shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))


def launch():
    adb('shell', 'am', 'start', '-n', component, '--ez', 'synthetic', 'true')
    wait_text('Record')


def screenshot(name):
    adb('shell', 'screencap', '-p', '/sdcard/stereo-screen.png')
    adb('pull', '/sdcard/stereo-screen.png', str(out / (name + '.png')))


def take(name, background=False):
    print('Recording ' + name, flush=True)
    tap('Record')
    wait_text('Recording stereo video')
    if background:
        adb('shell', 'input', 'keyevent', 'KEYCODE_HOME')
        time.sleep(2)
        adb('shell', 'input', 'keyevent', 'KEYCODE_SLEEP')
        time.sleep(3)
        adb('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP')
        adb('shell', 'wm', 'dismiss-keyguard')
        adb('shell', 'am', 'start', '-n', component, '--ez', 'synthetic', 'true')
        wait_text('Recording stereo video')
    else:
        time.sleep(5)
    tap('Stop')
    wait_text('Video saved to Movies / Stereo Bridge')
    screenshot(name)
    print('Saved ' + name, flush=True)

def run():
    adb('shell', 'svc', 'wifi', 'disable')
    adb('shell', 'settings', 'put', 'global', 'mobile_data', '0')
    for permission in ['CAMERA', 'RECORD_AUDIO', 'BLUETOOTH_CONNECT', 'POST_NOTIFICATIONS']:
        adb('shell', 'pm', 'grant', pkg, 'android.permission.' + permission, check=False)
    adb('logcat', '-c')
    launch()
    screenshot('ready')
    take('first')
    take('repeat-background', background=True)
    print('Rebooting emulator; no pairing/debug setup in app', flush=True)
    adb('reboot')
    end = time.monotonic() + 180
    while time.monotonic() < end:
        if adb('shell', 'getprop', 'sys.boot_completed', check=False).strip() == '1':
            break
        time.sleep(2)
    else:
        raise RuntimeError('Emulator reboot timeout')
    adb('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP')
    adb('shell', 'wm', 'dismiss-keyguard')
    end = time.monotonic() + 90
    while time.monotonic() < end:
        ready = adb('shell', 'sh', '-c',
                    '"echo ready > /sdcard/stereo-storage-ready && '
                    'rm /sdcard/stereo-storage-ready && echo STORAGE_READY"', check=False)
        if 'STORAGE_READY' in ready:
            break
        time.sleep(2)
    else:
        raise RuntimeError('Shared storage unavailable after reboot/unlock: ' + ready)
    launch()
    dimensions=list(map(int,re.findall(r'\d+',adb('shell','wm','size'))[-2:]))
    width,height=dimensions
    adb('shell','input','swipe',str(width//2),str(int(height*.65)),str(width//2),str(int(height*.3)),'350')
    last_tree=wait_text('Open last video')
    assert any(n.get('text')=='Open last video' and n.get('enabled')=='true' for n in last_tree), 'Last saved URI not restored'
    assert adb('shell','settings','get','global','wifi_on').strip()=='0', 'Wi-Fi enabled after reboot'
    screenshot('after-reboot')
    take('after-reboot-take')
    (out / 'logcat.txt').write_text(adb('logcat', '-d'))
    print('PASS: three saved takes, repeated session, background/screen sleep, reboot, Wi-Fi off. '
          'Synthetic audio only; Bluetooth and Pixel still unverified.', flush=True)

try:
    run()
finally:
    try:
        (out / 'logcat.txt').write_text(adb('logcat', '-d'))
        adb('pull', '/sdcard/Movies/Stereo Bridge', str(out / 'videos'), check=False)
        screenshot('final-state')
    except Exception as error:
        print('Could not capture final diagnostic state:', error, flush=True)
