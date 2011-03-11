/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2010 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package ti.modules.titanium.json;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollModule;
import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.titanium.TiContext;
import org.appcelerator.titanium.util.TiConvert;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

@Kroll.module @Kroll.topLevel
public class JSONModule extends KrollModule {

	public JSONModule(TiContext context) {
		super(context);
	}

	@Kroll.method
	public String stringify(Object data) {
		if (data instanceof KrollDict) {
			return TiConvert.toJSON((KrollDict)data).toString();
		} else if (data instanceof Object[]) {
			Object[] objects = (Object[])data;
			StringBuilder sb = new StringBuilder();
			sb.append("[");
			for (int i = 0; i < objects.length; i++) {
				sb.append(stringify(objects[i]));
				if (i < objects.length - 1) {
					sb.append(", ");
				}
			}
			sb.append("]");
			return sb.toString();
		} else if (data instanceof Number) {
			// Normalize whole numbers as ints
			double d = ((Number)data).doubleValue();
			double whole = d < 0 ? Math.ceil(d) : Math.floor(d);
			if (d - whole == 0) {
				return "" + ((int) d);
			} else {
				return "" + d;
			}
		}
		else if (data instanceof Boolean) {
			return TiConvert.toJSONString(data);
		} 
		else {
			return "\"" + TiConvert.toJSONString(data) + "\"";
		}
	}

	@Kroll.method
	public Object parse(String json)
		throws JSONException
	{
		Object parsed = null;
		if (json == null || json.length() == 0) {
			return parsed;
		}
		
		String trimmed = json.trim();
		char firstChar = trimmed.charAt(0);

		if (firstChar == '{') {
			parsed = new KrollDict(new JSONObject(json));
		} else if (firstChar == '[') {
			JSONArray array = new JSONArray(json);
			Object result[] = new Object[array.length()];
			for (int i = 0; i < array.length(); i++) {
				result[i] = KrollDict.fromJSON(array.get(i));
			}
			parsed = result;
		} else {
			parsed = new JSONTokener(json).nextValue();
		}
		
		return parsed;
	}

}
