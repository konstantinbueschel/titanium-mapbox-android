package flg.mapbox;

import com.mapbox.mapboxsdk.overlay.Marker;

public class MapboxMarker {

    private Marker marker;
    private AnnotationProxy proxy;

    public MapboxMarker(Marker m, AnnotationProxy p) {
        marker = m;
        proxy = p;
    }

    public void setMarker(Marker m) {
        marker = m;
    }

    public Marker getMarker() {
        return marker;
    }

    public AnnotationProxy getProxy() {
        return proxy;
    }
}
