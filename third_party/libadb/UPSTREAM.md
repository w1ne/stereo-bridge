# LibADB Android

https://github.com/MuntashirAkon/libadb-android
Tag 3.1.1, commit c849886ebc6d48e7b46d967e78a6bb65c90c3b74.
Selected Apache-2.0 option from dual license. Full upstream licenses retained.

Local patch: pairing socket connect/read deadlines and null-safe transport cleanup, to prevent a failed pairing attempt from hanging the setup receiver or leaking its socket. No proprietary code is included.

Local reliability patches: explicit socket-connect and stream-open deadlines; readiness predicates and interruption cleanup; drain buffered output before EOF. Real loopback regression tests live under app/src/test/java/io/github/muntashirakon/adb.
