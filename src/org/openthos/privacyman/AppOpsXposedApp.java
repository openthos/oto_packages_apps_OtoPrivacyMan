package org.openthos.privacyman;

import android.app.Application;

import org.openthos.privacyman.util.Util;

public class AppOpsXposedApp extends Application
{
	@Override
	public void onCreate()
	{
		super.onCreate();
		Util.fixPreferencePermissions();
	}
}
