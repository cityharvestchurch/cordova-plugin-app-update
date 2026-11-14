var exec = require('cordova/exec');

var PLUGIN_NAME = 'AppUpdateChecker';

var AppUpdateChecker = {
  /**
   * Checks whether an update is available.
   * Android: queries Google Play directly via the Play Core library.
   * iOS: queries Apple's public iTunes Lookup API for the published version.
   *
   * Resolves with:
   * {
   *   updateAvailable: Boolean,
   *   platform: 'android' | 'ios',
   *   currentVersion: String,
   *   // android-only:
   *   availableVersionCode: Number,
   *   immediateAllowed: Boolean,
   *   flexibleAllowed: Boolean,
   *   versionStalenessDays: Number,
   *   // ios-only:
   *   latestVersion: String,
   *   storeUrl: String
   * }
   */
  checkForUpdate: function (success, error) {
    exec(success, error, PLUGIN_NAME, 'checkForUpdate', []);
  },

  /**
   * Android only. Launches Google Play's full-screen, blocking update flow.
   * Call after checkForUpdate() confirms immediateAllowed === true.
   */
  startImmediateUpdate: function (success, error) {
    exec(success, error, PLUGIN_NAME, 'startImmediateUpdate', []);
  },

  /**
   * Android only. Starts a background download the user can keep using the app during.
   */
  startFlexibleUpdate: function (success, error) {
    exec(success, error, PLUGIN_NAME, 'startFlexibleUpdate', []);
  },

  /**
   * Android only. Call once a flexible update reports installStatus 'DOWNLOADED'
   * (via onFlexibleUpdateStateChange) to restart and install it.
   */
  completeFlexibleUpdate: function (success, error) {
    exec(success, error, PLUGIN_NAME, 'completeFlexibleUpdate', []);
  },

  /**
   * Android only. Reads the current download/install state for an in-progress
   * flexible update. Call this on an interval (e.g. every 2-3s) while a
   * flexible update is downloading, and call completeFlexibleUpdate() once
   * installStatus is 'DOWNLOADED'.
   *
   * Resolves with:
   * { installStatus: String, bytesDownloaded: Number, totalBytesToDownload: Number }
   */
  getFlexibleUpdateStatus: function (success, error) {
    exec(success, error, PLUGIN_NAME, 'getFlexibleUpdateStatus', []);
  },

  /**
   * iOS: opens the App Store listing. Android: opens the Play Store listing.
   * Use this as the call-to-action on iOS, since there's no native forced
   * in-app flow there the way there is on Android.
   */
  openStoreListing: function (success, error) {
    exec(success, error, PLUGIN_NAME, 'openStoreListing', []);
  }
};

module.exports = AppUpdateChecker;
