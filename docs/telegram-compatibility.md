# Telegram standalone compatibility

Reference checkout: Telegram commit `62b56a07c` (12.10.1, 7038).
Connected official standalone APK: `org.telegram.messenger.web`, 12.10.1,
versionCode 70389, targetSdk 36. Device: Android 17 / API 37, arm64.

## API history

These are the first source releases containing each change in the local upstream history.

| First version | Commit | Change | TMoe handling |
| --- | --- | --- | --- |
| 9.1.0 (2885) | `67b1fd17e` | `BaseFragment.parentLayout` and `setParentLayout` use `INavigationLayout` | Discover the actual field type; bridge ViewGroup parameters with a cast. |
| 9.5.2 (3208) | `1dcd153fe` | `finishFragment(boolean)` changes from void to boolean | Keep the TMoe void super bridge, discard the host result there; inherit the boolean host implementation for host callers. |
| 12.3.0 (6377) | `28e25a4ca` | `onBackPressed()` becomes `onBackPressed(boolean invoked)` | Generate the matching callback; query-only predictive back avoids invoking the legacy action callback. |
| 12.4.0 (6506) | `fa4e8aa0d` | New `SettingsActivity` replaces the profile settings entry | Existing SettingsActivity factory/fillItems/onClick hook remains compatible; legacy ProfileActivity entry remains supported. |
| 12.10.0 (7031) | `4e1a61eca` | `BaseFragment.createView` becomes protected; `performCreateView` is added | Match protected host methods and emit an override with the actual descriptor. ProfileActivity still declares a public createView in 12.10.1. |

Runtime method capabilities select compatibility behavior. There are no version-number
thresholds, so forks backporting individual APIs can use the same bridges.

Two existing TMoe defects also affect the proxy: the descriptor parser skipped the
parameter after an object/array, and super bridges copied the host return type even
when the TMoe interface required void. The dispatcher also used the module's return
type instead of the host ActionBar return type and placed its cast after return.
These defects predate Telegram 12.10.1; the source changes above should not all be
attributed to that release.

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
./gradlew :app:testLegacyDebugUnitTest :app:testModernDebugUnitTest :app:assembleDebug
```

APK outputs are under `app/build/outputs/apk/{legacy,modern}/debug/`.
The KSP plugin manages variant-specific generated sources automatically.

## Verification

JVM regression tests cover pre-9.1, 9.1–9.5.1, 9.5.2–12.2 and 12.3+ API shapes,
including all imported super methods, return adaptation, overload selection,
ActionBar casts, back callbacks, navigation interfaces, and descriptor parsing.
These are API fixtures; they are not full APK/device tests of older Telegram releases.

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

Both APK flavors build successfully, with 8 regression tests passing per flavor.
The existing HideUserAvatar initialization warning comes from its unimplemented
`initOnce()` placeholder, which always returns false.

API reference: https://libxposed.github.io/api/io/github/libxposed/api/XposedInterface.html
