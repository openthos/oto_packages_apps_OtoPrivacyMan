package org.openthos.privacyman.variants;


import android.content.pm.ApplicationInfo;

import org.openthos.privacyman.ApkVariant;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class Minimal extends ApkVariant
{
    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        addAppOpsToAppInfo(lpparam);
    }

    @Override
    protected boolean onMatch(ApplicationInfo appInfo, ClassChecker classChecker) {
        return false;
    }
}
