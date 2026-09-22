package com.cityharvestchurch.chcapp.appupdate;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import com.google.android.play.core.appupdate.AppUpdateManager;
import com.google.android.play.core.appupdate.AppUpdateManagerFactory;
import com.google.android.play.core.appupdate.AppUpdateOptions;
import com.google.android.play.core.install.InstallStateUpdatedListener;
import com.google.android.play.core.install.model.AppUpdateType;
import com.google.android.play.core.install.model.InstallStatus;
import com.google.android.play.core.install.model.UpdateAvailability;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Talks directly to Google Play Core. No custom backend is involved, so this
 * keeps working even if the app's own API (OutSystems, SSL pinning, etc.)
 * is unreachable.
 */
public class AppUpdateChecker extends CordovaPlugin {

    private static final String TAG = "AppUpdateChecker";
    private static final int REQUEST_CODE_UPDATE = 8471;

    private AppUpdateManager appUpdateManager;
    private CallbackContext flexibleListenerCallback;
    private CallbackContext pendingImmediateCallback;

    private final InstallStateUpdatedListener installStateListener = state -> {
        if (flexibleListenerCallback == null) return;
        try {
            JSONObject payload = new JSONObject();
            payload.put("installStatus", installStatusName(state.installStatus()));
            payload.put("bytesDownloaded", state.bytesDownloaded());
            payload.put("totalBytesToDownload", state.totalBytesToDownload());
            PluginResult result = new PluginResult(PluginResult.Status.OK, payload);
            result.setKeepCallback(true);
            flexibleListenerCallback.sendPluginResult(result);
        } catch (JSONException e) {
            Log.e(TAG, "Failed to serialize install state", e);
        }
    };

    @Override
    protected void pluginInitialize() {
        appUpdateManager = AppUpdateManagerFactory.create(cordova.getContext());
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) {
        switch (action) {
            case "checkForUpdate":
                checkForUpdate(callbackContext);
                return true;
            case "startImmediateUpdate":
                startUpdate(AppUpdateType.IMMEDIATE, callbackContext);
                return true;
            case "startFlexibleUpdate":
                startUpdate(AppUpdateType.FLEXIBLE, callbackContext);
                return true;
            case "completeFlexibleUpdate":
                appUpdateManager.completeUpdate();
                callbackContext.success();
                return true;
            case "registerListener":
                flexibleListenerCallback = callbackContext;
                appUpdateManager.registerListener(installStateListener);
                return true;
            case "openStoreListing":
                openStoreListing(callbackContext);
                return true;
            default:
                return false;
        }
    }

    private void checkForUpdate(CallbackContext callbackContext) {
        appUpdateManager.getAppUpdateInfo().addOnSuccessListener(info -> {
            try {
                JSONObject payload = new JSONObject();
                boolean updateAvailable = info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE;
                payload.put("updateAvailable", updateAvailable);
                payload.put("platform", "android");
                payload.put("currentVersion", cordova.getActivity().getPackageManager()
                        .getPackageInfo(cordova.getActivity().getPackageName(), 0).versionName);
                payload.put("availableVersionCode", info.availableVersionCode());
                payload.put("versionStalenessDays",
                        info.clientVersionStalenessDays() == null ? -1 : info.clientVersionStalenessDays());
                payload.put("immediateAllowed", info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE));
                payload.put("flexibleAllowed", info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE));
                callbackContext.success(payload);
            } catch (Exception e) {
                callbackContext.error("Failed to read update info: " + e.getMessage());
            }
        }).addOnFailureListener(e ->
                callbackContext.error("Play Core check failed: " + e.getMessage()));
    }

    private void startUpdate(int updateType, CallbackContext callbackContext) {
        appUpdateManager.getAppUpdateInfo().addOnSuccessListener(info -> {
            boolean allowed = (updateType == AppUpdateType.IMMEDIATE)
                    ? info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
                    : info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE);

            if (info.updateAvailability() != UpdateAvailability.UPDATE_AVAILABLE || !allowed) {
                callbackContext.error("No eligible update available for the requested type");
                return;
            }

            try {
                pendingImmediateCallback = callbackContext;
                appUpdateManager.startUpdateFlowForResult(
                        info,
                        cordova.getActivity(),
                        AppUpdateOptions.newBuilder(updateType).build(),
                        REQUEST_CODE_UPDATE);
            } catch (Exception e) {
                callbackContext.error("Failed to start update flow: " + e.getMessage());
            }
        }).addOnFailureListener(e -> callbackContext.error("Play Core check failed: " + e.getMessage()));
    }

    private void openStoreListing(CallbackContext callbackContext) {
        try {
            String packageName = cordova.getActivity().getPackageName();
            Intent intent = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=" + packageName));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            cordova.getActivity().startActivity(intent);
            callbackContext.success();
        } catch (Exception e) {
            callbackContext.error("Could not open Play Store: " + e.getMessage());
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode != REQUEST_CODE_UPDATE || pendingImmediateCallback == null) return;

        if (resultCode == Activity.RESULT_OK) {
            pendingImmediateCallback.success();
        } else if (resultCode == Activity.RESULT_CANCELED) {
            pendingImmediateCallback.error("User declined or cancelled the update flow");
        } else {
            pendingImmediateCallback.error("Update flow failed, result code: " + resultCode);
        }
        pendingImmediateCallback = null;
    }

    @Override
    public void onResume(boolean multitasking) {
        super.onResume(multitasking);
        // If the user backgrounded the app mid-IMMEDIATE-update instead of
        // completing it, resuming the flow here keeps the "force" guarantee.
        appUpdateManager.getAppUpdateInfo().addOnSuccessListener(info -> {
            if (info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                try {
                    appUpdateManager.startUpdateFlowForResult(
                            info, cordova.getActivity(),
                            AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build(),
                            REQUEST_CODE_UPDATE);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to resume immediate update", e);
                }
            }
        });
    }

    private String installStatusName(int status) {
        switch (status) {
            case InstallStatus.DOWNLOADED: return "DOWNLOADED";
            case InstallStatus.DOWNLOADING: return "DOWNLOADING";
            case InstallStatus.FAILED: return "FAILED";
            case InstallStatus.INSTALLED: return "INSTALLED";
            case InstallStatus.INSTALLING: return "INSTALLING";
            case InstallStatus.PENDING: return "PENDING";
            case InstallStatus.CANCELED: return "CANCELED";
            default: return "UNKNOWN";
        }
    }
}
