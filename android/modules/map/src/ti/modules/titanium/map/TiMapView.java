/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2010 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */

package ti.modules.titanium.map;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollProxy;
import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.TiContext.OnLifecycleEvent;
import org.appcelerator.titanium.TiProperties;
import org.appcelerator.titanium.io.TiBaseFile;
import org.appcelerator.titanium.io.TiFileFactory;
import org.appcelerator.titanium.proxy.TiViewProxy;
import org.appcelerator.titanium.util.AsyncResult;
import org.appcelerator.titanium.util.Log;
import org.appcelerator.titanium.util.TiConfig;
import org.appcelerator.titanium.util.TiConvert;
import org.appcelerator.titanium.util.TiUIHelper;
import org.appcelerator.titanium.view.TiUIView;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.widget.Toast;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.ItemizedOverlay;
import com.google.android.maps.MapController;
import com.google.android.maps.MapView;
import com.google.android.maps.MyLocationOverlay;
import com.google.android.maps.Overlay;

interface TitaniumOverlayListener {
	public void onTap(int index);
}

public class TiMapView extends TiUIView
	implements Handler.Callback, TitaniumOverlayListener
{
	private static final String LCAT = "TiMapView";
	private static final boolean DBG = TiConfig.LOGD;

	private static final String TI_DEVELOPMENT_KEY = "0ZnKXkWA2dIAu2EM-OV4ZD2lJY3sEWE5TSgjJNg";
	private static final String OLD_API_KEY = "ti.android.google.map.api.key";
	private static final String DEVELOPMENT_API_KEY = "ti.android.google.map.api.key.development";
	private static final String PRODUCTION_API_KEY = "ti.android.google.map.api.key.production";

	public static final int MAP_VIEW_STANDARD = 1;
	public static final int MAP_VIEW_SATELLITE = 2;
	public static final int MAP_VIEW_HYBRID = 3;

	private static final int MSG_SET_LOCATION = 300;
	private static final int MSG_SET_MAPTYPE = 301;
	private static final int MSG_SET_REGIONFIT = 302;
	private static final int MSG_SET_ANIMATE = 303;
	private static final int MSG_SET_USERLOCATION = 304;
	private static final int MSG_SET_SCROLLENABLED = 305;
	private static final int MSG_CHANGE_ZOOM = 306;
	private static final int MSG_ADD_ANNOTATION = 307;
	private static final int MSG_REMOVE_ANNOTATION = 308;
	private static final int MSG_SELECT_ANNOTATION = 309;
	private static final int MSG_REMOVE_ALL_ANNOTATIONS = 310;
	private static final int MSG_UPDATE_ANNOTATIONS = 311;

	private boolean scrollEnabled;
	private boolean regionFit;
	private boolean animate;
	private boolean userLocation;

	private LocalMapView view;
	private Window mapWindow;
	private TitaniumOverlay overlay;
	private MyLocationOverlay myLocation;
	private TiOverlayItemView itemView;
	private ArrayList<AnnotationProxy> annotations;
	private ArrayList<SelectedAnnotation> selectedAnnotations;
	private Handler handler;

	class LocalMapView extends MapView
	{
		private boolean scrollEnabled;
		private int lastLongitude;
		private int lastLatitude;
		private int lastLatitudeSpan;
		private int lastLongitudeSpan;

		public LocalMapView(Context context, String apiKey) {
			super(context, apiKey);
			scrollEnabled = false;
		}

		public void setScrollable(boolean enable) {
			scrollEnabled = enable;
		}

		@Override
		public boolean dispatchTouchEvent(MotionEvent ev) {
			if (!scrollEnabled && ev.getAction() == MotionEvent.ACTION_MOVE) {
				return true;
			}
			return super.dispatchTouchEvent(ev);
		}

		@Override
		public boolean dispatchTrackballEvent(MotionEvent ev) {
			if (!scrollEnabled && ev.getAction() == MotionEvent.ACTION_MOVE) {
				return true;
			}
			return super.dispatchTrackballEvent(ev);
		}

		@Override
		public void computeScroll() {
			super.computeScroll();
			GeoPoint center = getMapCenter();
			if (lastLatitude != center.getLatitudeE6() || lastLongitude != center.getLongitudeE6() ||
					lastLatitudeSpan != getLatitudeSpan() || lastLongitudeSpan != getLongitudeSpan())
			{
				lastLatitude = center.getLatitudeE6();
				lastLongitude = center.getLongitudeE6();
				lastLatitudeSpan = getLatitudeSpan();
				lastLongitudeSpan = getLongitudeSpan();

				KrollDict d = new KrollDict();
				d.put(TiC.PROPERTY_LATITUDE, scaleFromGoogle(lastLatitude));
				d.put(TiC.PROPERTY_LONGITUDE, scaleFromGoogle(lastLongitude));
				d.put(TiC.PROPERTY_LATITUDE_DELTA, scaleFromGoogle(lastLatitudeSpan));
				d.put(TiC.PROPERTY_LONGITUDE_DELTA, scaleFromGoogle(lastLongitudeSpan));
				proxy.fireEvent(TiC.EVENT_REGION_CHANGED, d);
			}
		}
	}

	class TitaniumOverlay extends ItemizedOverlay<TiOverlayItem>
	{
		ArrayList<AnnotationProxy> annotations;
		TitaniumOverlayListener listener;

		public TitaniumOverlay(Drawable defaultDrawable, TitaniumOverlayListener listener) {
			super(defaultDrawable);
			this.listener = listener;
		}

		public void setAnnotations(ArrayList<AnnotationProxy> annotations) {
			this.annotations = new ArrayList<AnnotationProxy>(annotations);
			populate();
		}

		@Override
		protected TiOverlayItem createItem(int i) {
			TiOverlayItem item = null;

			AnnotationProxy p = annotations.get(i);
			KrollDict a = p.getProperties();
			if (a.containsKey(TiC.PROPERTY_LATITUDE) && a.containsKey(TiC.PROPERTY_LONGITUDE)) {
				String title = a.optString(TiC.PROPERTY_TITLE, "");
				String subtitle = a.optString(TiC.PROPERTY_SUBTITLE, "");

				GeoPoint location = new GeoPoint(scaleToGoogle(a.getDouble(TiC.PROPERTY_LATITUDE)), scaleToGoogle(a.getDouble(TiC.PROPERTY_LONGITUDE)));
				item = new TiOverlayItem(location, title, subtitle, p);

				//prefer pinImage to pincolor.
				if (a.containsKey(TiC.PROPERTY_IMAGE) || a.containsKey(TiC.PROPERTY_PIN_IMAGE))
				{
					String imagePath = a.getString(TiC.PROPERTY_IMAGE);
					if (imagePath == null) {
						imagePath = a.getString(TiC.PROPERTY_PIN_IMAGE);
					}
					Drawable marker = makeMarker(imagePath);
					boundCenterBottom(marker);
					item.setMarker(marker);
				} else if (a.containsKey(TiC.PROPERTY_PINCOLOR)) {
					Object value = a.get(TiC.PROPERTY_PINCOLOR);
					
					try {
						if (value instanceof String) {
							
							// Supported strings: Supported formats are: 
							//     #RRGGBB #AARRGGBB 'red', 'blue', 'green', 'black', 'white', 'gray', 'cyan', 'magenta', 'yellow', 'lightgray', 'darkgray'
							int markerColor = TiConvert.toColor((String) value);
							item.setMarker(makeMarker(markerColor));
						} else {
							// Assume it's a numeric
							switch(a.getInt(TiC.PROPERTY_PINCOLOR)) {
								case 1 : // RED
									item.setMarker(makeMarker(Color.RED));
									break;
								case 2 : // GREEN
									item.setMarker(makeMarker(Color.GREEN));
									break;
								case 3 : // PURPLE
									item.setMarker(makeMarker(Color.argb(255,192,0,192)));
									break;
							}
						}
					} catch (Exception e) {
						// May as well catch all errors 
						Log.w(LCAT, "Unable to parse color [" + a.getString(TiC.PROPERTY_PINCOLOR)+"] for item ["+i+"]");
					}
				}

				if (a.containsKey(TiC.PROPERTY_LEFT_BUTTON)) {
					item.setLeftButton(proxy.getTiContext().resolveUrl(null, TiConvert.toString(a, TiC.PROPERTY_LEFT_BUTTON)));
				}
				if (a.containsKey(TiC.PROPERTY_RIGHT_BUTTON)) {
					item.setRightButton(proxy.getTiContext().resolveUrl(null, TiConvert.toString(a, TiC.PROPERTY_RIGHT_BUTTON)));
				}
			} else {
				Log.w(LCAT, "Skipping annotation: No coordinates #" + i);
			}
			return item;
		}

		@Override
		public int size() {
			return (annotations == null) ? 0 : annotations.size();
		}

		@Override
		protected boolean onTap(int index)
		{
			boolean handled = super.onTap(index);
			if(!handled ) {
				listener.onTap(index);
			}

			return handled;
		}
	}

	public static class SelectedAnnotation {
		String title;
		boolean animate;

		public SelectedAnnotation(String title, boolean animate) {
			this.title = title;
			this.animate = animate;
		}
	}
	
	public TiMapView(TiViewProxy proxy, Window mapWindow, ArrayList<AnnotationProxy> annotations, ArrayList<SelectedAnnotation>selectedAnnotations)
	{
		super(proxy);

		this.mapWindow = mapWindow;
		this.handler = new Handler(Looper.getMainLooper(), this);
		this.annotations = annotations;
		this.selectedAnnotations = selectedAnnotations;

		TiApplication app = proxy.getTiContext().getTiApp();
		TiProperties systemProperties = app.getSystemProperties();
		String oldKey = systemProperties.getString(OLD_API_KEY, "");
		String developmentKey = systemProperties.getString(DEVELOPMENT_API_KEY, "");
		String productionKey = systemProperties.getString(PRODUCTION_API_KEY, "");
		
		// To help in debugging key problems ...
		String devKeySourceInfo = "";
		String prodKeySourceInfo = "";
		
		if (developmentKey.length() > 0) {
			devKeySourceInfo = "application property '" + DEVELOPMENT_API_KEY + "'";
		} else if (oldKey.length() > 0) {
			developmentKey = oldKey;
			devKeySourceInfo = "application property '" + OLD_API_KEY + "'";
		} else {
			developmentKey = TI_DEVELOPMENT_KEY;
			devKeySourceInfo = "(Source Code)";
		}
		
		if (productionKey.length() > 0) {
			prodKeySourceInfo = "application property '" + PRODUCTION_API_KEY + "'";
		} else {
			productionKey = developmentKey;
			prodKeySourceInfo = devKeySourceInfo + " (fallback)";
		}
		
		String apiKey = developmentKey;
		if (app.getDeployType().equals(TiApplication.DEPLOY_TYPE_PRODUCTION)) {
			apiKey = productionKey;
			Log.d(LCAT, "Production mode using map api key ending with '" + productionKey.substring(productionKey.length() - 10, productionKey.length()) + "' retrieved from " + prodKeySourceInfo);
		} else {
			Log.d(LCAT, "Development mode using map api key ending with '" + developmentKey.substring(developmentKey.length() - 10, developmentKey.length()) + "' retrieved from " + devKeySourceInfo);
		}

		view = new LocalMapView(mapWindow.getContext(), apiKey);
		TiMapActivity ma = (TiMapActivity) mapWindow.getContext();

		ma.setLifecycleListener(new OnLifecycleEvent() {
			public void onPause(Activity activity) {
				if (myLocation != null) {
					if (DBG) {
						Log.d(LCAT, "onPause: Disabling My Location");
					}
					myLocation.disableMyLocation();
				}
			}
			public void onResume(Activity activity) {
				if (myLocation != null && userLocation) {
					if (DBG) {
						Log.d(LCAT, "onResume: Enabling My Location");
					}
					myLocation.enableMyLocation();
				}
			}
			public void onDestroy(Activity activity) {
			}

			public void onStart(Activity activity) {
			}

			public void onStop(Activity activity) {
			}
		});
		view.setBuiltInZoomControls(true);
		view.setScrollable(true);
		view.setClickable(true);

		setNativeView(view);

		this.regionFit =true;
		this.animate = false;

		final TiViewProxy fproxy = proxy;

		itemView = new TiOverlayItemView(proxy.getContext(), proxy.getTiContext());
		itemView.setOnOverlayClickedListener(new TiOverlayItemView.OnOverlayClicked(){
			public void onClick(int lastIndex, String clickedItem) {
				TiOverlayItem item = overlay.getItem(lastIndex);
				if (item != null) {
					KrollDict d = new KrollDict();
					d.put(TiC.PROPERTY_TITLE, item.getTitle());
					d.put(TiC.PROPERTY_SUBTITLE, item.getSnippet());
					d.put(TiC.PROPERTY_LATITUDE, scaleFromGoogle(item.getPoint().getLatitudeE6()));
					d.put(TiC.PROPERTY_LONGITUDE, scaleFromGoogle(item.getPoint().getLongitudeE6()));
					d.put(TiC.PROPERTY_ANNOTATION, item.getProxy());
					d.put(TiC.EVENT_PROPERTY_CLICKSOURCE, clickedItem);
					fproxy.fireEvent(TiC.EVENT_CLICK, d);
				}
			}
		});
	}

	private LocalMapView getView() {
		return view;
	}

	public boolean handleMessage(Message msg) {
		switch(msg.what) {
			case MSG_SET_LOCATION : {
				doSetLocation((KrollDict) msg.obj);
				return true;
			}
			case MSG_SET_MAPTYPE : {
				doSetMapType(msg.arg1);
				return true;
			}
			case MSG_SET_REGIONFIT :
				regionFit = msg.arg1 == 1 ? true : false;
				return true;
			case MSG_SET_ANIMATE :
				animate = msg.arg1 == 1 ? true : false;
				return true;
			case MSG_SET_SCROLLENABLED :
				animate = msg.arg1 == 1 ? true : false;
				if (view != null) {
					view.setScrollable(scrollEnabled);
				}
				return true;
			case MSG_SET_USERLOCATION :
				userLocation = msg.arg1 == 1 ? true : false;
				doUserLocation(userLocation);
				return true;
			case MSG_CHANGE_ZOOM :
				MapController mc = view.getController();
				if (mc != null) {
					mc.setZoom(view.getZoomLevel() + msg.arg1);
				}
				return true;
			case MSG_SELECT_ANNOTATION :
				boolean select = msg.arg1 == 1 ? true : false;
				boolean animate = msg.arg2 == 1 ? true : false;
				String title = (String) msg.obj;
				doSelectAnnotation(select, title, animate);
				return true;
			case MSG_UPDATE_ANNOTATIONS :
				doUpdateAnnotations();
				return true;
		}

		return false;
	}

	private void hideAnnotation() {
		if (view != null && itemView != null) {
			view.removeView(itemView);
			itemView.clearLastIndex();
		}
	}

	private void showAnnotation(int index, TiOverlayItem item) {
		Log.e(LCAT, "B:" + view + ":" + itemView + ":" + item);
		if (view != null && itemView != null && item != null) {
			Log.e(LCAT, "B2");
			itemView.setItem(index, item);
			//Make sure the atonnation is always on top of the marker
			int y = -1*item.getMarker(TiOverlayItem.ITEM_STATE_FOCUSED_MASK).getIntrinsicHeight();
			MapView.LayoutParams params = new MapView.LayoutParams(LayoutParams.WRAP_CONTENT,
					LayoutParams.WRAP_CONTENT, item.getPoint(), 0, y, MapView.LayoutParams.BOTTOM_CENTER);
			params.mode = MapView.LayoutParams.MODE_MAP;

			view.addView(itemView, params);
		}
	}

	public void updateAnnotations() {
		handler.obtainMessage(MSG_UPDATE_ANNOTATIONS).sendToTarget();
	}

	public void doUpdateAnnotations() {
		if (itemView != null && view != null && view.indexOfChild(itemView) != -1 ) {
			hideAnnotation();
		}
		doSetAnnotations(annotations);
	}

	public void onTap(int index) {
		if (overlay != null) {
			synchronized(overlay) {
				TiOverlayItem item = overlay.getItem(index);
				if (itemView != null && index == itemView.getLastIndex() && itemView.getVisibility() == View.VISIBLE) {
					hideAnnotation();
					return;
				}

				if (item.hasData()) {
					hideAnnotation();
					showAnnotation(index, item);
				} else {
					Toast.makeText(proxy.getContext(), "No information for location", Toast.LENGTH_SHORT).show();
				}
			}
		}
	}

	@Override
	public void processProperties(KrollDict d) {
		LocalMapView view = getView();
		if (d.containsKey(TiC.PROPERTY_MAP_TYPE)) {
			doSetMapType(TiConvert.toInt(d, TiC.PROPERTY_MAP_TYPE));
		}
		if (d.containsKey(TiC.PROPERTY_ZOOM_ENABLED)) {
			view.setBuiltInZoomControls(TiConvert.toBoolean(d,TiC.PROPERTY_ZOOM_ENABLED));
		}
		if (d.containsKey(TiC.PROPERTY_SCROLL_ENABLED)) {
			view.setScrollable(TiConvert.toBoolean(d, TiC.PROPERTY_SCROLL_ENABLED));
		}
		if (d.containsKey(TiC.PROPERTY_REGION)) {
			doSetLocation(d.getKrollDict(TiC.PROPERTY_REGION));
		}
		if (d.containsKey(TiC.PROPERTY_REGION_FIT)) {
			regionFit = d.getBoolean(TiC.PROPERTY_REGION_FIT);
		}
		if (d.containsKey(TiC.PROPERTY_ANIMATE)) {
			animate = d.getBoolean(TiC.PROPERTY_ANIMATE);
		}
		if (d.containsKey(TiC.PROPERTY_USER_LOCATION)) {
			doUserLocation(d.getBoolean(TiC.PROPERTY_USER_LOCATION));
		}
		if (d.containsKey(TiC.PROPERTY_ANNOTATIONS)) {
			proxy.setProperty(TiC.PROPERTY_ANNOTATIONS, d.get(TiC.PROPERTY_ANNOTATIONS));
			Object [] annotations = (Object[]) d.get(TiC.PROPERTY_ANNOTATIONS);
			for(int i = 0; i < annotations.length; i++) {
				AnnotationProxy ap = (AnnotationProxy) annotations[i];
				this.annotations.add(ap);
			}
			doSetAnnotations(this.annotations);
		}
		super.processProperties(d);
	}

	@Override
	public void propertyChanged(String key, Object oldValue, Object newValue, KrollProxy proxy) {
		if (key.equals(TiC.PROPERTY_LOCATION)) {
			if (newValue != null) {
				if (newValue instanceof AnnotationProxy) {
					AnnotationProxy ap = (AnnotationProxy) newValue;
					doSetLocation(ap.getProperties());
				} else if (newValue instanceof KrollDict) {
					doSetLocation((KrollDict) newValue);
				}
			}
		} else if (key.equals(TiC.PROPERTY_MAP_TYPE)) {
			if (newValue == null) {
				doSetMapType(MAP_VIEW_STANDARD);
			} else {
				doSetMapType(TiConvert.toInt(newValue));
			}
		} else {
			super.propertyChanged(key, oldValue, newValue, proxy);
		}
	}

	public void doSetLocation(KrollDict d) {
		LocalMapView view = getView();
		if (d.containsKey(TiC.PROPERTY_LONGITUDE) && d.containsKey(TiC.PROPERTY_LATITUDE)) {
			GeoPoint gp = new GeoPoint(scaleToGoogle(d.getDouble(TiC.PROPERTY_LATITUDE)), scaleToGoogle(d.getDouble(TiC.PROPERTY_LONGITUDE)));
			boolean anim = false;
			if (d.containsKey(TiC.PROPERTY_ANIMATE)) {
				anim = TiConvert.toBoolean(d, TiC.PROPERTY_ANIMATE);
			}
			if (anim) {
				view.getController().animateTo(gp);
			} else {
				view.getController().setCenter(gp);
			}
		}
		if (regionFit && d.containsKey(TiC.PROPERTY_LONGITUDE_DELTA) && d.containsKey(TiC.PROPERTY_LATITUDE_DELTA)) {
			view.getController().zoomToSpan(scaleToGoogle(d.getDouble(TiC.PROPERTY_LATITUDE_DELTA)), scaleToGoogle(d.getDouble(TiC.PROPERTY_LONGITUDE_DELTA)));
		} else {
			Log.w(LCAT, "span must have longitudeDelta and latitudeDelta");
		}
	}

	public void doSetMapType(int type) {
		if (view != null) {
			switch(type) {
			case MAP_VIEW_STANDARD :
				view.setSatellite(false);
				view.setTraffic(false);
				view.setStreetView(false);
				break;
			case MAP_VIEW_SATELLITE:
				view.setSatellite(true);
				view.setTraffic(false);
				view.setStreetView(false);
				break;
			case MAP_VIEW_HYBRID :
				view.setSatellite(false);
				view.setTraffic(false);
				view.setStreetView(true);
				break;
			}
		}
	}

	public void doSetAnnotations(ArrayList<AnnotationProxy> annotations)
	{
		if (annotations != null) {

			this.annotations = annotations;
			List<Overlay> overlays = view.getOverlays();

			synchronized(overlays) {
				if (overlays.contains(overlay)) {
					overlays.remove(overlay);
					overlay = null;
				}

				if (annotations.size() > 0) {
					overlay = new TitaniumOverlay(makeMarker(Color.BLUE), this);
					overlay.setAnnotations(annotations);
					overlays.add(overlay);

					int numSelectedAnnotations = selectedAnnotations.size();
					for(int i = 0; i < numSelectedAnnotations; i++) {
						Log.e(LCAT, "Executing internal call to selectAnnotation:" + (selectedAnnotations.get(i)).title);
						selectAnnotation(true, (selectedAnnotations.get(i)).title, (selectedAnnotations.get(i)).animate);
					}
				}

				view.invalidate();
			}
		}
	}

	public void selectAnnotation(boolean select, String title, boolean animate)
	{
		if (title != null) {
			Log.e(LCAT, "calling obtainMessage");
			handler.obtainMessage(MSG_SELECT_ANNOTATION, select ? 1 : 0, animate ? 1 : 0, title).sendToTarget();
		}
	}

	public void doSelectAnnotation(boolean select, String title, boolean animate)
	{
		Log.e(LCAT, "A:" + title + ":" + view + ":" + annotations + ":" + overlay);
		if (title != null && view != null && annotations != null && overlay != null) {
			int index = ((ViewProxy)proxy).findAnnotation(title);
			Log.e(LCAT, "A2:" + index);
			if (index > -1) {
				if (overlay != null) {
					synchronized(overlay) {
						TiOverlayItem item = overlay.getItem(index);

						if (select) {
							if (itemView != null && index == itemView.getLastIndex() && itemView.getVisibility() != View.VISIBLE) {
								showAnnotation(index, item);
								return;
							}

							hideAnnotation();

							MapController controller = view.getController();
							if (animate) {
								controller.animateTo(item.getPoint());
							} else {
								controller.setCenter(item.getPoint());
							}
							Log.e(LCAT, "A3:" + index + ":" + item);
							showAnnotation(index, item);
						} else {
							hideAnnotation();
						}
					}
				}
			}
		}
	}

	public void doUserLocation(boolean userLocation)
	{
		if (view != null) {
			if (userLocation) {
				if (myLocation == null) {
					myLocation = new MyLocationOverlay(proxy.getContext(), view);
				}

				List<Overlay> overlays = view.getOverlays();
				synchronized(overlays) {
					if (!overlays.contains(myLocation)) {
						overlays.add(myLocation);
					}
				}

				myLocation.enableMyLocation();

			} else {
				if (myLocation != null) {
					List<Overlay> overlays = view.getOverlays();
					synchronized(overlays) {
						if (overlays.contains(myLocation)) {
							overlays.remove(myLocation);
						}
						myLocation.disableMyLocation();
					}
				}
			}
		}
	}

	public void changeZoomLevel(int delta) {
		handler.obtainMessage(MSG_CHANGE_ZOOM, delta, 0).sendToTarget();
	}

	private Drawable makeMarker(int c)
	{
		OvalShape s = new OvalShape();
		s.resize(1.0f, 1.0f);
		ShapeDrawable d = new ShapeDrawable(s);
		d.setBounds(0, 0, 15, 15);
		d.getPaint().setColor(c);

		return d;
	}

	private Drawable makeMarker(String pinImage)
	{
		String url = proxy.getTiContext().resolveUrl(null, pinImage);
		TiBaseFile file = TiFileFactory.createTitaniumFile(proxy.getTiContext(), new String[] { url }, false);
		try {
			Drawable d = new BitmapDrawable(TiUIHelper.createBitmap(file.getInputStream()));
			d.setBounds(0, 0, d.getIntrinsicWidth(), d.getIntrinsicHeight());
			return d;
		} catch (IOException e) {
			Log.e(LCAT, "Error creating drawable from path: " + pinImage.toString(), e);
		}
		return null;
	}
	private double scaleFromGoogle(int value) {
		return (double)value / 1000000.0;
	}

	private int scaleToGoogle(double value) {
		return (int)(value * 1000000);
	}
}
