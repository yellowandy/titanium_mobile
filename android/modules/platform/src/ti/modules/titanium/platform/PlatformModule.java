/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2010 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package ti.modules.titanium.platform;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollModule;
import org.appcelerator.kroll.KrollProxy;
import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.TiContext;
import org.appcelerator.titanium.util.Log;
import org.appcelerator.titanium.util.TiConfig;
import org.appcelerator.titanium.util.TiPlatformHelper;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.BatteryManager;

@Kroll.module
public class PlatformModule extends KrollModule
{
	private static final String LCAT = "PlatformModule";
	private static final boolean DBG = TiConfig.LOGD;

	@Kroll.constant public static int BATTERY_STATE_UNKNOWN = 0;
	@Kroll.constant public static int BATTERY_STATE_UNPLUGGED = 1;
	@Kroll.constant public static int BATTERY_STATE_CHARGING = 2;
	@Kroll.constant public static int BATTERY_STATE_FULL = 3;

	protected DisplayCapsProxy displayCaps;

	protected int batteryState;
	protected double batteryLevel;
	protected boolean batteryStateReady;

	protected BroadcastReceiver batteryStateReceiver;

	public PlatformModule(TiContext context)
	{
		super(context);

		eventManager.addOnEventChangeListener(this);
		batteryState = BATTERY_STATE_UNKNOWN;
		batteryLevel = -1;
	}

	@Kroll.getProperty @Kroll.method
	public String getName() {
		return TiPlatformHelper.getName();
	}

	@Kroll.getProperty @Kroll.method
	public String getOsname() {
		return TiPlatformHelper.getName();
	}

	@Kroll.getProperty @Kroll.method
	public String getLocale() {
		return TiPlatformHelper.getLocale();
	}

	@Kroll.getProperty @Kroll.method
	public DisplayCapsProxy getDisplayCaps() {
		if (displayCaps == null) {
			displayCaps = new DisplayCapsProxy(getTiContext());
		}
		return displayCaps;
	}

	@Kroll.getProperty @Kroll.method
	public int getProcessorCount() {
		return TiPlatformHelper.getProcessorCount();
	}

	@Kroll.getProperty @Kroll.method
	public String getUsername() {
		return TiPlatformHelper.getUsername();
	}

	@Kroll.getProperty @Kroll.method
	public String getVersion() {
		return TiPlatformHelper.getVersion();
	}
	
	@Kroll.getProperty @Kroll.method
	public double getAvailableMemory() {
		return TiPlatformHelper.getAvailableMemory();
	}

	@Kroll.getProperty @Kroll.method
	public String getModel() {
		return TiPlatformHelper.getModel();
	}

	@Kroll.getProperty @Kroll.method
	public String getOstype() {
		return TiPlatformHelper.getOstype();
	}

	@Kroll.getProperty @Kroll.method
	public String getArchitecture() {
		return TiPlatformHelper.getArchitecture();
	}


	@Kroll.getProperty @Kroll.method
	public String getAddress() {
		return TiPlatformHelper.getIpAddress();
	}

	@Kroll.getProperty @Kroll.method
	public String getNetmask() {
		return TiPlatformHelper.getNetmask();
	}
	
	@Kroll.method
	public String createUUID() {
		return TiPlatformHelper.createUUID();
	}

	@Kroll.method
	public boolean openURL(String url) {
		if (DBG) {
			Log.d(LCAT, "Launching viewer for: " + url);
		}
		Uri uri = Uri.parse(url);
		Intent intent = new Intent(Intent.ACTION_VIEW, uri);
		try {
			getTiContext().getActivity().startActivity(intent);
			return true;
		} catch (ActivityNotFoundException e) {
			Log.e(LCAT,"Activity not found: " + url, e);
		}
		return false;
	}

	@Kroll.getProperty @Kroll.method
	public String getMacaddress() {
		return TiPlatformHelper.getMacaddress();
	}
	
	@Kroll.getProperty @Kroll.method
	public String getId() {
		return TiPlatformHelper.getMobileId();
	}

	@Kroll.setProperty @Kroll.method
	public void setBatteryMonitoring(boolean monitor)
	{
		if (monitor && batteryStateReceiver == null) {
			registerBatteryStateReceiver();
		} else if (!monitor && batteryStateReceiver != null) {
			unregisterBatteryStateReceiver();
		}
	}

	@Kroll.getProperty @Kroll.method
	public int getBatteryState()
	{
		return batteryState;
	}

	@Kroll.getProperty @Kroll.method
	public double getBatteryLevel()
	{
		return batteryLevel;
	}

	protected void registerBatteryStateReceiver()
	{
		batteryStateReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent)
			{
				int scale = intent.getIntExtra(TiC.PROPERTY_SCALE, -1);
				batteryLevel = convertBatteryLevel(intent.getIntExtra(TiC.PROPERTY_LEVEL, -1), scale);
				batteryState = convertBatteryStatus(intent.getIntExtra(TiC.PROPERTY_STATUS, -1));

				KrollDict event = new KrollDict();
				event.put(TiC.PROPERTY_LEVEL, batteryLevel);
				event.put(TiC.PROPERTY_STATE, batteryState);
				fireEvent(TiC.EVENT_BATTERY, event);
			}
		};

		registerBatteryReceiver(batteryStateReceiver);
	}

	protected void unregisterBatteryStateReceiver()
	{
		getTiContext().getActivity().unregisterReceiver(batteryStateReceiver);
	}

	@Override
	public void listenerAdded(String type, int count, final KrollProxy proxy)
	{
		if (TiC.EVENT_BATTERY.equals(type) && batteryStateReceiver == null) {
			registerBatteryStateReceiver();
		}
	}

	@Override
	public void listenerRemoved(String type, int count, KrollProxy proxy)
	{
		if (TiC.EVENT_BATTERY.equals(type) && count == 0 && batteryStateReceiver != null) {
			unregisterBatteryStateReceiver();
		}
	}

	private int convertBatteryStatus(int status)
	{
		int state = BATTERY_STATE_UNKNOWN;
		switch (status) {
			case BatteryManager.BATTERY_STATUS_CHARGING: {
				state = BATTERY_STATE_CHARGING;
				break;
			}
			case BatteryManager.BATTERY_STATUS_FULL: {
				state = BATTERY_STATE_FULL;
				break;
			}
			case BatteryManager.BATTERY_STATUS_DISCHARGING:
			case BatteryManager.BATTERY_STATUS_NOT_CHARGING: {
				state = BATTERY_STATE_UNPLUGGED;
				break;
			}
		}
		return state;
	}

	private double convertBatteryLevel(int level, int scale)
	{
		int l = -1;
		if (level >= 0 && scale > 0) {
			l = (level * 100) / scale;
		}
		return l;
	}

	private void registerBatteryReceiver(BroadcastReceiver batteryReceiver)
	{
		Activity a = getTiContext().getActivity();
		IntentFilter batteryFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
		a.registerReceiver(batteryReceiver, batteryFilter);
	}

	@Override
	public void onResume(Activity activity)
	{
		super.onResume(activity);
		if (batteryStateReceiver != null) {
			if (DBG) {
				Log.i(LCAT, "Reregistering battery changed receiver");
			}
			registerBatteryReceiver(batteryStateReceiver);
		}
	}

	@Override
	public void onPause(Activity activity)
	{
		super.onPause(activity);
		if (batteryStateReceiver != null) {
			unregisterBatteryStateReceiver();
		}
	}

	@Override
	public void onDestroy(Activity activity)
	{
		super.onDestroy(activity);
		if (batteryStateReceiver != null) {
			unregisterBatteryStateReceiver();
			batteryStateReceiver = null;
		}
	}
}
