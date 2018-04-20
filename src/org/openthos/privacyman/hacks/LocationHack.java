package org.openthos.privacyman.hacks;

import android.content.Context;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.openthos.privacyman.Hack;
import org.openthos.privacyman.util.AppOpsManagerWrapper;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class LocationHack extends Hack {

    private static final String TAG = "LocationHack";
    public static final double LATITUDE = 39.862559;
    public static final double LONGITUDE = 116.449535;
    public static final String CLASS_NAME = "android.location.LocationManager";
    public static final String METHOD_NAME = "requestLocationUpdates";
    private Context mContext;

    @Override
    protected void handleLoadFrameworkPackage(XC_LoadPackage.LoadPackageParam lpparam)
            throws Throwable {
        super.handleLoadFrameworkPackage(lpparam);
        if (AppOpsManagerWrapper.OP_GPS == -1 && AppOpsManagerWrapper.OP_COARSE_LOCATION == -1) {
            log("No OP_GPS or OP_COARSE_LOCATION; bailing out!");
            return;
        }

        Class<?> contextClazz = lpparam.classLoader.loadClass("android.content.ContextWrapper");
        hook_method(contextClazz, "getApplicationContext", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (mContext != null) {
                    return;
                }
                mContext = (Context) param.getResult();
                log("Hook method getApplicationContext to get Context");
            }
        });

    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpp) throws Throwable {
        // TODO Auto-generated method stub
        String packageName = lpp.packageName;
        int coarseLocation = Settings.Global.getInt(mContext.getContentResolver(),
                packageName + AppOpsManagerWrapper.OP_COARSE_LOCATION, 0);
        int gpsLocation = Settings.Global.getInt(mContext.getContentResolver(),
                packageName + AppOpsManagerWrapper.OP_GPS, 0);
        if (gpsLocation == 0 && coarseLocation == 0) {
            log("OP_GPS and OP_COARSE_LOCATION is GRANTED");
            return;
        }

        Class<?> targetClazz = lpp.classLoader.loadClass("android.location.Location");

        hook_method(targetClazz, "getLatitude", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                param.setResult(LATITUDE);
            }
        });

        hook_method(targetClazz, "getLongitude", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                param.setResult(LONGITUDE);
            }
        });

        hook_methods(CLASS_NAME, METHOD_NAME, new XC_MethodHook() {
            /**
             * android.location.LocationManager method "requestLocationUpdates"
             * params：
             * String provider, long minTime, float minDistance,LocationListener listener
             * Register for location updates using the named provider, and a pending intent
             */
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {

                if (param.args.length == 4 && (param.args[0] instanceof String)) {
                    //LocationListener, call onLocationChanged, when the position changes
                    LocationListener ll = (LocationListener)param.args[3];
                    fakeLocation(ll);
                }
            }
        });

        hook_methods(CLASS_NAME, METHOD_NAME, new XC_MethodHook() {
            /**
             * android.location.LocationManager method "requestLocationUpdates"
             * params：
             * String provider, LocationListener listener, Looper looper
             * Register for location updates using the named provider, and a pending intent
             */
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {

                if (param.args.length == 3) {
                    //LocationListener, call onLocationChanged, when the position changes
                    LocationListener ll = (LocationListener) param.args[1];
                    fakeLocation(ll);
                }
            }
        });

        hook_methods(CLASS_NAME, METHOD_NAME, new XC_MethodHook() {
            /**
             * android.location.LocationManager method "requestLocationUpdates"
             * params：String provider, long minTime, float minDistance,
             * LocationListener listener, Looper looper
             * Register for location updates using the named provider, and a pending intent
             */
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {

                if (param.args.length == 5 && (param.args[0] instanceof String)) {
                    //LocationListener, call onLocationChanged, when the position changes
                    LocationListener ll = (LocationListener) param.args[3];
                    fakeLocation(ll);
                }
            }
        });

        hook_methods(CLASS_NAME, "getGpsStatus", new XC_MethodHook() {
            /**
             * android.location.LocationManager method "getGpsStatus"
             * params：GpsStatus status
             * Retrieves information about the current status of the GPS engine.
             * This should only be called from the {@link GpsStatus.Listener#onGpsStatusChanged}
             * callback to ensure that the data is copied atomically.
             */
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                GpsStatus gss = (GpsStatus) param.getResult();
                if (gss == null)
                    return;

                Class<?> clazz = GpsStatus.class;
                Method m = null;
                for (Method method : clazz.getDeclaredMethods()) {
                    if (method.getName().equals("setStatus")) {
                        if (method.getParameterTypes().length > 1) {
                            m = method;
                            break;
                        }
                    }
                }

                //access the private setStatus function of GpsStatus
                m.setAccessible(true);

                //make the apps belive GPS works fine now
                int svCount = 5;
                int[] prns = {1, 2, 3, 4, 5};
                float[] snrs = {0, 0, 0, 0, 0};
                float[] elevations = {0, 0, 0, 0, 0};
                float[] azimuths = {0, 0, 0, 0, 0};
                int ephemerisMask = 0x1f;
                int almanacMask = 0x1f;

                //5 satellites are fixed
                int usedInFixMask = 0x1f;

                try {
                    if (m != null) {
                        m.invoke(gss, svCount, prns, snrs, elevations,
                                azimuths, ephemerisMask, almanacMask, usedInFixMask);
                        param.setResult(gss);
                    }
                } catch (Exception e) {
                    XposedBridge.log(e);
                }
            }
        });
    }

    private void fakeLocation(LocationListener ll) {
        Class<?> clazz = LocationListener.class;
        Method m = null;
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.getName().equals("onLocationChanged")) {
                m = method;
                break;
            }
        }

        try {
            if (m != null) {
                //  mSettings.reload();
                Object[] args = new Object[1];
                Location l = new Location(LocationManager.GPS_PROVIDER);

                double la= LATITUDE;
                double lo= LONGITUDE;
                l.setLatitude(la);
                l.setLongitude(lo);
                l.setAccuracy(100f);
                l.setTime(System.currentTimeMillis());
                l.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());

                args[0] = l;

                //invoke onLocationChanged directly to pass location infomation
                m.invoke(ll, args);

                log("requestLocationUpdates----fake location: " + la + ", " + lo);
            }
        } catch (Exception e) {
            XposedBridge.log(e);
        }
    }

    // Hook method without params
    private void hook_method(Class<?> clazz, String methodName,
                             Object... parameterTypesAndCallback) {
        try {
            XposedHelpers.findAndHookMethod(clazz, methodName, parameterTypesAndCallback);
        } catch (Exception e) {
            XposedBridge.log(e);
        }
    }

    // Hook method without params
    private void hook_method(String className, ClassLoader classLoader, String methodName,
                             Object... parameterTypesAndCallback) {
        try {
            XposedHelpers.findAndHookMethod(className, classLoader,
                    methodName, parameterTypesAndCallback);
        } catch (Exception e) {
            XposedBridge.log(e);
        }
    }

    // Hook method with params
    private void hook_methods(String className, String methodName, XC_MethodHook xmh)
    {
        try {
            Class<?> clazz = Class.forName(className);

            for (Method method : clazz.getDeclaredMethods())
                if (method.getName().equals(methodName)
                        && !Modifier.isAbstract(method.getModifiers())
                        && Modifier.isPublic(method.getModifiers())) {
                    XposedBridge.hookMethod(method, xmh);
                }
        } catch (Exception e) {
            XposedBridge.log(e);
        }
    }

    public void log(String s){
        Log.d(TAG, s);
        XposedBridge.log(s);
    }

    @Override
    protected String onGetKeySuffix() {
        return "gps_hook";
    }

    @Override
    protected boolean isEnabledByDefault() {
        return true;
    }
}
