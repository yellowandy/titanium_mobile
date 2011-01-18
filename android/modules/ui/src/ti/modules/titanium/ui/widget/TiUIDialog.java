/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2010 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package ti.modules.titanium.ui.widget;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollProxy;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.proxy.TiViewProxy;
import org.appcelerator.titanium.util.Log;
import org.appcelerator.titanium.util.TiConfig;
import org.appcelerator.titanium.util.TiConvert;
import org.appcelerator.titanium.view.TiUIView;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;

public class TiUIDialog extends TiUIView
{
	private static final String LCAT = "TiUIDialog";
	private static final boolean DBG = TiConfig.LOGD;
	private static final int BUTTON_MASK = 0x10000000;

	protected Builder builder;
	protected AlertDialog dialog;
	protected TiUIView view;

	protected class ClickHandler implements DialogInterface.OnClickListener {
		private int result;
		public ClickHandler(int id) {
			this.result = id;
		}
		public void onClick(DialogInterface dialog, int which) {
			handleEvent(result);
			hide(null);
		}
	}

	public TiUIDialog(TiViewProxy proxy) {
		super(proxy);
		if (DBG) {
			Log.d(LCAT, "Creating a dialog");
		}
		createBuilder();
	}

	private Activity getCurrentActivity() {
		Activity currentActivity = proxy.getTiContext().getTiApp().getCurrentActivity();
		if (currentActivity == null) {
			currentActivity = proxy.getTiContext().getActivity();
		}
		return currentActivity;
	}
	
	private Builder getBuilder() {
		if (builder == null) {
			createBuilder();
		}
		return builder;
	}
	
	@Override
	public void processProperties(KrollDict d) {
		if (d.containsKey(TiC.PROPERTY_TITLE)) {
			getBuilder().setTitle(d.getString(TiC.PROPERTY_TITLE));
		}
		if (d.containsKey(TiC.PROPERTY_MESSAGE)) {
			getBuilder().setMessage(d.getString(TiC.PROPERTY_MESSAGE));
		}
		if (d.containsKey(TiC.PROPERTY_BUTTON_NAMES))
		{
			String[] buttonText = d.getStringArray(TiC.PROPERTY_BUTTON_NAMES);
			processButtons(buttonText);
		}
		if (d.containsKeyAndNotNull(TiC.PROPERTY_ANDROID_VIEW)) {
			processView((TiViewProxy) proxy.getProperty(TiC.PROPERTY_ANDROID_VIEW));
		} else if (d.containsKey(TiC.PROPERTY_OPTIONS)) {
			String[] optionText = d.getStringArray(TiC.PROPERTY_OPTIONS);
			int selectedIndex = d.containsKey(TiC.PROPERTY_SELECTED_INDEX) ? d.getInt(TiC.PROPERTY_SELECTED_INDEX) : -1; 
			if(selectedIndex > optionText.length){
				Log.d(LCAT, "Ooops invalid selected index specified: " + selectedIndex);
				selectedIndex = -1;
			}
			
			processOptions(optionText, selectedIndex);
		}
		super.processProperties(d);
	}

	private void processOptions(String[] optionText) {
		getBuilder().setSingleChoiceItems(optionText, -1 , new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				handleEvent(which);
				dialog.dismiss();
			}
		});
	}

	private void processOptions(String[] optionText,int selectedIndex) {
		getBuilder().setSingleChoiceItems(optionText, selectedIndex , new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				handleEvent(which);
				dialog.dismiss();
			}
		});
	}
	
	private void processButtons(String[] buttonText) {
		getBuilder().setPositiveButton(null, null);
		getBuilder().setNegativeButton(null, null);
		getBuilder().setNeutralButton(null, null);

		for (int id = 0; id < buttonText.length; id++) {
			String text = buttonText[id];
			ClickHandler clicker = new ClickHandler(id | BUTTON_MASK);
			switch (id) {
			case 0:
				getBuilder().setPositiveButton(text, clicker);
				break;
			case 1:
				getBuilder().setNeutralButton(text, clicker);
				break;
			case 2:
				getBuilder().setNegativeButton(text, clicker);
				break;
			default:
				Log.e(LCAT, "Only 3 buttons are supported");
			}
		}
	}

	private void processView(TiViewProxy proxy) {
		if (proxy != null) {
			view = proxy.getView(getCurrentActivity());
			getBuilder().setView(view.getNativeView());
		}
	}

	@Override
	public void propertyChanged(String key, Object oldValue, Object newValue, KrollProxy proxy)
	{
		if (DBG) {
			Log.d(LCAT, "Property: " + key + " old: " + oldValue + " new: " + newValue);
		}

		if (key.equals(TiC.PROPERTY_TITLE)) {
			if (dialog != null) {
				dialog.setTitle((String) newValue);
			}
		} else if (key.equals(TiC.PROPERTY_MESSAGE)) {
			if (dialog != null) {
				dialog.setMessage((String) newValue);
			}
		} else if (key.equals(TiC.PROPERTY_BUTTON_NAMES)) {
			if (dialog != null) {
				dialog.dismiss();
				dialog = null;
			}

			processButtons(TiConvert.toStringArray((Object[]) newValue));
		} else if (key.equals(TiC.PROPERTY_OPTIONS)) {
			if (dialog != null) {
				dialog.dismiss();
				dialog = null;
			}

			getBuilder().setView(null);
			processOptions(TiConvert.toStringArray((Object[]) newValue));
		} else if (key.equals(TiC.PROPERTY_ANDROID_VIEW)) {
			if (dialog != null) {
				dialog.dismiss();
				dialog = null;
			}
			if (newValue != null) {
				processView((TiViewProxy) newValue);
			} else {
				proxy.setProperty(TiC.PROPERTY_ANDROID_VIEW, null, false);
			}
		} else {
			super.propertyChanged(key, oldValue, newValue, proxy);
		}
	}

	public void show(KrollDict options) {
		if (dialog == null) {
			processProperties(proxy.getProperties());
			getBuilder().setOnCancelListener(new OnCancelListener() {
				@Override
				public void onCancel(DialogInterface dlg) {
					int cancelIndex = (proxy.hasProperty(TiC.PROPERTY_CANCEL)) ? TiConvert.toInt(proxy.getProperty(TiC.PROPERTY_CANCEL)) : -1;
					if (DBG) {
						Log.d(LCAT, "onCancelListener called. Sending index: " + cancelIndex);
					}
					handleEvent(cancelIndex);
					hide(null);
				}
			});
			dialog = getBuilder().create();
			builder = null;
		}
		try {
			dialog.show();
		} catch (Throwable t) {
			Log.w(LCAT, "Window must have gone away.");
		}
	}

	public void hide(KrollDict options) {
		if (dialog != null) {
			dialog.dismiss();
			dialog = null;
		}
		if (view != null) {
			view.getProxy().releaseViews();
			view = null;
		}
	}

	private void createBuilder()  {
		Activity currentActivity = getCurrentActivity();
		
		this.builder = new AlertDialog.Builder(currentActivity);
		this.builder.setCancelable(true);
	}
	
	public void handleEvent(int id) {
		int cancelIndex = (proxy.hasProperty(TiC.PROPERTY_CANCEL)) ?
			TiConvert.toInt(proxy.getProperty(TiC.PROPERTY_CANCEL)) : -1;
		KrollDict data = new KrollDict();
		if ((id & BUTTON_MASK) != 0) {
			data.put("button", true);
			id &= ~BUTTON_MASK;
		} else {
			data.put("button", false);
		}
		data.put(TiC.EVENT_PROPERTY_INDEX, id);
		data.put(TiC.PROPERTY_CANCEL, id == cancelIndex);
		proxy.fireEvent(TiC.EVENT_CLICK, data);
	}
}
