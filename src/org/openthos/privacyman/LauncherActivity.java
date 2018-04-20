/*
 * AppOpsXposed - AppOps for Android 4.3+
 * Copyright (C) 2013 Joseph C. Lehner
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

package org.openthos.privacyman;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import org.openthos.privacyman.util.Util;

public class LauncherActivity extends Activity {
	public static class HtcActivity2 extends Activity {
	}

	public static final String SYSTEM_APK = "/system/" +
			(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT ?
					"priv-app" : "app") + "/OtoPrivacyMan.apk";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		Util.applyTheme(this);
		super.onCreate(savedInstanceState);

		if(checkModuleStatus())
			startActivity(Util.createAppOpsIntent(null));
	}

	// First-launch scenarios:
	//
	//         Running as Xposed module?
	//        /                        \
	//       NO                       YES
	//       |
	//  Xposed Installed?
	//  |               \
	//  YES             NO
	//  |                \
	//                   Install as system app?

	private boolean checkModuleStatus() {
		if(!Util.isXposedModuleEnabled() && !Util.hasAppOpsPermissions(this)) {
			final Intent intent;
			final String message;

			final CharSequence xposedInstallerName = getXposedInstallerName();

			if(xposedInstallerName != null) {
				intent = new Intent("de.robv.android.xposed.installer.OPEN_SECTION");
				intent.putExtra("section", "modules");
				message = getString(R.string.enable_module, xposedInstallerName);
			} else {
				intent = null;
				message = getString(R.string.cannot_enable);
			}

			final OnClickListener l = new OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					if(which == Dialog.BUTTON_POSITIVE && intent != null)
						startActivity(intent);

					finish();
				}
			};

			final AlertDialog.Builder ab = new AlertDialog.Builder(this);
			ab.setCancelable(false);
			ab.setMessage(message);
			ab.setPositiveButton(android.R.string.ok, l);
			if(intent != null)
				ab.setNegativeButton(android.R.string.cancel, l);

			ab.show();
			return false;
		}

		return true;
	}

	private CharSequence getXposedInstallerName() {
		try {
			final ApplicationInfo ai = getPackageManager().getApplicationInfo(
					"de.robv.android.xposed.installer", 0);
			return ai.loadLabel(getPackageManager());
		} catch(PackageManager.NameNotFoundException e) {
			return null;
		}
	}
}
