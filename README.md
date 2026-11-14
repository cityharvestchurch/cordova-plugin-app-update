# cordova-plugin-app-update-checker

Store-native update checks and force-update flows that **never touch your own
backend**. Use this as a fallback layer alongside your OutSystems Site
Property check, so a force-update prompt can still fire even if the app's own
API (or its SSL pinning) is broken.

- **Android** — uses the Google Play Core "In-App Updates" API
  (`AppUpdateManager`). Talks directly to Google Play services. Supports
  `IMMEDIATE` (full-screen, blocking) and `FLEXIBLE` (background download)
  flows.
- **iOS** — Apple has no equivalent native API, so this queries Apple's
  public, unauthenticated iTunes Lookup API (`itunes.apple.com/lookup`),
  compares versions, and deep-links to the App Store listing.

## Install

```bash
cordova plugin add ./cordova-plugin-app-update-checker
```

(Or reference the path/git URL in your `config.xml` `<plugin>` entry if
you're driving the build through OutSystems' native shell / MABS.)

## Usage

```js
document.addEventListener('deviceready', function () {
  cordova.plugins.AppUpdateChecker.checkForUpdate(function (info) {
    if (!info.updateAvailable) return;

    if (info.platform === 'android') {
      if (info.immediateAllowed) {
        cordova.plugins.AppUpdateChecker.startImmediateUpdate(
          function () { /* update flow completed / app will restart */ },
          function (err) {
            // Immediate flow itself failed to launch - fall back to the
            // store listing so the user still has something actionable.
            console.error('Immediate update failed:', err);
            cordova.plugins.AppUpdateChecker.openStoreListing(function () {}, console.error);
          }
        );
      } else if (info.flexibleAllowed) {
        cordova.plugins.AppUpdateChecker.startFlexibleUpdate(function () {}, console.error);
        // Poll instead of subscribing to a push listener - matches the
        // request/response shape OutSystems Client Actions expect.
        var pollHandle = setInterval(function () {
          cordova.plugins.AppUpdateChecker.getFlexibleUpdateStatus(function (state) {
            if (state.installStatus === 'DOWNLOADED') {
              clearInterval(pollHandle);
              cordova.plugins.AppUpdateChecker.completeFlexibleUpdate(function () {}, console.error);
            }
          }, console.error);
        }, 2500);
      }
    } else {
      // iOS: show your own blocking modal, then send them to the store.
      cordova.plugins.AppUpdateChecker.openStoreListing(function () {}, console.error);
    }
  }, function (err) {
    console.error('Update check failed:', err);
  });
});
```

## Where this fits in the layered approach

1. **Primary:** OutSystems Site Property check — fastest to change, no store
   review needed.
2. **Fallback (this plugin):** runs entirely against Google Play / Apple's
   servers, so it keeps working if your own API or its SSL pinning is down.
3. **Last resort:** a hardcoded minimum-version floor compiled into the
   native shell, for the (rare) case where neither of the above is reachable.
   Requires a store submission to change, so treat it as a dead-man's switch,
   not a primary mechanism.

## Notes

- Android `market://details?id=...` and iOS `trackViewUrl` both need no
  `LSApplicationQueriesSchemes` / Android package-visibility entries beyond
  what a standard Cordova app already has, since they're opened via
  `Intent.ACTION_VIEW` / `UIApplication.open` rather than queried first.
- The iTunes Lookup call and the Play Core call both go over standard HTTPS
  to Apple/Google infrastructure — confirm your SSL pinning implementation is
  scoped to your own API's domain and not applied app-wide, or these
  fallbacks would be affected too.
- `clientVersionStalenessDays()` on Android lets you stage the nag: flexible
  first, then immediate after N days — mirrors the two-tier Site Property
  pattern if you want parity across platforms.
