/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2011 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package org.appcelerator.titanium;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A registry for TiBaseActivity<->Window creation logic.
 */
public class TiActivityWindows
{
	protected static AtomicInteger windowIdGenerator = new AtomicInteger();
	protected static HashMap<Integer, TiActivityWindow> windows = new HashMap<Integer, TiActivityWindow>();

	public static int addWindow(TiActivityWindow window)
	{
		int windowId = windowIdGenerator.incrementAndGet();
		windows.put(windowId, window);
		return windowId;
	}

	public static void windowCreated(TiBaseActivity activity, int windowId)
	{
		TiActivityWindow window = windows.get(windowId);
		if (window != null) {
			window.windowCreated(activity);
			windows.remove(windowId);
		}
	}
}
