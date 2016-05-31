/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2013 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */

package flg.mapbox;

import java.util.HashMap;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollProxy;
import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.kroll.common.AsyncResult;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.kroll.common.TiMessenger;

import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.TiBlob;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.TiContext;
import org.appcelerator.titanium.TiDimension;
import org.appcelerator.titanium.proxy.TiViewProxy;
import org.appcelerator.titanium.util.TiConvert;
import org.appcelerator.titanium.view.TiDrawableReference;

import android.graphics.Bitmap;
import android.os.Message;
import android.view.View;

import com.mapbox.mapboxsdk.api.ILatLng;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.overlay.Icon;
import com.mapbox.mapboxsdk.overlay.Marker;

@Kroll.proxy(creatableInModule = MapboxModule.class, propertyAccessors = {
        TiC.PROPERTY_SUBTITLE,
        TiC.PROPERTY_TITLE,
        TiC.PROPERTY_LATITUDE,
        TiC.PROPERTY_LONGITUDE,
        TiC.PROPERTY_IMAGE,
        "showInfoWindow",
        "position",
        "icon",
        "pinColor"
})
public class AnnotationProxy extends KrollProxy {

    public interface AnnotationDelegate {

        public void refreshAnnotation(AnnotationProxy annotation);
    }

    private static final String TAG = "AnnotationProxy";

    private Icon icon;
    private MapboxMarker marker;
    private String annoTitle;
    private String annoSubtitle;
    private AnnotationDelegate delegate = null;

    private static final int MSG_FIRST_ID = KrollProxy.MSG_LAST_ID + 1;

    private static final int MSG_SET_LON = MSG_FIRST_ID + 300;
    private static final int MSG_SET_LAT = MSG_FIRST_ID + 301;
    private static final int MSG_SET_USER_INFO = MSG_FIRST_ID + 304;

    private static final String PROPERTY_SHOW_INFO_WINDOW = "showInfoWindow";

    public static final String PROPERTY_USER_INFO = "userInfo";
    public static final String PROPERTY_ICON = "icon";
    public static final String PROPERTY_PINCOLOR = "pinColor";

    public AnnotationProxy() {

        super();

        annoTitle = "";

        defaultValues.put(PROPERTY_SHOW_INFO_WINDOW, false);
    }

    public AnnotationProxy(TiContext tiContext) {

        this();
    }

    public void setDelegate(AnnotationDelegate delegate) {

        this.delegate = delegate;
    }

    public String getTitle() {

        return annoTitle;
    }

    public String getDescription() {

        return annoSubtitle;
    }

    public String getSubtitle() {

        return annoSubtitle;
    }

    public HashMap getUserInfo() {

        return (HashMap) getProperty(PROPERTY_USER_INFO);
    }

    @Override
    public boolean handleMessage(Message msg) {

        AsyncResult result = null;

        switch (msg.what) {

            case MSG_SET_LON: {
                result = (AsyncResult) msg.obj;
                setPosition(TiConvert.toDouble(getProperty(TiC.PROPERTY_LATITUDE)), (Double) result.getArg());
                result.setResult(null);
                return true;
            }

            case MSG_SET_LAT: {
                result = (AsyncResult) msg.obj;
                setPosition((Double) result.getArg(), TiConvert.toDouble(getProperty(TiC.PROPERTY_LONGITUDE)));
                result.setResult(null);
                return true;
            }

            case MSG_SET_USER_INFO: {

                result = (AsyncResult) msg.obj;
                setUserInfo((HashMap) result.getArg());
                result.setResult(null);
                return true;
            }

            default: {

                return super.handleMessage(msg);
            }
        }
    }

    public void processOptions() {

        double longitude = 0;
        double latitude = 0;

        if (hasProperty(TiC.PROPERTY_LONGITUDE)) {

            longitude = TiConvert.toDouble(getProperty(TiC.PROPERTY_LONGITUDE));
        }

        if (hasProperty(TiC.PROPERTY_LATITUDE)) {

            latitude = TiConvert.toDouble(getProperty(TiC.PROPERTY_LATITUDE));
        }

        LatLng position = new LatLng(latitude, longitude);

        setProperty(TiC.PROPERTY_LATITUDE, latitude);
        setProperty(TiC.PROPERTY_LONGITUDE, longitude);

        if (hasProperty(TiC.PROPERTY_TITLE) || hasProperty(TiC.PROPERTY_SUBTITLE)) {

            if (hasProperty(TiC.PROPERTY_TITLE)) {

                annoTitle = TiConvert.toString(getProperty(TiC.PROPERTY_TITLE));
            }

            if (hasProperty(TiC.PROPERTY_SUBTITLE)) {

                annoSubtitle = TiConvert.toString(getProperty(TiC.PROPERTY_SUBTITLE));
            }
        }

        // image, icon and pincolor must be defined before adding to mapview. Once added, their values are final.
        /*if (hasProperty(TiC.PROPERTY_IMAGE)) {

            handleImage(getProperty(TiC.PROPERTY_IMAGE));
        }*/
    }

    public void setPosition(double latitude, double longitude) {

        LatLng position = new LatLng(latitude, longitude);

        marker.getMarker().setPoint(position);

        setProperty(TiC.PROPERTY_LATITUDE, latitude, true);
        setProperty(TiC.PROPERTY_LONGITUDE, longitude, true);
    }

    public void setUserInfo(HashMap userInfo) {

        if (userInfo instanceof HashMap) {

            setProperty(PROPERTY_USER_INFO, userInfo, true);
        }
    }

    private void handleImage(Object image) {

        // Image path
        if (image instanceof String) {

            TiDrawableReference imageref = TiDrawableReference.fromUrl(this, (String) image);

            Bitmap bitmap = imageref.getBitmap();

            if (bitmap != null) {

//                markerOptions.icon(BitmapDescriptorFactory.fromBitmap(bitmap));

                return;
            }
        }

        // Image blob
        if (image instanceof TiBlob) {

            Bitmap bitmap = ((TiBlob) image).getImage();

            if (bitmap != null) {

//                markerOptions.icon(BitmapDescriptorFactory.fromBitmap(bitmap));

                return;
            }
        }

        Log.w(TAG, "Unable to get the image from the path: " + image);
    }

    public HashMap getMarkerOptions() {

        HashMap <String, Object> markerOptions = new HashMap();

        markerOptions.put(TiC.PROPERTY_TITLE, annoTitle);
        markerOptions.put(TiC.PROPERTY_SUBTITLE, annoSubtitle);
        markerOptions.put(TiC.PROPERTY_LATITUDE, getProperty(TiC.PROPERTY_LATITUDE));
        markerOptions.put(TiC.PROPERTY_LONGITUDE, getProperty(TiC.PROPERTY_LONGITUDE));
        markerOptions.put(PROPERTY_PINCOLOR, getProperty(PROPERTY_PINCOLOR));
        markerOptions.put(PROPERTY_ICON, getProperty(PROPERTY_ICON));

        return markerOptions;
    }

    public void setMapboxMarker(MapboxMarker m) {

        marker = m;
    }

    public MapboxMarker getMapboxMarker() {

        return marker;
    }

    @Override
    public boolean hasProperty(String name) {
        return (super.getProperty(name) != null);
    }

    @Override
    public void onPropertyChanged(String name, Object value) {

        super.onPropertyChanged(name, value);

        if (marker == null || value == null) {

            return;
        }

        if (name.equals(TiC.PROPERTY_LONGITUDE)) {

            TiMessenger.sendBlockingMainMessage(getMainHandler().obtainMessage(MSG_SET_LON), TiConvert.toDouble(value));

        } else if (name.equals(TiC.PROPERTY_LATITUDE)) {

            TiMessenger.sendBlockingMainMessage(getMainHandler().obtainMessage(MSG_SET_LAT), TiConvert.toDouble(value));

        } else if (name.equals(TiC.PROPERTY_TITLE)) {

            String title = TiConvert.toString(value);
            annoTitle = title;

            handleSetTitle(title);

        } else if (name.equals(TiC.PROPERTY_SUBTITLE)) {

            String subtitle = TiConvert.toString(value);
            annoSubtitle = subtitle;

            handleSetSubtitle(subtitle);

        } else if (name.equals(PROPERTY_PINCOLOR)) {

            requestRefresh();
        }
    }

    private void requestRefresh() {

        if (this.delegate != null) {

            this.delegate.refreshAnnotation(this);
        }
    }

    private void handleSetTitle(String title) {

        Marker m = marker.getMarker();

        if (m != null) {

            m.setTitle(title);
        }
    }

    private void handleSetSubtitle(String subtitle) {

        Marker m = marker.getMarker();

        if (m != null) {

            m.setDescription(subtitle);
        }
    }
}