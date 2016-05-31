package flg.mapbox;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.Iterator;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollProxy;
import org.appcelerator.kroll.common.Log;

import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.view.TiCompositeLayout;
import org.appcelerator.titanium.view.TiUIView;
import org.appcelerator.titanium.proxy.TiViewProxy;
import org.appcelerator.titanium.util.TiConvert;
import org.appcelerator.titanium.util.TiRHelper;
import org.appcelerator.titanium.util.TiRHelper.ResourceNotFoundException;
import org.appcelerator.titanium.util.TiUIHelper;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.graphics.Color;
import android.graphics.Bitmap;

import com.mapbox.mapboxsdk.api.ILatLng;
import com.mapbox.mapboxsdk.geometry.BoundingBox;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.overlay.GpsLocationProvider;
import com.mapbox.mapboxsdk.overlay.Icon;
import com.mapbox.mapboxsdk.overlay.Marker;
import com.mapbox.mapboxsdk.overlay.UserLocationOverlay;
import com.mapbox.mapboxsdk.overlay.UserLocationOverlay.TrackingMode;
import com.mapbox.mapboxsdk.tileprovider.tilesource.*;
import com.mapbox.mapboxsdk.views.util.TilesLoadedListener;
import com.mapbox.mapboxsdk.views.MapView;
import com.mapbox.mapboxsdk.views.MapViewListener;
import com.mapbox.mapboxsdk.events.*;
import com.mapbox.mapboxsdk.views.util.Projection;


public class MapboxMapView extends TiUIView implements MapViewListener, MapListener {

    // Standard Debugging variables
    private static final String LCAT = "MapboxModule";

    // Dictionary key names
    public static final String PROPERTY_USER_LOCATION = "userLocation";
    public static final String PROPERTY_MAP_ORIENTATION = "mapOrientation";
    public static final String PROPERTY_USER_TRACKING_MODE = "userTrackingMode";
    public static final String PROPERTY_BACKGROUND_COLOR = "backgroundColor";
    public static final String PROPERTY_MIN_ZOOM_LEVEL = "minZoom";
    public static final String PROPERTY_MAX_ZOOM_LEVEL = "maxZoom";
    public static final String PROPERTY_ZOOM = "zoom";

    private static final String PROPERTY_ANNOTATION_TITLE = "title";
    private static final String PROPERTY_ANNOTATION_SUBTITLE = "subtitle";

    private static final String PROPERTY_MAP = "map";
    private static final String PROPERTY_ACCESS_TOKEN = "accessToken";
    private static final String PROPERTY_DEBUG_MODE = "debugMode";

    private MapView map;

    protected ArrayList<MapboxMarker> mapboxMarkers;
    protected AnnotationProxy selectedAnnotation;

    private UserLocationOverlay userLocationOverlay;

    public MapboxMapView(TiViewProxy proxy) {

        super(proxy);

        Log.d(LCAT, "[VIEW LIFECYCLE EVENT] view");

        mapboxMarkers = new ArrayList<MapboxMarker>();

        LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        LinearLayout holder = new LinearLayout(proxy.getActivity());
        holder.setLayoutParams(lp);

        map = new MapView(proxy.getActivity());
        map.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        holder.addView(map);

        map.setMapViewListener(this);
        map.addListener(this);

        this.setNativeView(holder);
    }

    // The view is automatically registered as a model listener when the view
    // is realized by the view proxy. That means that the processProperties
    // method will be called during creation and that propertiesChanged and
    // propertyChanged will be called when properties are changed on the proxy.
    @Override
    public void processProperties(KrollDict props) {

        super.processProperties(props);

        Log.d(LCAT, "[VIEW LIFECYCLE EVENT] processProperties " + props);

        if (props.containsKey(PROPERTY_ACCESS_TOKEN)) {

            map.setAccessToken(TiConvert.toString(props, PROPERTY_ACCESS_TOKEN));
        }

        if (props.containsKey(TiC.PROPERTY_REGION)) {

            HashMap regionDict = (HashMap) props.get(TiC.PROPERTY_REGION);
            setRegion(regionDict);
        }

        if (props.containsKey(PROPERTY_USER_LOCATION)) {

            Object userLocationFlag = props.get(PROPERTY_USER_LOCATION);
            Boolean show = TiConvert.toBoolean(userLocationFlag);
            setUserLocation(show);
        }

        if (props.containsKey(TiC.PROPERTY_ANNOTATIONS)) {

            Object[] annotations = (Object[]) props.get(TiC.PROPERTY_ANNOTATIONS);
            addAnnotations(annotations);
        }

        if (props.containsKey(PROPERTY_MAP)) {

            String mapName = TiConvert.toString(props, PROPERTY_MAP);

            mapName += ".mbtiles";

            TileLayer mbTileLayer = new MBTilesLayer(proxy.getActivity(), mapName);
            map.setTileSource(new ITileLayer[]{mbTileLayer});

            map.setScrollableAreaLimit(mbTileLayer.getBoundingBox());

            map.setMinZoomLevel(map.getTileProvider().getMinimumZoomLevel());
            map.setMaxZoomLevel(map.getTileProvider().getMaximumZoomLevel());

            map.setCenter(map.getTileProvider().getCenterCoordinate());
        }

        if (props.containsKey(PROPERTY_DEBUG_MODE)) {

            map.setDebugMode(TiConvert.toBoolean(props, PROPERTY_DEBUG_MODE, false));
        }

        if (props.containsKey(PROPERTY_MIN_ZOOM_LEVEL)) {

            map.setMinZoomLevel(TiConvert.toFloat(props, PROPERTY_MIN_ZOOM_LEVEL, map.getTileProvider().getMinimumZoomLevel()));
        }

        if (props.containsKey(PROPERTY_MAX_ZOOM_LEVEL)) {

            map.setMaxZoomLevel(TiConvert.toFloat(props, PROPERTY_MAX_ZOOM_LEVEL, map.getTileProvider().getMaximumZoomLevel()));
        }

        if (props.containsKey(PROPERTY_ZOOM)) {

            map.setZoom(TiConvert.toFloat(props, PROPERTY_ZOOM, map.getTileProvider().getCenterZoom()));
        }
    }

    @Override
    public void propertyChanged(String key, Object oldValue, Object newValue, KrollProxy proxy) {

        // This method is called whenever a proxy property value is updated. Note that this
        // method is only called if the new value is different than the current value.
        super.propertyChanged(key, oldValue, newValue, proxy);


        Log.d(LCAT, "[VIEW LIFECYCLE EVENT] propertyChanged: " + key + ' ' + oldValue + ' ' + newValue);


        if (map == null) {

            return;
        }

        if (key.equals(TiC.PROPERTY_REGION)) {

            setRegion((HashMap) newValue);
        }

        if (key.equals(PROPERTY_USER_LOCATION)) {

            setUserLocation(TiConvert.toBoolean(newValue));
        }

        if (key.equals(PROPERTY_USER_TRACKING_MODE)) {

            setUserLocationTrackingMode(TiConvert.toInt(newValue, 0));
        }

        if (key.equals(PROPERTY_BACKGROUND_COLOR)) {

            setBackgroundColor(TiConvert.toInt(newValue));
        }

        if (key.equals(PROPERTY_DEBUG_MODE)) {

            map.setDebugMode(TiConvert.toBoolean(newValue, false));
        }

        if (key.equals(PROPERTY_MIN_ZOOM_LEVEL)) {

            map.setMinZoomLevel(TiConvert.toFloat(newValue, map.getTileProvider().getMinimumZoomLevel()));
        }

        if (key.equals(PROPERTY_MAX_ZOOM_LEVEL)) {

            map.setMaxZoomLevel(TiConvert.toFloat(newValue, map.getTileProvider().getMaximumZoomLevel()));
        }

        if (key.equals(PROPERTY_ZOOM)) {

            map.setZoom(TiConvert.toFloat(newValue, map.getTileProvider().getCenterZoom()));
        }
    }

    public MapView getMap() {

        return map;
    }

    public float getMaxZoomLevel() {

        return map.getMaxZoomLevel();
    }

    public float getMinZoomLevel() {

        return map.getMinZoomLevel();
    }

    protected void addAnnotation(AnnotationProxy annotation) {

        // if annotation already on map, remove it first then re-add it
        MapboxMarker mapboxMarker = annotation.getMapboxMarker();

        if (mapboxMarker != null) {

            removeAnnotation(mapboxMarker);
        }

        annotation.processOptions();

        HashMap markerOptions = (HashMap) annotation.getMarkerOptions();


        // add annotation to map view
        Marker marker = new Marker(map, (String) markerOptions.get(TiC.PROPERTY_TITLE), (String) markerOptions.get(TiC.PROPERTY_SUBTITLE), new LatLng((Double) markerOptions.get(TiC.PROPERTY_LATITUDE), (Double) markerOptions.get(TiC.PROPERTY_LONGITUDE)));

        if (markerOptions.get(AnnotationProxy.PROPERTY_ICON) != null && markerOptions.get(AnnotationProxy.PROPERTY_PINCOLOR) != null) {

            marker.setIcon(new Icon(proxy.getActivity(), Icon.Size.MEDIUM, (String) markerOptions.get(AnnotationProxy.PROPERTY_ICON), (String) markerOptions.get(AnnotationProxy.PROPERTY_PINCOLOR)));
        }


        Log.d(LCAT, "Created marker:" + marker);


        map.addMarker(marker);

        mapboxMarker = new MapboxMarker(marker, annotation);

        annotation.setMapboxMarker(mapboxMarker);

        mapboxMarkers.add(mapboxMarker);
    }

    protected void addAnnotations(Object[] annotations) {

        for (int i = 0; i < annotations.length; i++) {

            Object obj = annotations[i];

            if (obj instanceof AnnotationProxy) {

                AnnotationProxy annotation = (AnnotationProxy) obj;

                addAnnotation(annotation);
            }
        }
    }

    protected void updateAnnotations(Object[] annotations) {

        // First, remove old annotations from map
        removeAllAnnotations();

        // Then we add new annotations to the map
        addAnnotations(annotations);
    }

    protected void removeAllAnnotations() {

        for (int i = 0; i < mapboxMarkers.size(); i++) {

            MapboxMarker mapboxMarker = mapboxMarkers.get(i);

            AnnotationProxy proxy = mapboxMarker.getProxy();

            if (proxy != null) {

                proxy.setMapboxMarker(null);
            }
        }

        mapboxMarkers.clear();

        map.clear();
    }

    public MapboxMarker findMarkerByTitle(String title) {

        for (int i = 0; i < mapboxMarkers.size(); i++) {

            MapboxMarker mapboxMarker = mapboxMarkers.get(i);

            AnnotationProxy annoProxy = mapboxMarker.getProxy();

            if (annoProxy != null && annoProxy.getTitle().equals(title)) {

                return mapboxMarker;
            }
        }

        return null;
    }

    protected void removeAnnotation(Object annotation) {

        MapboxMarker mapboxMarker = null;

        if (annotation instanceof MapboxMarker) {

            mapboxMarker = (MapboxMarker) annotation;

        } else if (annotation instanceof AnnotationProxy) {

            mapboxMarker = ((AnnotationProxy) annotation).getMapboxMarker();

        } else if (annotation instanceof String) {

            mapboxMarker = findMarkerByTitle((String) annotation);
        }

        if (mapboxMarker != null && mapboxMarkers.remove(mapboxMarker)) {

            map.removeMarker(mapboxMarker.getMarker());

            AnnotationProxy proxy = mapboxMarker.getProxy();

            if (proxy != null) {

                if (selectedAnnotation != null && proxy.equals(selectedAnnotation)) {

                    selectedAnnotation = null;
                }

                proxy.setMapboxMarker(null);
            }
        }
    }

    protected void selectAnnotation(Object annotation) {

        if (annotation instanceof AnnotationProxy) {

            AnnotationProxy proxy = (AnnotationProxy) annotation;

            MapboxMarker mapboxMarker = proxy.getMapboxMarker();

            if (mapboxMarker != null) {

                Marker marker = mapboxMarker.getMarker();

                map.selectMarker(marker);

                selectedAnnotation = proxy;
            }

        } else if (annotation instanceof String) {

            String title = (String) annotation;

            MapboxMarker mapboxMarker = findMarkerByTitle(title);

            if (mapboxMarker != null) {

                Marker marker = mapboxMarker.getMarker();

                map.selectMarker(marker);

                selectedAnnotation = mapboxMarker.getProxy();
            }
        }
    }

    protected void deselectAnnotation(Object annotation) {

        if (annotation instanceof AnnotationProxy) {

            AnnotationProxy proxy = (AnnotationProxy) annotation;

            if (proxy.getMapboxMarker() != null) {

                map.clearMarkerFocus();

                if (selectedAnnotation != null && proxy.equals(selectedAnnotation)) {

                    selectedAnnotation = null;
                }

                fireDeselectAnnotationEvent(map, proxy.getMapboxMarker().getMarker());
            }

        } else if (annotation instanceof String) {

            String title = (String) annotation;

            MapboxMarker mapboxMarker = findMarkerByTitle(title);

            if (mapboxMarker != null) {

                map.clearMarkerFocus();

                if (selectedAnnotation != null && mapboxMarker.getProxy() != null && mapboxMarker.getProxy().equals(selectedAnnotation)) {

                    selectedAnnotation = null;
                }

                fireDeselectAnnotationEvent(map, mapboxMarker.getMarker());
            }
        }

        selectedAnnotation = null;
    }

    private AnnotationProxy getProxyByMarker(Marker m) {

        if (m != null) {

            for (int i = 0; i < mapboxMarkers.size(); i++) {

                MapboxMarker mapboxMarker = mapboxMarkers.get(i);

                if (m.equals(mapboxMarker.getMarker())) {

                    return mapboxMarker.getProxy();
                }
            }
        }

        return null;
    }

    // TODO: implement this one here
    public void changeZoomLevel(int delta) {

//        CameraUpdate camUpdate = CameraUpdateFactory.zoomBy(delta);
//        moveCamera(camUpdate, animate);
    }

    @Override
    public void release() {

        selectedAnnotation = null;

        if (map != null) {

            map.clear();
        }

        map = null;

        mapboxMarkers.clear();

        super.release();
    }


    // Setter method called by the proxy when the property is
    // set. This could also be handled in the propertyChanged handler.
    public void setRegion(final HashMap regionDict) {

        float latitude = 0;
        float longitude = 0;
        boolean animated = true;

        if (regionDict.containsKey(TiC.PROPERTY_LATITUDE)) {

            latitude = TiConvert.toFloat(regionDict, TiC.PROPERTY_LATITUDE);
        }

        if (regionDict.containsKey(TiC.PROPERTY_LONGITUDE)) {

            longitude = TiConvert.toFloat(regionDict, TiC.PROPERTY_LONGITUDE);
        }

        if (regionDict.containsKey(TiC.PROPERTY_ANIMATED)) {

            animated = TiConvert.toBoolean(regionDict, TiC.PROPERTY_ANIMATED, false);
        }

        ILatLng latlong = new LatLng(latitude, longitude);

        if (animated) {

            map.getController().animateTo(latlong);
        }
        else {

            map.setCenter(latlong);
        }
    }

    public void setZoom(final HashMap regionDict) {

        float latitudeDelta = 0;
        float longitudeDelta = 0;

        Boolean hasZoomInfo = false;

        if (regionDict.containsKey(TiC.PROPERTY_LATITUDE_DELTA)) {

            latitudeDelta = (TiConvert.toFloat(regionDict, TiC.PROPERTY_LATITUDE_DELTA));
            hasZoomInfo = true;

        }
        if (regionDict.containsKey(TiC.PROPERTY_LONGITUDE_DELTA)) {

            longitudeDelta = (TiConvert.toFloat(regionDict, TiC.PROPERTY_LONGITUDE_DELTA));
            hasZoomInfo = true;
        }

        if (hasZoomInfo) {

            map.setZoom(convertCoordinatesDeltaToZoomLevel(latitudeDelta, longitudeDelta));
        }
    }

    public void setZoom(float zoom) {

        map.setZoom(zoom);
    }

    public void setMinZoomLevel(float minZoomLevel) {

        map.setMinZoomLevel(minZoomLevel);
    }

    public void setMaxZoomLevel(float maxZoomLevel) {

        map.setMaxZoomLevel(maxZoomLevel);
    }

    public void setUserLocation(final Boolean flag) {

        if (flag) {

            addLocationOverlay();

            userLocationOverlay.enableMyLocation();

            userLocationOverlay.setRequiredZoom(6);

        } else {

            removeLocationOverlay();
        }
    }

    private void addLocationOverlay() {

        removeLocationOverlay();

        userLocationOverlay = new UserLocationOverlay(new GpsLocationProvider(proxy.getActivity()), map);

        userLocationOverlay.setDrawAccuracyEnabled(true);

        int personId = 0;

        try {

            personId = TiRHelper.getApplicationResource("drawable.location_marker");

        } catch (ResourceNotFoundException e) {

            Log.d(LCAT, "addLocationOverlay - person - RESOURCE NOT FOUND! Exception:" + e);
        }

        if (personId != 0) {

            Bitmap personBitmap = TiUIHelper.getResourceBitmap(personId);

            userLocationOverlay.setPersonBitmap(personBitmap);
        }

        int arrowId = 0;

        try {

            arrowId = TiRHelper.getApplicationResource("drawable.direction_arrow");

        } catch (ResourceNotFoundException e) {

            Log.d(LCAT, "addLocationOverlay - direction arrow - RESOURCE NOT FOUND! Exception:" + e);
        }

        if (arrowId != 0) {

            Bitmap arrowBitmap = TiUIHelper.getResourceBitmap(arrowId);

            userLocationOverlay.setDirectionArrowBitmap(arrowBitmap);
        }

        map.addOverlay(userLocationOverlay);
    }

    private void removeLocationOverlay() {

        if (userLocationOverlay != null) {

            userLocationOverlay.disableMyLocation();

            map.removeOverlay(userLocationOverlay);

            userLocationOverlay = null;
        }
    }

    private float convertCoordinatesDeltaToZoomLevel(float latitudeDelta, float longitudeDelta) {

        float averageDelta = (latitudeDelta + longitudeDelta) / 2;

        // Set the zoom level according to the passed lat/lng deltas using the table at
        // http://wiki.openstreetmap.org/wiki/Zoom_levels
        float zoomLevel = map.getTileProvider().getCenterZoom();

        if (averageDelta <= 0) {

            zoomLevel = 20;

        } else if (averageDelta < 0.0005) {

            zoomLevel = 19;

        } else if (averageDelta < 0.001) {

            zoomLevel = 18;

        } else if (averageDelta < 0.003) {

            zoomLevel = 17;

        } else if (averageDelta < 0.005) {

            zoomLevel = 16;

        } else if (averageDelta < 0.011) {

            zoomLevel = 15;

        } else if (averageDelta < 0.022) {

            zoomLevel = 14;

        } else if (averageDelta < 0.044) {

            zoomLevel = 13;

        } else if (averageDelta < 0.088) {

            zoomLevel = 12;

        } else if (averageDelta < 0.176) {

            zoomLevel = 11;

        } else if (averageDelta < 0.352) {

            zoomLevel = 10;

        } else if (averageDelta < 0.703) {

            zoomLevel = 9;

        } else if (averageDelta < 1.406) {

            zoomLevel = 8;

        } else if (averageDelta < 2.813) {

            zoomLevel = 7;

        } else if (averageDelta < 5.625) {

            zoomLevel = 6;

        } else if (averageDelta < 11.25) {

            zoomLevel = 5;

        } else if (averageDelta < 22.5) {

            zoomLevel = 4;

        } else if (averageDelta < 45) {

            zoomLevel = 3;

        } else if (averageDelta < 90) {

            zoomLevel = 2;

        } else if (averageDelta < 180) {

            zoomLevel = 1;

        } else if (averageDelta < 360) {

            zoomLevel = 0;
        }

        return zoomLevel;
    }

    public void setMapOrientation(float orientation) {

        map.setMapOrientation(orientation);
    }

    public void goToUserLocation() {

        if (userLocationOverlay != null) {

            userLocationOverlay.goToMyPosition(true);
        }
    }

    public void setUserLocationTrackingMode(int userLocationTrackingMode) {

        if (userLocationOverlay != null) {

            switch (userLocationTrackingMode) {

                case 0: {

                    userLocationOverlay.setTrackingMode(UserLocationOverlay.TrackingMode.NONE);
                    break;
                }

                case 1: {

                    userLocationOverlay.setTrackingMode(UserLocationOverlay.TrackingMode.FOLLOW);
                    break;
                }

                case 2: {

                    userLocationOverlay.setTrackingMode(UserLocationOverlay.TrackingMode.FOLLOW_BEARING);
                    break;
                }

                default: {

                    userLocationOverlay.setTrackingMode(UserLocationOverlay.TrackingMode.NONE);
                    break;
                }
            }

        }
    }

    public int getUserLocationTrackingMode() {

        if (userLocationOverlay != null) {

            UserLocationOverlay.TrackingMode trackingMode = userLocationOverlay.getTrackingMode();

            switch (trackingMode) {

                case NONE: {

                    return 0;
                }

                case FOLLOW: {

                    return 1;
                }

                case FOLLOW_BEARING: {

                    return 2;
                }

                default: {

                    return 0;
                }
            }
        }

        return 0;
    }

    public void setBackgroundColor(int backgroundColor) {

        map.setBackgroundColor((Integer) backgroundColor);
    }

    private void fireSelectAnnotationEvent(MapView mapView, Marker marker) {

        // It is a good idea to check if there are listeners for the event that
        // is about to be fired. There could be zero or multiple listeners for the
        // specified event.
        if (proxy.hasListeners("selectAnnotation")) {

            AnnotationProxy annotation = getProxyByMarker(marker);

            LatLng markerPosition = marker.getPosition();

            KrollDict event = new KrollDict();

            event.put("annotation", annotation);
            event.put("userInfo", annotation.getUserInfo());
            event.put("latitude", markerPosition.getLatitude());
            event.put("longitude", markerPosition.getLongitude());

            proxy.fireEvent("selectAnnotation", event);
        }
    }

    private void fireDeselectAnnotationEvent(MapView mapView, Marker marker) {

        if (proxy.hasListeners("deselectAnnotation")) {

            AnnotationProxy annotation = getProxyByMarker(marker);

            LatLng markerPosition = marker.getPosition();

            KrollDict event = new KrollDict();

            event.put("annotation", annotation);
            event.put("userInfo", annotation.getUserInfo());
            event.put("latitude", markerPosition.getLatitude());
            event.put("longitude", markerPosition.getLongitude());

            proxy.fireEvent("deselectAnnotation", event);
        }
    }

    @Override
    public void onShowMarker(MapView pMapView, Marker pMarker) {

        fireSelectAnnotationEvent(pMapView, pMarker);
    }

    @Override
    public void onHideMarker(MapView pMapView, Marker pMarker) {

        fireDeselectAnnotationEvent(pMapView, pMarker);
    }

    @Override
    public void onTapMarker(MapView pMapView, Marker pMarker) {

        AnnotationProxy annotation = getProxyByMarker(pMarker);

        if (proxy.hasListeners("tapOnAnnotation")) {

            LatLng markerPosition = pMarker.getPosition();

            KrollDict event = new KrollDict();

            event.put("annotation", annotation);
            event.put("userInfo", annotation.getUserInfo());
            event.put("latitude", markerPosition.getLatitude());
            event.put("longitude", markerPosition.getLongitude());

            proxy.fireEvent("tapOnAnnotation", event);
        }

        if (selectedAnnotation != null) {

            if (!selectedAnnotation.equals(annotation)) {

                fireDeselectAnnotationEvent(pMapView, selectedAnnotation.getMapboxMarker().getMarker());

                selectedAnnotation = annotation;

                fireSelectAnnotationEvent(pMapView, annotation.getMapboxMarker().getMarker());
            }
        } else {

            selectedAnnotation = annotation;

            fireSelectAnnotationEvent(pMapView, annotation.getMapboxMarker().getMarker());
        }

//        map.getController().animateTo(pMarker.getPoint());
    }

    @Override
    public void onTapMap(MapView pMapView, ILatLng pPosition) {

        if (proxy.hasListeners("singleTapOnMap")) {

            KrollDict event = new KrollDict();

            event.put("annoation", null);
            event.put("latitude", pPosition.getLatitude());
            event.put("longitude", pPosition.getLongitude());

            proxy.fireEvent("singleTapOnMap", event);
        }
    }

    @Override
    public void onLongPressMap(MapView pMapView, ILatLng pPosition) {

        if (proxy.hasListeners("longPressOnMap")) {

            String coords = String.format("Original Lat = %f, Lon = %f", pPosition.getLatitude(), pPosition.getLongitude());

            float[] rc = {(float) pPosition.getLatitude(), (float) pPosition.getLongitude()};

            Projection p = pMapView.getProjection();

            p.rotatePoints(rc);

            ILatLng rotLatLon = p.fromPixels(rc[0], rc[1]);

            String rotCoords = String.format("Rotated Lat = %f, Lon = %f", rotLatLon.getLatitude(), rotLatLon.getLongitude());

            Log.i("TapForUTFGridTestFragment", String.format("coords = '%s', rotated coords = '%s'", coords, rotCoords));

            KrollDict event = new KrollDict();

            event.put("annoation", null);
            event.put("latitude", rotLatLon.getLatitude());
            event.put("longitude", rotLatLon.getLongitude());

            proxy.fireEvent("longPressOnMap", event);
        }
    }

    @Override
    public void onLongPressMarker(MapView pMapView, Marker pMarker) {

        if (proxy.hasListeners("longPressOnAnnotation")) {

            AnnotationProxy annotation = getProxyByMarker(pMarker);
            LatLng markerPosition = pMarker.getPosition();

            KrollDict event = new KrollDict();

            event.put("annotation", annotation);
            event.put("userInfo", annotation.getUserInfo());
            event.put("latitude", markerPosition.getLatitude());
            event.put("longitude", markerPosition.getLongitude());

            proxy.fireEvent("longPressOnAnnotation", event);
        }
    }

    @Override
    public void onScroll(ScrollEvent event) {

        if (proxy.hasListeners("beforeMapMove") || proxy.hasListeners("scroll")) {

            KrollDict tiEvent = new KrollDict();

            tiEvent.put("wasUserAction", event.getUserAction());

            proxy.fireEvent("beforeMapMove", tiEvent);
            proxy.fireEvent("scroll", tiEvent);
        }
    }

    @Override
    public void onRotate(RotateEvent event) {

        if (proxy.hasListeners("rotate")) {

            KrollDict tiEvent = new KrollDict();

            tiEvent.put("wasUserAction", event.getUserAction());
            tiEvent.put("angle", event.getAngle());

            proxy.fireEvent("rotate", tiEvent);
        }
    }

    @Override
    public void onZoom(ZoomEvent event) {

        if (proxy.hasListeners("zoom")) {

            KrollDict tiEvent = new KrollDict();

            tiEvent.put("wasUserAction", event.getUserAction());
            tiEvent.put("zoomLevel", event.getZoomLevel());

            proxy.fireEvent("zoom", tiEvent);
        }
    }
}
