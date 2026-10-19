#!/usr/bin/env python3
"""Validate emulator-only 440 Hz left / 880 Hz right MP4 recordings."""
import array
import json
import math
from pathlib import Path
import subprocess
import sys

files = sorted(Path(sys.argv[1]).rglob('*.mp4'))
assert len(files) >= 3, f'Expected at least three saved takes, got {len(files)}'
reports = []
for path in files:
    metadata = json.loads(subprocess.check_output([
        'ffprobe', '-v', 'error', '-show_streams', '-show_format', '-of', 'json', str(path)]))
    video = next(s for s in metadata['streams'] if s['codec_type'] == 'video')
    audio = next(s for s in metadata['streams'] if s['codec_type'] == 'audio')
    assert video['codec_name'] in ('h264', 'hevc'), f'{path}: unexpected codec {video["codec_name"]}'
    assert audio['codec_name'] == 'aac' and audio['channels'] == 2
    assert int(audio['sample_rate']) == 48000
    assert float(audio['duration']) >= 3 and float(video['duration']) >= 3
    raw = subprocess.check_output(['ffmpeg', '-v', 'error', '-i', str(path),
        '-map', '0:a:0', '-f', 's16le', '-acodec', 'pcm_s16le', '-'])
    samples = array.array('h', raw)
    if sys.byteorder != 'little':
        samples.byteswap()
    channels = [samples[side::2] for side in (0, 1)]
    ratios = []
    for side, channel in enumerate(channels):
        clip = channel[24000:72000]
        assert len(clip) == 48000
        def power(freq):
            real = sum(x * math.cos(2*math.pi*freq*n/48000) for n, x in enumerate(clip))
            imag = sum(x * math.sin(2*math.pi*freq*n/48000) for n, x in enumerate(clip))
            return real*real + imag*imag
        expected, other = (440, 880) if side == 0 else (880, 440)
        ratio = power(expected) / max(power(other), 1)
        assert ratio > 100, f'{path}: incorrect channel {side} tones ({ratio})'
        ratios.append(round(ratio, 1))
        for offset in range(24000, len(channel)-24000-4800, 4800):
            window = channel[offset:offset+4800]
            rms = math.sqrt(sum(x*x for x in window)/len(window))
            assert rms > 500, f'{path}: silent 100 ms interval on channel {side}'
    packets = json.loads(subprocess.check_output(['ffprobe', '-v', 'error', '-show_packets',
        '-show_entries', 'packet=stream_index,pts_time,dts_time', '-of', 'json', str(path)]))['packets']
    for stream in (video, audio):
        stream_packets = [p for p in packets if p['stream_index'] == stream['index']]
        pts = [float(p['pts_time']) for p in stream_packets if 'pts_time' in p]
        dts = [float(p['dts_time']) for p in stream_packets if 'dts_time' in p]
        timeline = [float(p['dts_time'] if 'dts_time' in p else p['pts_time'])
                    for p in stream_packets if 'dts_time' in p or 'pts_time' in p]
        assert pts and all(t >= 0 for t in pts + dts), 'Negative track timestamps'
        assert all(b >= a for a, b in zip(timeline, timeline[1:])), 'Nonmonotonic track timestamps'
    report = {'file': path.name, 'video': f"{video['width']}x{video['height']}",
              'duration': metadata['format']['duration'], 'sample_rate': 48000,
              'channels': 2, 'tone_power_ratios': ratios,
              'codec': video['codec_name'],
              'profile': video.get('profile'),
              'pix_fmt': video.get('pix_fmt'),
              'color_transfer': video.get('color_transfer'),
              'hdr': video.get('color_transfer') == 'arib-std-b67',
              'fps': video.get('avg_frame_rate')}
    reports.append(report)
    print(json.dumps(report), flush=True)
(Path(sys.argv[1]).parent / 'media-verification.json').write_text(json.dumps(reports, indent=2))
print('PASS: playable H.264/stereo AAC, distinct channel tones, monotonic timestamps, no silent 100 ms windows.')
