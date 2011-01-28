/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2010 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */

package org.appcelerator.titanium.util;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.codec.digest.DigestUtils;
import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollProxy;
import org.appcelerator.titanium.TiBlob;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.TiContext;
import org.appcelerator.titanium.TiDimension;
import org.appcelerator.titanium.view.TiBackgroundDrawable;
import org.appcelerator.titanium.view.TiUIView;
import org.appcelerator.titanium.proxy.TiViewProxy;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.os.Process;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.OrientationEventListener;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;

public class TiUIHelper
{
	private static final String LCAT = "TiUIHelper";
	private static final boolean DBG = TiConfig.LOGD;

	public static final int PORTRAIT = 1;
	public static final int UPSIDE_PORTRAIT = 2;
	public static final int LANDSCAPE_LEFT = 3;
	public static final int LANDSCAPE_RIGHT = 4;
	public static final int FACE_UP = 5;
	public static final int FACE_DOWN = 6;
	public static final int UNKNOWN = 7;
	public static final Pattern SIZED_VALUE = Pattern.compile("([0-9]*\\.?[0-9]+)\\W*(px|dp|dip|sp|sip|mm|pt|in)?");

	private static Method overridePendingTransition;
	private static Map<String, String> resourceImageKeys = Collections.synchronizedMap(new HashMap<String, String>());
	
	public static OnClickListener createDoNothingListener() {
		return new OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				// Do nothing
			}
		};
	}

	public static OnClickListener createKillListener() {
		return new OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				Process.killProcess(Process.myPid());
			}
		};
	}

	public static OnClickListener createFinishListener(final Activity me) {
		return new OnClickListener(){
			public void onClick(DialogInterface dialog, int which) {
				me.finish();
			}
		};
	}

	public static void doKillOrContinueDialog(Context context, String title, String message, OnClickListener positiveListener, OnClickListener negativeListener) {
		if (positiveListener == null) {
			positiveListener = createDoNothingListener();
		}
		if (negativeListener == null) {
			negativeListener = createKillListener();
		}
		
		new AlertDialog.Builder(context).setTitle(title).setMessage(message)
			.setPositiveButton("Continue", positiveListener)
			.setNegativeButton("Kill", negativeListener)
			.setCancelable(false).create().show();
	}

	public static void doOkDialog(Context context, String title, String message, OnClickListener listener) {
		if (listener == null) {
			listener = new OnClickListener() {

				public void onClick(DialogInterface dialog, int which) {
					// Do nothing.
				}};
		}
		
		new AlertDialog.Builder(context).setTitle(title).setMessage(message)
			.setPositiveButton(android.R.string.ok, listener)
			.setCancelable(false).create().show();
	}

	public static int toTypefaceStyle(String fontWeight) {
		int style = Typeface.NORMAL;
		if (fontWeight != null) {
			if(fontWeight.equals("bold")) {
				style = Typeface.BOLD;
			}
		}
		return style;
	}

	public static int getSizeUnits(String size) {
		//int units = TypedValue.COMPLEX_UNIT_SP;
		int units = TypedValue.COMPLEX_UNIT_PX;

		if (size != null) {
			Matcher m = SIZED_VALUE.matcher(size.trim());
			if (m.matches()) {
				if (m.groupCount() == 2) {
					String unit = m.group(2);
					if ("px".equals(unit)) {
						units = TypedValue.COMPLEX_UNIT_PX;
					} else if ("pt".equals(unit)) {
						units = TypedValue.COMPLEX_UNIT_PT;
					} else if ("dp".equals(unit) || "dip".equals(unit)) {
						units = TypedValue.COMPLEX_UNIT_DIP;
					} else if ("sp".equals(unit) || "sip".equals(unit)) {
						units = TypedValue.COMPLEX_UNIT_SP;
					} else if ("pt".equals(unit)) {
						units = TypedValue.COMPLEX_UNIT_PT;
					} else if ("mm".equals(unit)) {
						units = TypedValue.COMPLEX_UNIT_MM;
					} else if ("in".equals(unit)) {
						units = TypedValue.COMPLEX_UNIT_IN;
					} else {
						if (DBG) {
							if (unit != null) {
								Log.w(LCAT, "Unknown unit: " + unit);
							}
						}
						//units = TypedValue.COMPLEX_UNIT_PX;
					}
				}
			}
		}

		return units;
	}

	public static float getSize(String size) {
		float value = 15.0f;
		if (size != null) {
			Matcher m = SIZED_VALUE.matcher(size.trim());
			if (m.matches()) {
				value = Float.parseFloat(m.group(1));
			}
		}

		return value;
	}
	
	public static float getRawSize(int unit, float size, Context context) {
		Resources r;
		if (context != null) {
			r = context.getResources();
		} else {
			r = Resources.getSystem();
		}
		return TypedValue.applyDimension(unit, size, r.getDisplayMetrics());
	}
	
	public static float getRawDIPSize(float size, Context context) {
		return getRawSize(TypedValue.COMPLEX_UNIT_DIP, size, context);
	}
	
	public static float getRawSize(String size, Context context) {
		return getRawSize(getSizeUnits(size), getSize(size), context);
	}

	public static void styleText(TextView tv, KrollDict d) {
		String fontSize = null;
		String fontWeight = null;
		String fontFamily = null;

		if (d.containsKey("fontSize")) {
			fontSize = TiConvert.toString(d, "fontSize");
		}
		if (d.containsKey("fontWeight")) {
			fontWeight = TiConvert.toString(d, "fontWeight");
		}
		if (d.containsKey("fontFamily")) {
			fontFamily = TiConvert.toString(d, "fontFamily");
		}
		TiUIHelper.styleText(tv, fontFamily, fontSize, fontWeight);
	}

	public static void styleText(TextView tv, String fontFamily, String fontSize, String fontWeight) {
		Typeface tf = tv.getTypeface();
		tf = toTypeface(fontFamily);
		tv.setTypeface(tf, toTypefaceStyle(fontWeight));
		tv.setTextSize(getSizeUnits(fontSize), getSize(fontSize));
	}

	public static Typeface toTypeface(String fontFamily) {
		Typeface tf = Typeface.SANS_SERIF; // default

		if (fontFamily != null) {
			if ("monospace".equals(fontFamily)) {
				tf = Typeface.MONOSPACE;
			} else if ("serif".equals(fontFamily)) {
				tf = Typeface.SERIF;
			} else if ("sans-serif".equals(fontFamily)) {
				tf = Typeface.SANS_SERIF;
			} else {
				if (DBG) {
					Log.w(LCAT, "Unsupported font: '" + fontFamily + "' supported fonts are 'monospace', 'serif', 'sans-serif'.");
				}
			}
		}
		return tf;
	}

	public static String getDefaultFontSize(Context context) {
		String size = "15.0px";
		TextView tv = new TextView(context);
		if (tv != null) {
			size = String.valueOf(tv.getTextSize()) + "px";
			tv = null;
		}

		return size;
	}

	public static String getDefaultFontWeight(Context context) {
		String style = "normal";
		TextView tv = new TextView(context);
		if (tv != null) {
			Typeface tf = tv.getTypeface();
			if (tf != null && tf.isBold()) {
				style = "bold";
			}
		}

		return style;
	}

	public static void setAlignment(TextView tv, String textAlign, String verticalAlign) 
	{
		int gravity = Gravity.NO_GRAVITY;
		
		if (textAlign != null) {
			if ("left".equals(textAlign)) {
				 gravity |= Gravity.LEFT;
			} else if ("center".equals(textAlign)) {
				gravity |=  Gravity.CENTER_HORIZONTAL;
			} else if ("right".equals(textAlign)) {
				gravity |=  Gravity.RIGHT;
			} else {
				Log.w(LCAT, "Unsupported horizontal alignment: " + textAlign);
			}
		} else {
			// Nothing has been set - let's set if something was set previously
			// You can do this with shortcut syntax - but long term maint of code is easier if it's explicit
			if (DBG) {
				Log.w(LCAT, "No alignment set - old horiz align was: " + (tv.getGravity() & Gravity.HORIZONTAL_GRAVITY_MASK));
			}
			
			if ((tv.getGravity() & Gravity.HORIZONTAL_GRAVITY_MASK) != Gravity.NO_GRAVITY) {
				// Something was set before - so let's use it
				gravity |= tv.getGravity() & Gravity.HORIZONTAL_GRAVITY_MASK;
			}
		}
		
		if (verticalAlign != null) {
			if ("top".equals(verticalAlign)) {
				gravity |= Gravity.TOP;
			} else if ("middle".equals(verticalAlign)) {
				gravity |= Gravity.CENTER_VERTICAL;			
			} else if ("bottom".equals(verticalAlign)) {
				gravity |= Gravity.BOTTOM;			
			} else {
				Log.w(LCAT, "Unsupported vertical alignment: " + verticalAlign);			
			}
		} else {
			// Nothing has been set - let's set if something was set previously
			// You can do this with shortcut syntax - but long term maint of code is easier if it's explicit
			if (DBG) {
				Log.w(LCAT, "No alignment set - old vert align was: " + (tv.getGravity() & Gravity.VERTICAL_GRAVITY_MASK));
			}
			if ((tv.getGravity() & Gravity.VERTICAL_GRAVITY_MASK) != Gravity.NO_GRAVITY) {
				// Something was set before - so let's use it
				gravity |= tv.getGravity() & Gravity.VERTICAL_GRAVITY_MASK;
			}			
		}
		
		tv.setGravity(gravity);
	}

	public static void setTextViewDIPPadding(TextView textView, int horizontalPadding, int verticalPadding) {
		int rawHPadding = (int)getRawDIPSize(horizontalPadding, textView.getContext());
		int rawVPadding = (int)getRawDIPSize(verticalPadding, textView.getContext());
		textView.setPadding(rawHPadding, rawVPadding, rawHPadding, rawVPadding);
	}

	public static StateListDrawable buildBackgroundDrawable(TiContext tiContext,
			String image,
			String color,
			String selectedImage,
			String selectedColor,
			String disabledImage,
			String disabledColor,
			String focusedImage,
			String focusedColor)
	{
		StateListDrawable sld = null;

		Drawable bgDrawable = null;
		Drawable bgSelectedDrawable = null;
		Drawable bgFocusedDrawable = null;
		Drawable bgDisabledDrawable = null;
		
		Context appContext = tiContext.getActivity().getApplicationContext();

		TiFileHelper tfh = new TiFileHelper(appContext);

		if (image != null) {
			bgDrawable = tfh.loadDrawable(tiContext, image, false, true);
		} else if (color != null) {
			bgDrawable = new ColorDrawable(TiConvert.toColor(color));
		}

		if (selectedImage != null) {
			bgSelectedDrawable = tfh.loadDrawable(tiContext, selectedImage, false, true);
		} else if (selectedColor != null) {
			bgSelectedDrawable = new ColorDrawable(TiConvert.toColor(selectedColor));
		} else {
			if (image != null) {
				bgSelectedDrawable = tfh.loadDrawable(tiContext, image, false, true);
			} else if (color != null) {
				bgSelectedDrawable = new ColorDrawable(TiConvert.toColor(color));				
			}			
		}

		if (focusedImage != null) {
			bgFocusedDrawable = tfh.loadDrawable(tiContext, focusedImage, false, true);
		} else if (focusedColor != null) {
			bgFocusedDrawable = new ColorDrawable(TiConvert.toColor(focusedColor));
		} else {
			if (image != null) {
				bgFocusedDrawable = tfh.loadDrawable(tiContext, image, false, true);
			} else if (color != null) {
				bgFocusedDrawable = new ColorDrawable(TiConvert.toColor(color));				
			}
		}

		if (disabledImage != null) {
			bgDisabledDrawable = tfh.loadDrawable(tiContext, disabledImage, false, true);
		} else if (disabledColor != null) {
			bgDisabledDrawable = new ColorDrawable(TiConvert.toColor(disabledColor));
		} else {
			if (image != null) {
				bgDisabledDrawable = tfh.loadDrawable(tiContext, image, false, true);
			} else if (color != null) {
				bgDisabledDrawable = new ColorDrawable(TiConvert.toColor(color));				
			}
		}

		if (bgDrawable != null || bgSelectedDrawable != null || bgFocusedDrawable != null || bgDisabledDrawable != null) {
			sld = new StateListDrawable();

			if (bgDisabledDrawable != null) {
				int[] stateSet = {
					-android.R.attr.state_enabled
				};
				sld.addState(stateSet, bgDisabledDrawable);
			}

			if (bgFocusedDrawable != null) {
				int[] ss = {
					android.R.attr.state_focused,
					android.R.attr.state_window_focused,
					android.R.attr.state_enabled
				};
				sld.addState(ss, bgFocusedDrawable);
			}

			if (bgSelectedDrawable != null) {
				int[] ss = {
						android.R.attr.state_window_focused,
						android.R.attr.state_enabled,
						android.R.attr.state_pressed
					};
				sld.addState(ss, bgSelectedDrawable);


				int[] ss1 = {
					android.R.attr.state_focused,
					android.R.attr.state_window_focused,
					android.R.attr.state_enabled,
					android.R.attr.state_pressed
				};
				sld.addState(ss1, bgSelectedDrawable);
				
//				int[] ss2 = { android.R.attr.state_selected };
//				sld.addState(ss2, bgSelectedDrawable);
			}

			if (bgDrawable != null) {
				int[] ss1 = {
					android.R.attr.state_window_focused,
					android.R.attr.state_enabled
				};
				sld.addState(ss1, bgDrawable);
				int[] ss2 = { android.R.attr.state_enabled };
				sld.addState(ss2, bgDrawable);
			}
		}

		return sld;
	}

	public static KrollDict createDictForImage(TiContext context, int width, int height, byte[] data)
	{
		KrollDict d = new KrollDict();
		d.put("x", 0);
		d.put("y", 0);
		d.put("width", width);
		d.put("height", height);

		KrollDict cropRect = new KrollDict();
		cropRect.put("x", 0);
		cropRect.put("y", 0);
		cropRect.put("width", width);
		cropRect.put("height", height);
		d.put("cropRect", cropRect);
		d.put("media", TiBlob.blobFromData(context, data, "image/png"));

		return d;
	}

	public static TiBlob getImageFromDict(KrollDict dict)
	{
		if (dict != null) {
			if (dict.containsKey("media")) {
				Object media = dict.get("media");
				if (media instanceof TiBlob) {
					return (TiBlob) media;
				}
			}
		}
		return null;
	}

	public static KrollDict viewToImage(TiContext context, KrollDict proxyDict, View view)
	{
		KrollDict image = new KrollDict();

		if (view != null) {
			int width = view.getWidth();
			int height = view.getHeight();

			// maybe move this out to a separate method once other refactor regarding "getWidth", etc is done
			if(view.getWidth() == 0) {
				if(proxyDict != null) {
					if(proxyDict.containsKey(TiC.PROPERTY_WIDTH)) {
						TiDimension widthDimension = new TiDimension(proxyDict.getString(TiC.PROPERTY_WIDTH), TiDimension.TYPE_WIDTH);
						width = widthDimension.getAsPixels(view);
					}
				}
			}
			if(view.getHeight() == 0) {
				if(proxyDict != null) {
					if(proxyDict.containsKey(TiC.PROPERTY_HEIGHT)) {
						TiDimension heightDimension = new TiDimension(proxyDict.getString(TiC.PROPERTY_HEIGHT), TiDimension.TYPE_HEIGHT);
						height = heightDimension.getAsPixels(view);
					}
				}
			}
			view.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
			if (view.getParent() == null) {
				Log.i(LCAT, "view does not have parent, calling layout");
				view.layout(0, 0, width, height);
			}

			// now that we have forced the view to layout itself, grab dimensions
			width = view.getMeasuredWidth();
			height = view.getMeasuredHeight();

			// set a default BS value if the dimension is still 0 and log a warning
			if(width == 0) {
				width = 100;
				Log.e(LCAT, "width property is 0 for view, display view before calling toImage()");
			}
			if(height == 0) {
				height = 100;
				Log.e(LCAT, "height property is 0 for view, display view before calling toImage()");
			}

			Bitmap bitmap = Bitmap.createBitmap(width, height, Config.RGB_565);
			Canvas canvas = new Canvas(bitmap);

			view.draw(canvas);

			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			if (bitmap.compress(CompressFormat.PNG, 100, bos)) {
				image = createDictForImage(context, width, height, bos.toByteArray());
			}

			canvas = null;
			bitmap.recycle();
		}

		return image;
	}

	public static Bitmap createBitmap(InputStream stream)
	{
		Rect pad = new Rect();
		BitmapFactory.Options opts = new BitmapFactory.Options();
		opts.inPurgeable = true;
		opts.inInputShareable = true;

		Bitmap b = null;
		try {
			b = BitmapFactory.decodeResourceStream(null, null, stream, pad, opts);
		} catch (OutOfMemoryError e) {
			Log.e(LCAT, "Unable to load bitmap. Not enough memory: " + e.getMessage());
		}
		return b;
	}
	
	private static String getResourceKeyForImage(String url)
	{
		if (resourceImageKeys.containsKey(url)) {
			return resourceImageKeys.get(url);
		}
		
		Pattern pattern = Pattern.compile("^.*/Resources/images/(.*$)");
		Matcher matcher = pattern.matcher(url);
		if (!matcher.matches()) {
			return null;
		}
		
		String chopped = matcher.group(1);
		if (chopped == null) {
			return null;
		}
		
		chopped = chopped.toLowerCase();
		String forHash = chopped;
		if (forHash.endsWith(".9.png")) {
			forHash = forHash.replace(".9.png", ".png");
		}
		String withoutExtension = chopped;
		
		if (chopped.matches("^.*\\..*$")) {
			if (chopped.endsWith(".9.png")) {
				withoutExtension = chopped.substring(0, chopped.lastIndexOf(".9.png"));
			} else {
				withoutExtension = chopped.substring(0, chopped.lastIndexOf('.'));
			}
		}
		
		String cleanedWithoutExtension = withoutExtension.replaceAll("[^a-z0-9_]", "_");
		StringBuilder result = new StringBuilder(100);
		result.append(cleanedWithoutExtension.substring(0, Math.min(cleanedWithoutExtension.length(), 80))) ;
		result.append("_");
		result.append(DigestUtils.md5Hex(forHash).substring(0, 10));
		String sResult = result.toString();
		resourceImageKeys.put(url, sResult);
		return sResult;
	}
	
	public static int getResourceId(String url)
	{
		if (!url.contains("Resources/images/")) {
			return 0;
		}
		
		String key = getResourceKeyForImage(url);
		if (key == null) {
			return 0;
		}
		
		try {
			return TiRHelper.getResource("drawable." + key);
		} catch (TiRHelper.ResourceNotFoundException e) {
			return 0;
		}
	}
	
	public static Bitmap getResourceBitmap(TiContext context, String url)
	{
		int id = getResourceId(url);
		if (id == 0) {
			return null;
		} else {
			return getResourceBitmap(context, id);
		}
	}
	
	public static Bitmap getResourceBitmap(TiContext context, int res_id)
	{
		BitmapFactory.Options opts = new BitmapFactory.Options();
		opts.inPurgeable = true;
		opts.inInputShareable = true;
		
		Bitmap bitmap = null;
		try {
			bitmap = BitmapFactory.decodeResource(context.getActivity().getResources(), res_id, opts);
		} catch (OutOfMemoryError e) {
			Log.e(LCAT, "Unable to load bitmap. Not enough memory: " + e.getMessage());
		}
		return bitmap;
	}
	
	public static Drawable getResourceDrawable(TiContext context, String url)
	{
		int id = getResourceId(url);
		if (id == 0) {
			return null;
		}
		
		return getResourceDrawable(context, id);
	}
	
	public static Drawable getResourceDrawable(TiContext context, int res_id)
	{
		return context.getActivity().getResources().getDrawable(res_id);
	}
	
	
	public static void overridePendingTransition(Activity activity) 
	{
		if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.DONUT) {
			return;
		}
		
		if (overridePendingTransition == null) {
			try {
				overridePendingTransition = Activity.class.getMethod("overridePendingTransition", Integer.TYPE, Integer.TYPE);
			} catch (NoSuchMethodException e) {
				Log.w(LCAT, "Activity.overridePendingTransition() not found");
			}
			
		}
		
		if (overridePendingTransition != null) {
			try {
				overridePendingTransition.invoke(activity, new Object[]{0,0});
			} catch (InvocationTargetException e) {
				Log.e(LCAT, "Called incorrectly: " + e.getMessage());
			} catch (IllegalAccessException e) {
				Log.e(LCAT, "Illegal access: " + e.getMessage());
			}
		}
	}
	
	public static ColorFilter createColorFilterForOpacity(float opacity) {
		// 5x4 identity color matrix + fade the alpha to achieve opacity
		float[] matrix = {
			1, 0, 0, 0, 0,
			0, 1, 0, 0, 0,
			0, 0, 1, 0, 0,
			0, 0, 0, opacity, 0
		};
		
		return new ColorMatrixColorFilter(new ColorMatrix(matrix));
	}
	
	public static void setDrawableOpacity(Drawable drawable, float opacity) {
		if (drawable instanceof ColorDrawable || drawable instanceof TiBackgroundDrawable) {
			drawable.setAlpha(Math.round(opacity * 255));
		} else if (drawable != null) {
			drawable.setColorFilter(createColorFilterForOpacity(opacity));
		}
	}
	
	public static void setPaintOpacity(Paint paint, float opacity) {
		paint.setColorFilter(createColorFilterForOpacity(opacity));
	}

	public static void requestSoftInputChange(KrollProxy proxy, View view) 
	{
		int focusState = TiUIView.SOFT_KEYBOARD_DEFAULT_ON_FOCUS;
		
		if (proxy.hasProperty("softKeyboardOnFocus")) {
			focusState = TiConvert.toInt(proxy.getProperty("softKeyboardOnFocus"));
		}

		if (focusState > TiUIView.SOFT_KEYBOARD_DEFAULT_ON_FOCUS) {
			if (focusState == TiUIView.SOFT_KEYBOARD_SHOW_ON_FOCUS) {
				showSoftKeyboard(view, true);
			} else if (focusState == TiUIView.SOFT_KEYBOARD_HIDE_ON_FOCUS) {
				showSoftKeyboard(view, false);
			} else {
				Log.w(LCAT, "Unknown onFocus state: " + focusState);
			}
		}
	}
	
	public static void showSoftKeyboard(View view, boolean show) 
	{
		InputMethodManager imm = (InputMethodManager) view.getContext().getSystemService(Activity.INPUT_METHOD_SERVICE);

		if (imm != null) {
			boolean useForce = (Build.VERSION.SDK_INT <= Build.VERSION_CODES.DONUT || Build.VERSION.SDK_INT >= 8) ? true : false;
			String model = TiPlatformHelper.getModel(); 
			if (model != null && model.toLowerCase().startsWith("droid")) {
				useForce = true;
			}
			
			if (show) {
				imm.showSoftInput(view, useForce ? InputMethodManager.SHOW_FORCED : InputMethodManager.SHOW_IMPLICIT);
			} else {
				imm.hideSoftInputFromWindow(view.getWindowToken(), useForce ? 0 : InputMethodManager.HIDE_IMPLICIT_ONLY);
			}
		}
	}
	
	public static int convertToAndroidOrientation(int orientation) {
		switch (orientation) {
			case LANDSCAPE_LEFT :
			case LANDSCAPE_RIGHT :
				return ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
			case PORTRAIT :
			case UPSIDE_PORTRAIT :
				return ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
		}
		return ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
	}
	
	public static int convertToTiOrientation(int orientation) {
		switch(orientation)
		{
			case Configuration.ORIENTATION_LANDSCAPE:
			case ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:
				return LANDSCAPE_LEFT;
			case Configuration.ORIENTATION_PORTRAIT:
			// == case ActivityInfo.SCREEN_ORIENTATION_PORTRAIT:
				return PORTRAIT;
		}
		return UNKNOWN;
	}
	
	public static int convertToTiOrientation(int orientation, int degrees) {
		if (degrees == OrientationEventListener.ORIENTATION_UNKNOWN) {
			return convertToTiOrientation(orientation);
		}
		switch (orientation) {
		case Configuration.ORIENTATION_LANDSCAPE:
		case ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:
			if (degrees >= 270 && degrees <= 360) {
				return LANDSCAPE_LEFT;
			} else {
				return LANDSCAPE_RIGHT;
			}
		case Configuration.ORIENTATION_PORTRAIT:
			return PORTRAIT;
		}
		return UNKNOWN;
	}
}
