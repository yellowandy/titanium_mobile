/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2010 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package org.appcelerator.kroll;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;

public class KrollDict
	extends HashMap<String, Object>
{
	private static final long serialVersionUID = 1L;
	private static final int INITIAL_SIZE = 5;

	public KrollDict() {
		this(INITIAL_SIZE);
	}

	@SuppressWarnings("unchecked")
	public KrollDict(JSONObject object) throws JSONException {
		for (Iterator<String> iter = object.keys(); iter.hasNext();) {
			String key = iter.next();
			Object value = object.get(key);			
			Object json = fromJSON(value);
			put(key, json);
		}
	}
	
	public static Object fromJSON(Object value) {
		try {
			if (value instanceof JSONObject) {
				return new KrollDict((JSONObject)value);
			} else if (value instanceof JSONArray) {
				JSONArray array = (JSONArray)value;
				Object[] values = new Object[array.length()];
				for (int i = 0; i < array.length(); i++) {
					values[i] = fromJSON(array.get(i));
				}
				return values;
			}
		} catch (JSONException e) {
			Log.e("KrollDict", "Error parsing JSON", e);
		}
		return value;
	}
	
	public KrollDict(Map<? extends String, ? extends Object> map) {
		super(map);
	}

	public KrollDict(int size) {
		super(size);
	}

	public boolean containsKeyAndNotNull(String key) {
		return containsKey(key) && get(key) != null;
	}

	public boolean containsKeyStartingWith(String keyStartsWith) {
		if (keySet() != null) { 
			for (String key : keySet()) {
				if (key.startsWith(keyStartsWith)) {
					return true;
				}
			}
		}
		return false;
	}
	
	public boolean getBoolean(String key) {
		return KrollConverter.toBoolean(get(key));
	}

	public boolean optBoolean(String key, boolean defaultValue) {
		boolean result = defaultValue;

		if (containsKey(key)) {
			result = getBoolean(key);
		}
		return result;
	}

	public String getString(String key) {
		return KrollConverter.toString(get(key));
	}

	public String optString(String key, String defalt) {
		if (containsKey(key)) {
			return getString(key);
		}
		return defalt;
	}

	public Integer getInt(String key) {
		return KrollConverter.toInt(get(key));
	}

	public Double getDouble(String key) {
		return KrollConverter.toDouble(get(key));
	}

	public String[] getStringArray(String key) {
		return KrollConverter.toStringArray((Object[])get(key));
	}

	public KrollDict getKrollDict(String key) {
		return (KrollDict) get(key);
	}

	public boolean isNull(String key) {
		return (get(key) == null);
	}
	
	@Override
	public String toString() {
		return new JSONObject(this).toString();
	}
}
