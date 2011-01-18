/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2010 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package org.appcelerator.titanium.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollObject;
import org.appcelerator.kroll.KrollProxy;
import org.appcelerator.titanium.TiBlob;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.TiDimension;
import org.appcelerator.titanium.kroll.KrollCallback;
import org.appcelerator.titanium.view.Ti2DMatrix;
import org.appcelerator.titanium.view.TiCompositeLayout.LayoutParams;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.javascript.Function;

import android.graphics.drawable.ColorDrawable;
import android.net.Uri;

public class TiConvert
{
	private static final String LCAT = "TiConvert";
	private static final boolean DBG = TiConfig.LOGD;

	public static final String ASSET_URL = "file:///android_asset/"; // class scope on URLUtil
	public static final String JSON_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";

	// Bundle 
	public static Object putInKrollDict(KrollDict d, String key, Object value) {
		if (value instanceof String || value instanceof Number || value instanceof Boolean || value instanceof Date) {
			d.put(key, value);
		} else if (value instanceof KrollDict) {
			KrollDict nd = new KrollDict();
			KrollDict dict = (KrollDict) value;
			for (String k : dict.keySet()) {
				putInKrollDict(nd, k, dict.get(k));
			}
			d.put(key, nd);
			value = nd;
		} else if (value instanceof Object[]) {
			Object[] a = (Object[]) value;
			int len = a.length;
			if (len > 0) {
				Object v = a[0];
				if (DBG) {
					if (v != null) {
						Log.w(LCAT, "Array member is type: " + v.getClass().getSimpleName());
					} else {
						Log.w(LCAT, "First member of array is null");
					}
				}
				if (v != null && v instanceof String) {
					String[] sa = new String[len];
					for(int i = 0; i < len; i++) {
						sa[i] = (String) a[i];
					}
					d.put(key, sa);
				} else if (v != null && v instanceof Double) {
					double[] da = new double[len];
					for(int i = 0; i < len; i++) {
						da[i] = (Double) a[i];
					}
					d.put(key, da);
				} else if (v != null && v instanceof KrollObject) {
					KrollProxy[] pa = new KrollProxy[len];
					for(int i = 0; i < len; i++) {
						KrollObject ko = (KrollObject) a[i];
						pa[i] = (KrollProxy) ko.getProxy();
					}
					d.put(key, pa);
				} else {

					Object[] oa = new Object[len];
					for(int i = 0; i < len; i++) {
						oa[i] = a[i];
					}
					d.put(key, oa);
					//throw new IllegalArgumentException("Unsupported array property type " + v.getClass().getSimpleName());
				}
			} else {
				d.put(key, (Object[]) value);
			}
		} else if (value == null) {
			d.put(key, null);
		} else if (value instanceof KrollProxy) {
			d.put(key, value);
		} else if (value instanceof KrollCallback || value instanceof Function) {
			d.put(key, value);
		} else if (value instanceof Map) {
			KrollDict dict = new KrollDict();
			Map<?,?> map = (Map<?,?>)value;
			Iterator<?> iter = map.keySet().iterator();
			while(iter.hasNext())
			{
				String k = (String)iter.next();
				putInKrollDict(dict,k,map.get(k));
			}
			d.put(key,dict);
		} else {
			throw new IllegalArgumentException("Unsupported property type " + value.getClass().getName());
		}
		return value;
	}

	// Color conversions
	public static int toColor(String value) {
		return TiColorHelper.parseColor(value);
	}

	public static int toColor(KrollDict d, String key) {
		return toColor(d.getString(key));
	}

	public static ColorDrawable toColorDrawable(String value) {
		return new ColorDrawable(toColor(value));
	}

	public static ColorDrawable toColorDrawable(KrollDict d, String key) {
		return toColorDrawable(d.getString(key));
	}

	// Layout
	public static boolean fillLayout(KrollDict d, LayoutParams layoutParams) {
		boolean dirty = false;
		Object width = null;
		Object height = null;
		if (d.containsKey(TiC.PROPERTY_SIZE)) {
			KrollDict size = (KrollDict)d.get(TiC.PROPERTY_SIZE);
			width = size.get(TiC.PROPERTY_WIDTH);
			height = size.get(TiC.PROPERTY_HEIGHT);
		}
		if (d.containsKey(TiC.PROPERTY_LEFT)) {
			layoutParams.optionLeft = toTiDimension(d, TiC.PROPERTY_LEFT, TiDimension.TYPE_LEFT);
			dirty = true;
		}
		if (d.containsKey(TiC.PROPERTY_TOP)) {
			layoutParams.optionTop = toTiDimension(d, TiC.PROPERTY_TOP, TiDimension.TYPE_TOP);
			dirty = true;
		}
		if (d.containsKey(TiC.PROPERTY_CENTER)) {
			updateLayoutCenter(d.get(TiC.PROPERTY_CENTER), layoutParams);
			dirty = true;
		}
		if (d.containsKey(TiC.PROPERTY_RIGHT)) {
			layoutParams.optionRight = toTiDimension(d, TiC.PROPERTY_RIGHT, TiDimension.TYPE_RIGHT);
			dirty = true;
		}
		if (d.containsKey(TiC.PROPERTY_BOTTOM)) {
			layoutParams.optionBottom = toTiDimension(d, TiC.PROPERTY_BOTTOM, TiDimension.TYPE_BOTTOM);
			dirty = true;
		}
		if (width != null || d.containsKey(TiC.PROPERTY_WIDTH)) {
			if (width == null)
			{
				width = d.get(TiC.PROPERTY_WIDTH);
			}
			if (width == null || width.equals(TiC.SIZE_AUTO)) {
				layoutParams.optionWidth = null;
				layoutParams.autoWidth = true;
			} else {
				layoutParams.optionWidth = toTiDimension(width, TiDimension.TYPE_WIDTH);
				layoutParams.autoWidth = false;
			}
			dirty = true;
		}
		if (height != null || d.containsKey(TiC.PROPERTY_HEIGHT)) {
			if (height == null)
			{
				height = d.get(TiC.PROPERTY_HEIGHT);
			}
			if (height == null || height.equals(TiC.SIZE_AUTO)) {
				layoutParams.optionHeight = null;
				layoutParams.autoHeight = true;
			} else {
				layoutParams.optionHeight = toTiDimension(height, TiDimension.TYPE_HEIGHT);
				layoutParams.autoHeight = false;
			}
			dirty = true;
		}
		if (d.containsKey(TiC.PROPERTY_ZINDEX)) {
			Object zIndex = d.get(TiC.PROPERTY_ZINDEX);
			if (zIndex != null) {
				layoutParams.optionZIndex = toInt(zIndex);
			} else {
				layoutParams.optionZIndex = 0;
			}
			dirty = true;
		}
		if (d.containsKey(TiC.PROPERTY_TRANSFORM)) {
			layoutParams.optionTransform = (Ti2DMatrix) d.get(TiC.PROPERTY_TRANSFORM);
		}
		return dirty;
	}

	public static void updateLayoutCenter(Object value, LayoutParams layoutParams)
	{
		if (value instanceof KrollDict) {
			KrollDict center = (KrollDict) value;
			if (center.containsKeyAndNotNull(TiC.PROPERTY_X)) {
				layoutParams.optionCenterX = toTiDimension(center, TiC.PROPERTY_X, TiDimension.TYPE_CENTER_X);
			} else {
				layoutParams.optionCenterX = null;
			}
			if (center.containsKeyAndNotNull(TiC.PROPERTY_Y)) {
				layoutParams.optionCenterY = toTiDimension(center, TiC.PROPERTY_Y, TiDimension.TYPE_CENTER_Y);
			} else {
				layoutParams.optionCenterY = null;
			}
		} else if (value != null) {
			layoutParams.optionCenterX = toTiDimension(value, TiDimension.TYPE_CENTER_X);
			layoutParams.optionCenterY = null;
		} else {
			layoutParams.optionCenterX = null;
			layoutParams.optionCenterY = null;
		}
	}

	// Values
	public static boolean toBoolean(Object value) {
		if (value instanceof Boolean) {
			return (Boolean) value;
		} else if (value instanceof String) {
			return Boolean.parseBoolean(((String) value));
		} else {
			throw new IllegalArgumentException("Unable to convert " + value.getClass().getName() + " to boolean.");
		}
	}
	public static boolean toBoolean(KrollDict d, String key) {
		return toBoolean(d.get(key));
	}

	public static int toInt(Object value) {
		if (value instanceof Double) {
			return ((Double) value).intValue();
		} else if (value instanceof Integer) {
			return ((Integer) value);
		} else if (value instanceof String) {
			return Integer.parseInt((String) value);
		} else {
			throw new NumberFormatException("Unable to convert " + value.getClass().getName());
		}
	}
	public static int toInt(KrollDict d, String key) {
		return toInt(d.get(key));
	}

	public static float toFloat(Object value) {
		if (value instanceof Double) {
			return ((Double) value).floatValue();
		} else if (value instanceof Integer) {
			return ((Integer) value).floatValue();
		} else if (value instanceof String) {
			return Float.parseFloat((String) value);
		} else {
			throw new NumberFormatException("Unable to convert " + value.getClass().getName());
		}
	}
	public static float toFloat(KrollDict d, String key) {
		return toFloat(d.get(key));
	}

	public static double toDouble(Object value) {
		if (value instanceof Double) {
			return ((Double) value);
		} else if (value instanceof Integer) {
			return ((Integer) value).doubleValue();
		} else if (value instanceof String) {
			return Double.parseDouble((String) value);
		} else {
			throw new NumberFormatException("Unable to convert " + value.getClass().getName());
		}
	}
	public static double toDouble(KrollDict d, String key) {
		return toDouble(d.get(key));
	}

	public static String toString(Object value) {
		return value == null ? null : value.toString();
	}
	public static String toString(KrollDict d, String key) {
		return toString(d.get(key));
	}

	public static String[] toStringArray(Object[] parts) {
		String[] sparts = (parts != null ? new String[parts.length] : new String[0]);
		if (parts != null) {
			for (int i = 0; i < parts.length; i++) {
				sparts[i] = parts[i] == null ? null : parts[i].toString();
			}
		}
		return sparts;
	}

	// Dimensions
	public static TiDimension toTiDimension(String value, int valueType) {
		return new TiDimension(value, valueType);
	}

	public static TiDimension toTiDimension(Object value, int valueType) {
		if (value instanceof Number) {
			value = value.toString() + "px";
		}
		return toTiDimension((String) value, valueType);
	}
	
	public static TiDimension toTiDimension(KrollDict d, String key, int valueType) {
		return toTiDimension(d.get(key), valueType);
	}

	// URL
	public static String toURL(Uri uri) {
		String url = null;
		if (uri.isRelative()) {
			url = uri.toString();
			if (url.startsWith("/")) {
				url = ASSET_URL + "Resources" + url.substring(1);
			} else {
				url = ASSET_URL + "Resources/" + url;
			}
		} else {
			url = uri.toString();
		}

		return url;
	}

	//Error
	public static KrollDict toErrorObject(int code, String msg) {
		KrollDict d = new KrollDict(1);
		KrollDict e = new KrollDict();
		e.put(TiC.ERROR_PROPERTY_CODE, code);
		e.put(TiC.ERROR_PROPERTY_MESSAGE, msg);
		d.put(TiC.EVENT_PROPERTY_ERROR, e);
		return d;
	}

	public static TiBlob toBlob(Object value) {
		return (TiBlob) value;
	}

	public static TiBlob toBlob(KrollDict object, String property) {
		return toBlob(object.get(property));
	}

	// JSON
	public static JSONObject toJSON(KrollDict data) {
		if (data == null) {
			return null;
		}
		JSONObject json = new JSONObject();

		for (String key : data.keySet()) {
			try {
				Object o = data.get(key);
				if (o == null) {
					json.put(key, JSONObject.NULL);
				} else if (o instanceof Number) {
					json.put(key, (Number) o);
				} else if (o instanceof String) {
					json.put(key, (String) o);
				} else if (o instanceof Boolean) {
					json.put(key, (Boolean) o);
				} else if (o instanceof Date) {
					json.put(key, toJSONString((Date)o));
				} else if (o instanceof KrollDict) {
					json.put(key, toJSON((KrollDict) o));
				} else if (o.getClass().isArray()) {
					json.put(key, toJSONArray((Object[]) o));
				} else {
					Log.w(LCAT, "Unsupported type " + o.getClass());
				}
			} catch (JSONException e) {
				Log.w(LCAT, "Unable to JSON encode key: " + key);
			}
		}

		return json;
	}

	public static JSONArray toJSONArray(Object[] a) {
		JSONArray ja = new JSONArray();
		for (Object o : a) {
			if (o == null) {
				if (DBG) {
					Log.w(LCAT, "Skipping null value in array");
				}
				continue;
			}
			if (o == null) {
				ja.put(JSONObject.NULL);
			} else if (o instanceof Number) {
				ja.put((Number) o);
			} else if (o instanceof String) {
				ja.put((String) o);
			} else if (o instanceof Boolean) {
				ja.put((Boolean) o);
			} else if (o instanceof Date) {
				ja.put(toJSONString((Date)o));
			} else if (o instanceof KrollDict) {
				ja.put(toJSON((KrollDict) o));
			} else if (o.getClass().isArray()) {
				ja.put(toJSONArray((Object[]) o));
			} else {
				Log.w(LCAT, "Unsupported type " + o.getClass());
			}
		}
		return ja;
	}
	
	public static String toJSONString(Object value) {
		if (value instanceof Date) {
			return new SimpleDateFormat(JSON_DATE_FORMAT).format((Date)value);
		} else return toString(value);
	}

	public static Date toDate(Object value) {
		if (value instanceof Date) {
			return (Date)value;
		} else if (value instanceof Number) {
			long millis = ((Number)value).longValue();
			return new Date(millis);
		}
		return null;
	}
	
	public static Date toDate(KrollDict d, String key) {
		return toDate(d.get(key));
	}
}
