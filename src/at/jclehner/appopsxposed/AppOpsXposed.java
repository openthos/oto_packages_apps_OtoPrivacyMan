/*
 * AppOpsXposed - AppOps for Android 4.3+
 * Copyright (C) 2013-2015 Joseph C. Lehner
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

package at.jclehner.appopsxposed;

import static at.jclehner.appopsxposed.util.Util.log;
import android.content.res.XModuleResources;
import android.os.StrictMode;

import at.jclehner.appopsxposed.util.Constants;
import at.jclehner.appopsxposed.util.Res;
import at.jclehner.appopsxposed.util.Util;
import at.jclehner.appopsxposed.util.XUtils;
import de.robv.android.xposed.IXposedHookInitPackageResources;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_InitPackageResources.InitPackageResourcesParam;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class AppOpsXposed implements IXposedHookZygoteInit, IXposedHookLoadPackage, IXposedHookInitPackageResources
{
	public static final String MODULE_PACKAGE = AppOpsXposed.class.getPackage().getName();
	public static final String SETTINGS_PACKAGE = "com.android.settings";
	public static final String SETTINGS_MAIN_ACTIVITY = SETTINGS_PACKAGE + ".Settings";
	public static final String APP_OPS_FRAGMENT = "com.android.settings.applications.AppOpsSummary";
	public static final String APP_OPS_DETAILS_FRAGMENT = "com.android.settings.applications.AppOpsDetails";

	private String mModPath;

	static
	{
		Util.logger = new Util.Logger() {

			@Override
			public void log(Throwable t)
			{
				XposedBridge.log(t);
			}

			@Override
			public void log(String s)
			{
				XposedBridge.log(s);
			}
		};
	}

	@Override
	public void initZygote(StartupParam startupParam) throws Throwable
	{
		mModPath = startupParam.modulePath;
		Res.modRes = XModuleResources.createInstance(mModPath, null);
		Res.modPrefs = new XSharedPreferences(AppOpsXposed.class.getPackage().getName());
		Res.modPrefs.makeWorldReadable();
	}

	@Override
	public void handleInitPackageResources(InitPackageResourcesParam resparam) throws Throwable
	{
		if(!ApkVariant.isSettingsPackage(resparam.packageName))
			return;

		for(int i = 0; i != Res.icons.length; ++i)
			Res.icons[i] = resparam.res.addResource(Res.modRes, Constants.ICONS[i]);

		XUtils.reloadPrefs();

		for(ApkVariant variant : ApkVariant.getAllMatching(resparam.packageName))
		{
			try
			{
				variant.handleInitPackageResources(resparam);
			}
			catch(Throwable t)
			{
				log(variant.getClass().getSimpleName() + ": [!!]");
				Util.debug(t);
			}

			break;
		}

		for(Hack hack : Hack.getAllEnabled(true))
		{
			try
			{
				hack.handleInitPackageResources(resparam);
			}
			catch(Throwable t)
			{
				log(hack.getClass().getSimpleName() + ": [!!]");
				Util.debug(t);
			}
		}
	}

	@Override
	public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable
	{
		final boolean isSettings = ApkVariant.isSettingsPackage(lpparam);

		if(MODULE_PACKAGE.equals(lpparam.packageName))
		{
			XposedHelpers.findAndHookMethod(Util.class.getName(), lpparam.classLoader,
					"isXposedModuleEnabled", XC_MethodReplacement.returnConstant(true));
		}

		XUtils.reloadPrefs();

		for(Hack hack : Hack.getAllEnabled(true))
		{
			try
			{
				hack.handleLoadPackage(lpparam);
			}
			catch(Throwable t)
			{
				log(hack.getClass().getSimpleName() + ": [!!]");
				Util.debug(t);
			}
		}

		if(!isSettings)
			return;

		Res.settingsRes = XModuleResources.createInstance(lpparam.appInfo.sourceDir, null);

		for(ApkVariant variant : ApkVariant.getAllMatching(lpparam))
		{
			final String variantName = "  " + variant.getClass().getSimpleName();

			try
			{
				variant.handleLoadPackage(lpparam);
				log(variantName + ": [OK]");
				break;
			}
			catch(Throwable t)
			{
				Util.debug(variantName + ": [!!]");
				Util.debug(t);
			}
		}
	}
}
