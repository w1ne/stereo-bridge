# Stereo Bridge polish and reliability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Deliver a polished, theme-adaptive app with dependable session control, then publish its reviewed source and submit the store candidate.

**Architecture:** Keep native Java UI and independent LC3 driver. Extract testable bounded audio buffering and presentation rules; leave Android routing architecture intact. Perform asynchronous resource cancellation and explicit terminal-state notification.

**Tech Stack:** Java 17, Android API 36, native liblc3, JUnit 4, Gradle, ADB, GitHub CLI.

## Task 1: Session reliability
Files: BridgeService.java, BridgeState.java, new AudioFrameBuffer.java; test AudioFrameBufferTest.java, WavFileTest.java.
- [ ] Add regression tests showing startup overflow must not count as live audio loss, clear pending startup PCM at Live boundary, retain a bounded 200ms buffer and preserve newest frames.
- [ ] Run `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:testDebugUnitTest` and confirm the new assertions fail before changing behavior.
- [ ] Implement buffer API `offer(short[])`, `poll(long, TimeUnit)`, `beginLive()`, `clear()`, `droppedFrames()`; integrate one buffer per mic, reset at each new session, expose aggregate live drops.
- [ ] Make Stop set cancellation immediately, then perform source/socket/shell cleanup on a dedicated thread rather than main thread. Ensure new session cannot start until old worker cleanup and state finalization complete. Include channel-specific error messages and microphone status.
- [ ] On failure finalize resources, leave ERROR visible, and publish a non-ongoing failure notification linking to app. Do not restart microphone capture automatically.
- [ ] Verify tests including WAV header size/channels/rate, release compilation, and review session race paths.

## Task 2: System-theme interface
Files: MainActivity.java, SetupActivity.java, new Ui.java, SessionPresentation.java; values/styles.xml, launcher drawable XML; SessionPresentationTest.java.
- [ ] Add presentation tests for stopped/connecting/live/test/stopping/error labels, primary action and disabled state.
- [ ] Run tests to establish missing behavior.
- [ ] Add shared adaptive palette and rounded native card/button styling. Use system-theme parent `Theme.Material.DayNight` only if platform supports it; otherwise choose runtime palette and platform theme from uiMode before super.onCreate. Set text colors explicitly. Handle Insets with platform APIs, dp sizing, scroll and font scaling.
- [ ] Dashboard: title/tagline; status card; L/R mic cards (name, meter, state); primary Start/Stop; collapsed Advanced containing existing controls and optional diagnostics. Do not show dropped-frame counters on default view. Preserve selection, permissions, camera launch and playback behavior. Explain Camera owns video record button.
- [ ] Setup: shared palette, numbered guide cards, connection status, primary setup action, Done only after connected, collapsed manual fallback; guard callbacks after destruction and serialize concurrent setup actions. Read EditText input on main thread before starting workers.
- [ ] Add vector stereo microphone launcher icon and manifest references; verify light/dark screens on phone and UI accessibility bounds.

## Task 3: Review, device verification and release
- [ ] Spec review, then independent quality review; fix important defects and re-review.
- [ ] Build signed APK/AAB, run all unit tests and release lint, verify apksigner and 16KB zip alignment.
- [ ] Install on target Pixel 8 Pro the authorized device ADB endpoint, verify current app before tapping. Check Start/Stop/restart, cancellation, app death and stock-camera stereo; keep unrelated phone data out of artifacts.
- [ ] Update README, privacy policy, compatibility and verification notes to accurately describe tested behavior. Prepare public app-only UI screenshots.

## Task 4: Public repository and store
- [ ] Review tracked files for credentials, private paths and captures; commit reviewed source.
- [ ] Create public w1ne/stereo-bridge using GitHub CLI, push source and publish signed release candidate artifacts with truthful notes.
- [ ] Host static privacy policy/release landing page in repository GitHub Pages if available; contact details use user's provided public support address.
- [ ] Prepare Google Play listing, screenshot assets, data safety and app access/setup instructions. Open Play Console and submit with user account when accessible; stop only for concrete user-only prerequisites or legally binding agreement that requires confirmation.
