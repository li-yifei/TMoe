package cc.ioctl.tmoe.startup;

import android.util.Log;

import androidx.annotation.NonNull;

import io.github.libxposed.api.XposedModule;

/**
 * API 101 lifecycle entry. Target 101 deliberately retains LSPosed's legacy hook bridge
 * for existing function hooks and EzXHelper. API 102 forbids those legacy API calls.
 * The legacy APK flavor remains available for older Xposed implementations.
 */
public final class ModernHookEntry extends XposedModule {
    private String processName;

    @Override
    public void onModuleLoaded(@NonNull ModuleLoadedParam param) {
        processName = param.getProcessName();
        log(Log.INFO, "TMoe", "API 101 entry loaded in " + processName
                + "; runtime API " + getApiVersion());
        HookEntry.setModulePath(getModuleApplicationInfo().sourceDir);
        // Resource injection uses Android ResourcesLoader in the shared startup path.
        // EzXHelper's legacy initZygote requires XModuleResources, absent from modern API.
    }

    @Override
    public void onPackageReady(@NonNull PackageReadyParam param) {
        if (!param.isFirstPackage()) return;
        try {
            HookEntry.initializeForPackage(param.getPackageName(), param.getClassLoader());
            log(Log.INFO, "TMoe", "Startup hooks installed for " + param.getPackageName());
        } catch (Throwable error) {
            log(Log.ERROR, "TMoe", "Startup hook installation failed for " + param.getPackageName(), error);
        }
    }
}
