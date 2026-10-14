# Security

The offline recorder connects only to the two selected, already paired Bluetooth transmitters. Camera and audio capture start after an explicit Record action and end on Stop, cancellation or failure. No recording starts automatically after reboot.

The app has no Internet permission, downloaded code, root, Shizuku or ADB runtime dependency. It uses scoped app-owned MediaStore captures; recovery checks ownership before updating or deleting pending items. Application backup is disabled.

The historical 0.1 stock-camera bridge used a different, debugging-dependent architecture. Its requirements do not apply to the 0.2 offline recorder.

For security issues, email andrii@shylenko.com. Do not post pairing codes, private keys, raw phone/Bluetooth logs or personal recordings in public issues. See [privacy](PRIVACY.md) and [architecture](docs/architecture.md).
