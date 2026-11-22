# Telegram standalone compatibility

Current reference: Telegram commit `21f7d109d` (13.2.2, 7303), published
November 12, 2026. On November 17, the connected standalone installation on
`11xlr:45454` is `org.telegram.messenger.web` 13.2.2, versionCode 73039,
running Android 17 on arm64. Source inspection uses the upstream commit; the
local Telegram working checkout may still contain 12.10.1.

The previous reference was commit `62b56a07c` (12.10.1, 7038), tested against
standalone versionCode 70389. That device result is retained below as history.

## API history

These are the first source releases containing each change in the local upstream history.

| First version | Commit | Change | TMoe handling |
| --- | --- | --- | --- |
| 9.1.0 (2885) | `67b1fd17e` | `BaseFragment.parentLayout` and `setParentLayout` use `INavigationLayout` | Discover the actual field type; bridge ViewGroup parameters with a cast. |
| 9.5.2 (3208) | `1dcd153fe` | `finishFragment(boolean)` changes from void to boolean | Keep the TMoe void super bridge, discard the host result there; inherit the boolean host implementation for host callers. |
| 12.3.0 (6377) | `28e25a4ca` | `onBackPressed()` becomes `onBackPressed(boolean invoked)` | Generate the matching callback; query-only predictive back avoids invoking the legacy action callback. |
| 12.4.0 (6506) | `fa4e8aa0d` | New `SettingsActivity` replaces the profile settings entry | Existing SettingsActivity factory/fillItems/onClick hook remains compatible; legacy ProfileActivity entry remains supported. |
| 12.10.0 (7031) | `4e1a61eca` | `BaseFragment.createView` becomes protected; `performCreateView` is added | Match protected host methods and emit an override with the actual descriptor. ProfileActivity still declares a public createView in 12.10.1. |
| 12.10.2 (7086) | `163356809` | R8 optimization and obfuscation are enabled by commenting out `-dontoptimize` and `-dontobfuscate` | Resolve renamed UI callbacks using DEX strings, signatures and call relationships. Account for staticization and parameter reordering. |
| 13.2.0 (7280) | `55356b2a7` | The blanket `org.telegram.messenger.*` keep rule is removed | Resolve the retained manifest ApplicationLoaderImpl entry and inherited onCreate, plus optimized messenger classes structurally. |

Runtime method capabilities select compatibility behavior. There are no version-number
thresholds, so forks backporting individual APIs can use the same bridges.

Two existing TMoe defects also affect the proxy: the descriptor parser skipped the
parameter after an object/array, and super bridges copied the host return type even
when the TMoe interface required void. The dispatcher also used the module's return
type instead of the host ActionBar return type and placed its cast after return.
These defects predate Telegram 12.10.1; the source changes above should not all be
attributed to that release.

## Optimized standalone builds

`ApplicationLifecycleResolver` starts from the retained manifest application class
and finds the inherited lifecycle method. `HostDexIndex` reads base/split DEX files
using the existing dexlib2 dependency. Resolvers combine retained protocol fields,
literal strings, signatures, and call/field relationships. A selector must find a
unique match before its hook is installed. Production code does not embed the
13.2.2 obfuscated class aliases.

The settings resolver follows the settings item factory and ID assignment, then
hooks both list population and item clicks. It rolls back list population if
installing the click hook fails. Navigation uses the host fragment's retained
`presentFragment` entry. Theme lookup follows BaseFragment's color accessor and
the host's theme-key table.

Compatibility paths also cover message forwarding/copying, sponsored messages,
tracking, emoji, channel switching, reactions, phone visibility, service stories,
video mute, blur, instant camera, greetings, spoilers, command confirmation,
topic navigation, premium stickers, exact last-seen time, history menus, message
IDs, reload menus, profile IDs, linked channels, account access, member export,
historical common groups, local member mention results, and additional group
information screens.
Named legacy paths remain available when the corresponding host symbols exist.

On optimized hosts, profile IDs appear in the profile menu and can be copied.
Legacy hosts retain the original information-row placement. The repeat action
continues to require a user click; paid forwarding retains the host confirmation
flow. Copying restricted/topic media retains the existing text/sticker scope.
Telegram's debug-mode branches have been removed by R8 in the inspected APK;
EnableDebugMode reports unavailable there.

## Xposed builds

- `legacy`: original Xposed entry / API 82 dependency, Android 7+.
- `modern`: libxposed 101.0.1 entry, min/target Xposed API 101, Android 8+.
  It uses `onModuleLoaded` and `onPackageReady`, then shares existing hooks through
  LSPosed's legacy bridge. It deliberately targets 101: targeting 102 disallows
  calls to legacy `de.robv.android.xposed` APIs. This is a lifecycle migration,
  with existing function hooks and EzXHelper continuing through that bridge.

The modern flavor registers META-INF/xposed metadata. The legacy flavor contains
only the original legacy entry. Both have the same app ID and share configuration.
Use matching signing keys when switching APK variants.

Build with JDK 17:

```sh
TMOE_HOST_APK=/path/to/telegram-13.2.2.apk \
  ./gradlew :app:testLegacyDebugUnitTest :app:testModernDebugUnitTest :app:assembleDebug
```

APK outputs are under `app/build/outputs/apk/{legacy,modern}/debug/`.
The KSP plugin manages variant-specific generated sources automatically.
`TMOE_HOST_APK` is an optional Gradle test input. Supplying it runs the real APK
selector tests; omitting it skips those tests while retaining API fixture tests.
Changing the APK invalidates the test task's up-to-date state.

## Verification scope

JVM regression tests cover pre-9.1, 9.1–9.5.1, 9.5.2–12.2 and 12.3+ API shapes,
including all imported super methods, return adaptation, overload selection,
ActionBar casts, back callbacks, navigation interfaces, and descriptor parsing.
These are API fixtures. Full APK/device regression coverage for older releases,
including the intermediate 12.10.2–13.1 optimization stage, remains pending.

Actual-APK JVM tests check structural symbol uniqueness and relevant descriptors
against 13.2.2. Both debug flavors built successfully with 29 tests per flavor,
zero failures, and zero skips when the APK was supplied. The reusable ART probe
passed on the connected device and additionally loads the generated fragment
proxy, checks its super bridges and return adaptation, resolves application
startup, and constructs a settings item through the actual host factory:

```sh
python3 tools/compat/verify-device.py \
  --serial 11xlr:45454 \
  --sdk /opt/homebrew/share/android-commandlinetools
```

The probe uses a separate `app_process` and never registers hooks or opens chats.
It checks theme accessor signatures; color evaluation requires the running
Telegram application context. Selector and ART checks establish binding behavior.
User-facing feature behavior also requires interaction in the host application.

On 13.2.2, the final modern debug APK was installed at device-local time
19:00:55 on November 17, 2026. The fresh process (PID 26862) logged API 101 entry
loading under runtime API 102 and successful startup hook installation. Its
module log reported only the existing HideUserAvatar placeholder warning.
The TMoe Settings row was observed in Telegram Settings earlier in this session. Further
page navigation and feature interaction acceptance remains pending while the
connected phone is locked. Only Telegram is restarted during verification.

### Previous 12.10.1 verification

On the connected device, an isolated ART check loaded the actual 12.10.1 BaseFragment
from the installed APK, generated and loaded the fixed proxy, checked every super
bridge descriptor, invoked finishFragment$super(true), and verified that the host
boolean method still returns false when no parent layout is attached. All passed.
Loading the device's previous cached proxy reproduced a return-type assertion:
`public boolean ProxyFragmentImpl.finishFragment$super(boolean)` where TMoe requires void.

The reported injection failure was caused by enforced unmount / denylist policy.
After the user changed Zygisk enforcement from 1 to 2 (unmount-only), the framework
logged `API 101 entry loaded ... runtime API 102` and `Startup hooks installed`.
A fresh proxy DEX was generated and the TMoe settings entry appeared in Telegram.
The user independently confirmed the module works on the official 12.10.1 build.
Only Telegram was cold-started; the phone was kept running.

At the 12.10.1 checkpoint, both APK flavors built with 8 regression tests per flavor.
The existing HideUserAvatar initialization warning comes from its unimplemented
`initOnce()` placeholder, which always returns false.

API reference: https://libxposed.github.io/api/io/github/libxposed/api/XposedInterface.html
