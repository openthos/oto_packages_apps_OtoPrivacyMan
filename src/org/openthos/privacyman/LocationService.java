package org.openthos.privacyman;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;

import org.openthos.privacyman.util.Util;

public class LocationService extends Service {

    private static final int MSG_LOCATION_FAKE = 1;
    private static final String FAKE_LOCATION_LATITUDE = "25.7718800000";
    private static final String FAKE_LOCATION_LONGITUDE = "123.5291300000";
    private static final String LATITUDE = "latitude";
    private static final String LONGITUDE = "longitude";
    private static final String FAKE_LOCATION = "fakeLocation";

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                String[] location = Util.getLocation();
                String latitude = null;
                String longitude = null;
                if (location.length > 1) {
                    latitude = location[0].trim();
                    longitude = location[1].trim();
                }
                if (latitude == null || longitude == null) {
                    latitude = FAKE_LOCATION_LATITUDE;
                    longitude = FAKE_LOCATION_LONGITUDE;
                }

                Intent intent = new Intent(Intent.ACTION_FAKE_LOCATION);
                Bundle bundle = new Bundle();
                bundle.putString(LATITUDE, latitude);
                bundle.putString(LONGITUDE, longitude);
                intent.putExtra(FAKE_LOCATION, bundle);

                while (true) {
                    try {
                        Thread.sleep(100);
                        Message message = new Message();
                        message.what = MSG_LOCATION_FAKE;
                        message.obj = intent;
                        handler.sendMessage(message);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();
    }

    Handler handler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_LOCATION_FAKE:
                    sendBroadcast((Intent) msg.obj);
                    break;
            }
        }
    };
}
