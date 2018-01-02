/*
 * AppOpsXposed - AppOps for Android 4.3+
 * Copyright (C) 2013, 2014 Joseph C. Lehner
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of  MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package at.jclehner.appopsxposed.hacks;

import android.os.Build;

import java.util.HashMap;
import java.util.Map;
import java.util.jar.Manifest;

import at.jclehner.appopsxposed.AppOpsXposed;
import at.jclehner.appopsxposed.Hack;
import at.jclehner.appopsxposed.util.AppOpsManagerWrapper;
import at.jclehner.appopsxposed.util.Constants;
import at.jclehner.appopsxposed.util.XUtils;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;


public class GeneralHacks extends Hack
{
	@Override
	protected void handleLoadFrameworkPackage(LoadPackageParam lpparam) throws Throwable
	{
		fixPmCrash();
		grantAppOpsPermissionsToSelf();
		// FIXME move to a separate hack or rename this hack
		fixVerifyOpCrash();
	}

	@Override
	protected void handleLoadAnyPackage(LoadPackageParam lpparam) throws Throwable
	{
		fixOpPermissions();
	}

	@Override
	protected String onGetKeySuffix() {
		return "pm_crash";
	}

	@Override
	protected boolean isEnabledByDefault() {
		return true;
	}

	private void grantAppOpsPermissionsToSelf() throws Throwable
	{
		final Class<?> pmSvcClazz = loadClass("com.android.server.pm.PackageManagerService");
		// For the duration of grantPermissionsLPw, make PackageManagerService believe that
		// the {UPDATE,GET}_APP_OPS_STATS permissions have a protectionLevel of 0 (normal).
		XposedBridge.hookAllMethods(pmSvcClazz, "grantPermissionsLPw", new XC_MethodHook() {

			private ThreadLocal<Map<String, Integer>> mRestoreInfo = new ThreadLocal<Map<String, Integer>>() {
				protected Map<String, Integer> initialValue() { return new HashMap<String, Integer>(); }
			};

			@Override
			protected void beforeHookedMethod(MethodHookParam param) throws Throwable
			{
				if(!isAoxModulePackage(param))
					return;

				try
				{
					XposedHelpers.setBooleanField(XposedHelpers.getObjectField(param.args[0],
							"mExtras"), "permissionsFixed", false);
				}
				catch(Throwable t)
				{
					// If the above fails, simply set the 'replace' argument to true (this also sets
					// permissionsFixed to false)
					param.args[1] = true;
				}

				try
				{
					mRestoreInfo.get().clear();

					final Map<String, ?> perms = getPermissions(param.thisObject);
					for(String perm : Constants.APP_OPS_PERMISSIONS)
					{
						if(perms.containsKey(perm))
						{
							final Object basePerm = perms.get(perm);

							mRestoreInfo.get().put(perm, XposedHelpers.getIntField(basePerm, "protectionLevel"));
							XposedHelpers.setIntField(basePerm, "protectionLevel", 0);
						}
					}
				}
				catch(Throwable t)
				{
					debug(t);
				}
			}

			@Override
			protected void afterHookedMethod(MethodHookParam param) throws Throwable
			{
				if(!isAoxModulePackage(param))
					return;

				try
				{
					final Map<String, ?> perms = getPermissions(param.thisObject);
					for(String perm : mRestoreInfo.get().keySet())
					{
						XposedHelpers.setIntField(perms.get(perm), "protectionLevel",
								mRestoreInfo.get().get(perm));
					}
				}
				catch(Throwable t)
				{
					debug(t);
				}
			}

			private boolean isAoxModulePackage(MethodHookParam param)
			{
				return AppOpsXposed.MODULE_PACKAGE.equals(
						XposedHelpers.getObjectField(param.args[0], "packageName"));
			}

			@SuppressWarnings("unchecked")
			private Map<String, ?> getPermissions(Object pkgMgrSvc)
			{
				return (Map<String, ?>) XposedHelpers.getObjectField(
						XposedHelpers.getObjectField(pkgMgrSvc, "mSettings"),
						"mPermissions");
			}
		});
	}

	private void fixPmCrash() throws Throwable
	{
		final Class<?> pmSvcClazz = loadClass("com.android.server.pm.PackageManagerService");
		XposedBridge.hookAllMethods(pmSvcClazz, "addPackageHoldingPermissions",
				new XC_MethodHook(XC_MethodHook.PRIORITY_LOWEST) {
					@Override
					protected void afterHookedMethod(MethodHookParam param) throws Throwable
					{
						final Throwable t = param.getThrowable();
						if(t != null && (t instanceof NullPointerException))
							param.setResult(null);
					}
		});
	}

	private void fixVerifyOpCrash() throws Throwable
	{
		final Class<?> appOpsSvcClazz = loadClass("com.android.server.AppOpsService");
		XposedHelpers.findAndHookMethod(appOpsSvcClazz, "verifyIncomingOp", int.class,
				new XC_MethodHook() {

					@Override
					protected void afterHookedMethod(MethodHookParam param) throws Throwable
					{
						final Throwable t = param.getThrowable();
						if(t != null && (t instanceof IllegalArgumentException))
						{
							final int op = (Integer) param.args[0];
							if(op >= 0 && op < AppOpsManagerWrapper._NUM_OP)
							{
								param.setResult(null);
								// FIXME log -> debug
								log("Eating IllegalArgumentException for op #" + op);
							}
						}
					}
		});
	}

	private void fixOpPermissions() throws Throwable
	{
		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
			return;

		// On <= KitKat, the permissions for OP_POST_NOTIFICATION and OP_WIFI_SCAN
		// are interchanged: OP_POST_NOTIFICATION returns a WiFi permission, whereas
		// OP_WIFI_SCAN returns null.
		XposedHelpers.findAndHookMethod(loadClass("android.app.AppOpsManager"),
				"opToPermission", int.class, new XC_MethodHook() {
					@Override
					protected void afterHookedMethod(MethodHookParam param) throws Throwable
					{
						final int op = (int) param.args[0];

						if(op == AppOpsManagerWrapper.OP_POST_NOTIFICATION)
							param.setResult(null);
						else if(op == AppOpsManagerWrapper.OP_WIFI_SCAN)
							param.setResult(android.Manifest.permission.ACCESS_WIFI_STATE);
					}
		});
	}
}
