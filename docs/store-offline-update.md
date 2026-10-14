# Google Play update checklist — DRAFT offline candidate

Version code 6 / 0.2.0-offline-rc3 changes the previously published bridge into an offline recorder. The owner explicitly authorized the Google Play production update and public repository publication on 14 October 2026. The checklist records work required for that authorized submission.

Before submitting code 6:

1. Complete hardware and compatibility checks in [offline verification](offline-verification.md), including permissions denied/granted, background/sleep Stop, direct save/recovery, repeat capture and endurance. Record limits accurately.
2. Replace screenshots and demonstration video with built-in Record/Stop. Demonstrate two distinct channels, a reboot and offline capture.
3. Use the candidate [listing draft](../store/listing.md): ordinary Bluetooth pairing, Camera/Nearby devices access, optional notifications and settings recovery. No phone Microphone or debugging setup.
4. Declare camera and connectedDevice foreground-service use. Describe paired external microphone RFCOMM streams and provide explicit user-started capture/background Stop demonstration. Google determines eligibility and approval.
5. Review and publish the offline privacy policy with the version update. In-app policy is generated from root PRIVACY.md. Website draft is not yet deployed.
6. Validate signed code 6 AAB, API29+ behavior, all three native ABIs and 16 KiB page compatibility before upload. Verify resulting manifest and final dependency/data declarations.
7. Upload only after required validation, assets and declarations are complete; inspect Google processing and review status.

Local build success does not establish hardware compatibility, endurance or Google Play approval.
