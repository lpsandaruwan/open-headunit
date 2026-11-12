# Dongfeng projection with OABL vehicle data

The private Dongfeng build can embed the sibling OABL Link service so projection and
vehicle data share the one firmware-accepted package, `com.koushikdutta.vysor`.

```sh
./gradlew -PheadunitVysor=true -PheadunitOablLink=true \
  :core:test :wire:test :app:testGithubDebugUnitTest :app:assembleGithubRelease
```

OABL Link sources default to `../oabl/oabl-link`; override with `-PoablLinkDir=...`.
Ordinary builds do not include Link. Include both flags for future Dongfeng updates
to retain telemetry. The version name ends in `-oabl-link` for combined builds.

Open Headunit keeps its projection UI, settings, USB handling and rendering changes.
The existing Link service runs in `:oabl_link`, starts after boot/update and when
Open Headunit opens, and continues when projection restarts its own process. Tap the
OABL Link notification or launcher entry for status and the pairing code.

The AI-box dashboard/Energy app continue using their existing data protocol and
stored history. Pair again only if the previous app replacement lost its pairing.
Sign with the existing installed certificate and update in place with `adb install -r`;
uninstalling or clearing storage loses settings and pairing.

See the sibling `oabl/oabl-link/headunit/README.md` for implementation and verification.
