/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2010 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */

package ti.modules.titanium.accelerometer;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollInvocation;
import org.appcelerator.kroll.KrollModule;
import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.titanium.TiContext;
import org.appcelerator.titanium.util.TiConfig;
import org.appcelerator.titanium.util.TiSensorHelper;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;


@Kroll.module
public class AccelerometerModule extends KrollModule
	implements SensorEventListener
{
	private static final String LCAT = "TiAccelerometer";
	private static final boolean DBG = TiConfig.LOGD;
	private static final String EVENT_UPDATE = "update";

	private boolean accelerometerRegistered = false;
	private long lastSensorEventTimestamp = 0;


	public AccelerometerModule(TiContext tiContext)
	{
		super(tiContext);
	}

	@Override
	public int addEventListener(KrollInvocation invocation, String eventName, Object listener)
	{
		if (!accelerometerRegistered) {
			if (EVENT_UPDATE.equals(eventName)) {
				TiSensorHelper.registerListener(Sensor.TYPE_ACCELEROMETER, this, SensorManager.SENSOR_DELAY_UI);
				accelerometerRegistered = true;
			}
		}
		return super.addEventListener(invocation, eventName, listener);
	}

	@Override
	public void removeEventListener(KrollInvocation invocation, String eventName, Object listener)
	{
		if (accelerometerRegistered) {
			if (EVENT_UPDATE.equals(eventName)) {
				TiSensorHelper.unregisterListener(Sensor.TYPE_ACCELEROMETER, this);
				accelerometerRegistered = false;
			}
		}
		super.removeEventListener(invocation, eventName, listener);
	}

	public void onAccuracyChanged(Sensor sensor, int accuracy)
	{
	}

	public void onSensorChanged(SensorEvent event)
	{
		if (event.timestamp - lastSensorEventTimestamp > 100) {
			lastSensorEventTimestamp = event.timestamp;

			float x = event.values[SensorManager.DATA_X];
			float y = event.values[SensorManager.DATA_Y];
			float z = event.values[SensorManager.DATA_Z];

			KrollDict data = new KrollDict();
			data.put("type", EVENT_UPDATE);
			data.put("timestamp", lastSensorEventTimestamp);
			data.put("x", x);
			data.put("y", y);
			data.put("z", z);
			fireEvent(EVENT_UPDATE, data);
		}
	}
}

