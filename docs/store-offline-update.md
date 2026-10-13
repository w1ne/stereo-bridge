# Google Play update checklist — offline candidate

Version code 4 / 0.2.0-offline-rc1 changes the product from stock-camera routing to an offline recorder. The currently submitted code 3 is unchanged.

Before submitting code 4:

1. Complete the Pixel hardware checks in [offline verification](offline-verification.md).
2. Replace screenshots and demonstration video with the new built-in Record/Stop flow. Demonstrate two distinct microphone channels and a reboot with Wi-Fi/debugging disabled.
3. Update listing and reviewer instructions: first-use Bluetooth pairing and normal permissions; daily open app → Record → Stop. Remove wireless-debugging setup instructions.
4. Declare the camera foreground-service use in addition to microphone capture; provide the foreground recording demonstration. Recording is user initiated with visible progress and Stop.
5. Publish the offline privacy policy alongside the version update. Audio/video remain local; no Internet permission, analytics or cloud collection.
6. Upload the signed version 4 AAB, check Google processing and declarations, submit the release, and inspect its resulting review status.

Physical validation and store submission have not been completed for version 4. A successful local build is not Google Play approval.
