# Camera companion experiment

Local interoperability prototype approved by the owner. It modifies a **separate camera copy**, never the installed Google-signed Camera. This is not the published recorder or a generic virtual microphone.

No Google APKs, resources, native libraries, decompiled source or recordings belong in this repository. Input/output APKs and temporary decoded material stay outside the checkout. Only original bridge code, transformation scripts, tests and factual results are tracked.

First gate: a renamed camera copy launches on Pixel 6 / Android 15. Then validate deterministic left/right tones in its saved video before integrating live DJI inputs. Keep the app visibly labeled as a test. Do not use the experimental build for important recordings.

## Result

Stopped at the startup gate: the renamed camera crashed in its protection runtime. The failed copy was uninstalled. Continue with `../camera-handoff`, which uses the unchanged Camera app and adds stereo audio afterward. No successful clone recording is claimed.
