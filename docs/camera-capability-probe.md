# Camera capability probe

`OfflineRecorderService` logs one line per camera negotiation:

```
Camera capabilities: camera stabilization=<mode> optical=<bool> zoom=<min-max|off> flash=<bool> afRegions=<n> hdrCamera=<bool> hdrEncoder=<bool> mode=<label>
```

Capture it with:

```bash
adb logcat -c
# open Stereo Bridge once
adb logcat -d | grep "Camera capabilities" | tail -1
```

## Recorded devices

| Device | Date | Probe line |
| --- | --- | --- |
| Pixel 8 Pro | pending first physical run (Task 13) | |
| Pixel 6 | pending device availability | |
| Non-Pixel Android 10+ | pending hardware availability | |
