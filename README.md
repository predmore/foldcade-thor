# Foldcade Thor

A root device-controls plugin for [Foldcade](https://github.com/predmore/foldcade)
on the AYN Thor. It exposes fan, stick lights, per-screen brightness, panel power
and per-game fan settings through Foldcade device protocol 1. Foldcade does not
need root. This app bundles no modules or native binaries.

## Requirements and setup

Android 13 or newer, Magisk, and the relevant
[thor-features modules](https://github.com/Wingdom/thor-features):

| Control | Module |
| --- | --- |
| Fan and per-game fan | thor-fanctl |
| Stick lights | thor-stickfx |
| Independent brightness | thor-screenbright, active after reboot |
| Panel power | thor-screenblank |
| Display ID lookup (optional) | thor-screenmove |
| Per-app profiles (optional) | thor-appwatch |

1. Install Foldcade Thor and open it to check root and module availability.
2. Magisk 31 never prompts for root. Open **Magisk › Superuser › +** and add
   **Foldcade Thor**, then tap **Check again** in this app.
3. Open **Foldcade › Settings › Plugins › Foldcade Thor** and turn on
   **Control this device**.

Missing controls stay hidden. Installing a module needs a reboot; check again
afterward. The launcher activity is a setup/status page. Use Foldcade to change
settings. Debug builds include a local device-check button that temporarily
changes controls and restores them; it has no intent or provider test endpoint.

## Authentication

The exported provider accepts only `call()`, from `app.foldcade` or
`app.foldcade.debug`. Android's calling package, binder UID, package UID and exact
current SHA-256 signer set must all match. All rejected calls return
`not_allowed`, log the package and UID, and do no root work.

| Caller | Pinned certificate SHA-256 |
| --- | --- |
| app.foldcade | 36f6883086e694e0a8b245421543063e6b96450970bd4030198543a59e6581ff |
| app.foldcade.debug | b8069324c56de0db74f96afc444763e97b71dfae33440a95755b380b74f36bd2 |

The authority is `app.foldcade.thor.device` (`app.foldcade.thor.debug.device` in
debug builds). A fork must deliberately change the pins for its own launcher.
Root is first probed on an allowed `state` call or when this status page opens,
then on refresh. A valid authenticated action also rebuilds state if Android
restarted the provider; an empty game.end never probes. One executor owns
all root work; one persistent `su` shell proves UID 0, frames replies with a
random sentinel and times out each command after ten seconds. A failed session
is retired until refresh, with UID-checked one-shot `su -c` fallback.

## Version compatibility

Only these installed CLI commands are called. `fanctl`, `stickfx`, `screenblank`,
`screenmove` and `appwatch` are resolved at `/system/bin/`:

```text
id -u
fanctl get
fanctl quiet|balanced|performance
fanctl default                         # restore a recorded stock baseline only
stickfx get
stickfx off|charge|breathe|usb           # charge and breathe refuse black stick colours: needs_color
screenblank status
screenblank on top|bottom
screenblank off top|bottom
screenmove status                      # display IDs only; never moves an app
appwatch status
appwatch stop
appwatch start
cmd display get-brightness --id <runtime-id>
cmd display set-brightness <0.01..1> --id <runtime-id>
```

The brightness probe also runs this fixed shell check:

```sh
test -d /data/adb/modules/thor-screenbright && test ! -e /data/adb/modules/thor-screenbright/disable && test ! -e /data/adb/modules/thor-screenbright/remove
```

Bottom display IDs come from `screenmove status`, accepting its normal idle exit
code 1, with Android's non-default display as fallback. Brightness uses a finite,
clamped float formatted with the root locale. Only enum values and validated
numeric display IDs reach command builders. Fan and panel changes are verified
by read-back. Panel plans always light the wanted panel before darkening the
other. A dozing status with both panels off is reported as `dual`: those sleep
readings do not describe an awake panel layout and both relight on wake.

## Games and appwatch

On the first `game.begin`, the plugin stops appwatch only if its daemon was
running, then reads the fan baseline after appwatch restores its own baseline.
It saves the game's tier, the first baseline, and whether appwatch was running
in private no-backup storage before applying the game tier and replying.
Repeated begins retain that first baseline. No appwatch table or enabled switch
is changed.

`game.end` restores the baseline only while the fan still matches the game tier;
a newer manual choice stays. It then restarts appwatch if it was previously
running and clears the saved override. With no saved override, end returns the
last cached state without any root call. `fan.set` ends the override without
restoring, resumes appwatch when needed, and applies the user's tier. Recovery
survives process death and reboot; Foldcade ends abandoned overrides on Home.

## Build and releases

Use JDK 21, Android SDK platform 37.0 and build tools 37.0.0. The checked-in
Gradle 9.6.0 wrapper uses AGP 9.4.1. There are no third-party runtime libraries.

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

The app targets API 35, so Foldcade can update it without a prompt on Android 17. Debug uses `app.foldcade.thor.debug`; release uses
`app.foldcade.thor`. Release builds require `RELEASE_KEYSTORE_FILE`,
`RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS` and `RELEASE_KEY_PASSWORD`.
A deliberate unsigned validation build uses `-PthorAllowUnsigned=true`.
`versionCode` is the commit count, overridable with `-PthorVersionCode=N`.
`versionName` starts at 0.1.0 in `app/build.gradle.kts`.

CI runs JVM tests, lint and debug assembly on PRs and main. A pushed `v*` tag
matching versionName creates the latest full GitHub release with exactly one
asset, `foldcade-thor.apk`, and `versionCode: N` in its notes. It requires the four
repository signing secrets, enforces increasing version codes, and refuses to
publish unless `apksigner` verifies this certificate:

```text
6d3570114da628a5085edf54c43186e0bc8cf6592f0bff6dec7ab7ee75c93366
```

## License

[Apache License 2.0](LICENSE). See [NOTICE](NOTICE).
`RootProtocol.java` and `RootSession.java` are derived from
[thor-features](https://github.com/Wingdom/thor-features) by Wingdom (Apache-2.0).
Screen parsing and safe panel ordering also follow its thor-screenpanel CLI
contract. No Qualcomm firmware, GPL fsck.exfat, or module binaries are included.
