/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2010 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package org.appcelerator.titanium;

import java.io.IOException;
import java.lang.ref.WeakReference;

import org.appcelerator.titanium.kroll.KrollBridge;
import org.appcelerator.titanium.kroll.KrollContext;
import org.appcelerator.titanium.util.Log;
import org.appcelerator.titanium.util.TiConfig;
import org.appcelerator.titanium.util.TiFileHelper;
import org.appcelerator.titanium.util.TiJSErrorDialog;
import org.appcelerator.titanium.util.TiUrl;
import org.appcelerator.titanium.util.TiWeakList;
import org.mozilla.javascript.ErrorReporter;
import org.mozilla.javascript.EvaluatorException;
import org.mozilla.javascript.Scriptable;

import android.app.Activity;
import android.app.Service;
import android.content.ContextWrapper;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;

public class TiContext implements TiEvaluator, ErrorReporter
{
	private static final String LCAT = "TiContext";
	private static final boolean DBG = TiConfig.LOGD;
	@SuppressWarnings("unused")
	private static final boolean TRACE = TiConfig.LOGV;

	public static final int LIFECYCLE_ON_START = 0;
	public static final int LIFECYCLE_ON_RESUME = 1;
	public static final int LIFECYCLE_ON_PAUSE = 2;
	public static final int LIFECYCLE_ON_STOP = 3;
	public static final int LIFECYCLE_ON_DESTROY = 4;

	private long mainThreadId;

	private TiUrl baseUrl;
	private String currentUrl;
	private boolean launchContext;
	private boolean serviceContext; // Contexts created for Ti services won't have associated activities.

	private WeakReference<Activity> weakActivity;
	private TiEvaluator	tiEvaluator;
	private TiApplication tiApp;
	protected KrollContext krollContext;

	private TiWeakList<OnLifecycleEvent> lifecycleListeners;
	private TiWeakList<OnServiceLifecycleEvent> serviceLifecycleListeners;

	public static interface OnLifecycleEvent {
		void onStart(Activity activity);
		void onResume(Activity activity);
		void onPause(Activity activity);
		void onStop(Activity activity);
		void onDestroy(Activity activity);
	}

	public static interface OnServiceLifecycleEvent {
		void onDestroy(Service service);
	}

	public TiContext(Activity activity, String baseUrl)
	{
		this.mainThreadId = Looper.getMainLooper().getThread().getId();
		if (activity != null) {
			this.tiApp = (TiApplication) activity.getApplication();
		} else {
			this.tiApp = TiApplication.getInstance();
		}
		this.weakActivity = new WeakReference<Activity>(activity);
		lifecycleListeners = new TiWeakList<OnLifecycleEvent>(true);
		if (baseUrl == null) {
			baseUrl = TiC.URL_APP_PREFIX;
		} else if (!baseUrl.endsWith("/")) {
			baseUrl += "/";
		}
		this.baseUrl = new TiUrl(baseUrl, null);

		if (activity instanceof TiActivity) {
			((TiActivity)activity).addTiContext(this);
		}

		if (DBG) {
			Log.e(LCAT, "BaseURL for context is " + baseUrl);
		}
	}

	public boolean isUIThread() {
		return Thread.currentThread().getId() == mainThreadId;
	}

	public TiEvaluator getJSContext() {
		return tiEvaluator;
	}

	public void setJSContext(TiEvaluator evaluator) {
		if (DBG) {
			Log.d(LCAT, "Setting JS Context on " + this + " to " + evaluator);
		}
		tiEvaluator = evaluator;
	}

	public KrollBridge getKrollBridge() {
		if (tiEvaluator instanceof KrollBridge) {
			return (KrollBridge)tiEvaluator;
		} else if (tiEvaluator instanceof TiContext) {
			return ((TiContext)tiEvaluator).getKrollBridge();
		}
		return null;
	}

	public Activity getActivity()
	{
		if (weakActivity == null) return null;
		Activity activity = weakActivity.get();
		return activity;
	}

	public void setActivity(Activity activity)
	{
		if (activity instanceof TiActivity) {
			((TiActivity)activity).addTiContext(this);
		}
		weakActivity = new WeakReference<Activity>(activity);
	}

	public TiApplication getTiApp() 
	{
		return tiApp;
	}

	public TiRootActivity getRootActivity()
	{
		return getTiApp().getRootActivity();
	}

	public TiFileHelper getTiFileHelper()
	{
		return new TiFileHelper(getTiApp());
	}

	public String resolveUrl(String path)
	{
		return resolveUrl(null, path);
	}

	public String resolveUrl(String scheme, String path)
	{
		return baseUrl.resolve(this, baseUrl.baseUrl, path, scheme);
	}

	public String resolveUrl(String scheme, String path, String relativeTo)
	{
		return baseUrl.resolve(this, relativeTo, path, scheme);
	}

	public String getBaseUrl()
	{
		return baseUrl.baseUrl;
	}

	public String getCurrentUrl()
	{
		return currentUrl;
	}

	// Javascript Support

	public Object evalFile(String filename, Messenger messenger, int messageId)
		throws IOException
	{
		Object result = null;
		this.currentUrl = filename;
		TiEvaluator jsContext = getJSContext();
		if (jsContext == null) {
			if (DBG) {
				Log.w(LCAT, "Cannot eval file '" + filename + "'. Context has been released already.");
			}
			return null;
		}

		result = jsContext.evalFile(filename);
		if (messenger != null) {
			try {
				Message msg = Message.obtain();
				msg.what = messageId;
				messenger.send(msg);
				if (DBG) {
					Log.d(LCAT, "Notifying caller that evalFile has completed");
				}
			} catch(RemoteException e) {
				Log.w(LCAT, "Failed to notify caller that eval completed");
			}
		}
		return result;
	}

	public Object evalFile(String filename)
		throws IOException
	{
		return evalFile(filename, null, -1);
	}

	public Object evalJS(String src)
	{
		TiEvaluator evaluator = getJSContext();
		if (evaluator == null)
		{
			Log.e(LCAT,"on evalJS, evaluator is null and shouldn't be");
		}
		return evaluator.evalJS(src);
	}

	@Override
	public Scriptable getScope()
	{
		if (tiEvaluator != null) {
			return tiEvaluator.getScope();
		}
		return null;
	}

	public void addOnLifecycleEventListener(OnLifecycleEvent listener)
	{
		lifecycleListeners.add(new WeakReference<OnLifecycleEvent>(listener));
	}

	public void addOnServiceLifecycleEventListener(OnServiceLifecycleEvent listener)
	{
		serviceLifecycleListeners.add(new WeakReference<OnServiceLifecycleEvent>(listener));
	}

	public void removeOnLifecycleEventListener(OnLifecycleEvent listener)
	{
		lifecycleListeners.remove(listener);
	}

	public void removeOnServiceLifecycleEventListener(OnServiceLifecycleEvent listener)
	{
		serviceLifecycleListeners.remove(listener);
	}

	public void fireLifecycleEvent(Activity activity, int which)
	{
		synchronized (lifecycleListeners.synchronizedList()) {
			for (OnLifecycleEvent listener : lifecycleListeners.nonNull()) {
				try {
					fireLifecycleEvent(activity, listener, which);
				} catch (Throwable t) {
					Log.e(LCAT, "Error dispatching lifecycle event: " + t.getMessage(), t);
				}
			}
		}
	}

	protected void fireLifecycleEvent(Activity activity, OnLifecycleEvent listener, int which)
	{
		switch (which) {
			case LIFECYCLE_ON_START: listener.onStart(activity); break;
			case LIFECYCLE_ON_RESUME: listener.onResume(activity); break;
			case LIFECYCLE_ON_PAUSE: listener.onPause(activity); break;
			case LIFECYCLE_ON_STOP: listener.onStop(activity); break;
			case LIFECYCLE_ON_DESTROY: listener.onDestroy(activity); break;
		}
	}

	public void dispatchOnServiceDestroy(Service service)
	{
		synchronized (serviceLifecycleListeners) {
			for (OnServiceLifecycleEvent listener : serviceLifecycleListeners.nonNull()) {
				try {
					listener.onDestroy(service);
				} catch (Throwable t) {
					Log.e(LCAT, "Error dispatching service onDestroy  event: " + t.getMessage(), t);
				}
			}
		}
	}

	@Override
	public void error(String message, String sourceName, int line, String lineSource, int lineOffset)
	{
		TiJSErrorDialog.openErrorDialog(getActivity(),
			"Error", message, sourceName, line, lineSource, lineOffset);
	}

	@Override
	public EvaluatorException runtimeError(String message, String sourceName, int line, String lineSource, int lineOffset)
	{
		TiJSErrorDialog.openErrorDialog(getActivity(),
			"Runtime Error", message, sourceName, line, lineSource, lineOffset);
		return null;
	}

	@Override
	public void warning(String message, String sourceName, int line, String lineSource, int lineOffset)
	{
		TiJSErrorDialog.openErrorDialog(getActivity(),
			"Warning", message, sourceName, line, lineSource, lineOffset);
	}

	public static TiContext createTiContext(Activity activity, String baseUrl)
	{
		return createTiContext(activity, baseUrl, null);
	}

	public static TiContext createTiContext(Activity activity, String baseUrl, String loadFile)
	{
		TiContext tic = new TiContext(activity, baseUrl);
		KrollContext kroll = KrollContext.createContext(tic, loadFile);
		tic.setKrollContext(kroll);
		KrollBridge krollBridge = new KrollBridge(kroll);
		tic.setJSContext(krollBridge);
		return tic;
	}

	public KrollContext getKrollContext()
	{
		return krollContext;
	}

	public void setKrollContext(KrollContext krollContext)
	{
		this.krollContext = krollContext;
	}

	public static TiContext getCurrentTiContext()
	{
		KrollContext currentCtx = KrollContext.getCurrentKrollContext();
		if (currentCtx == null) {
			return null;
		}
		return currentCtx.getTiContext();
	}

	public void release()
	{
		if (tiEvaluator != null && tiEvaluator instanceof KrollBridge) {
			((KrollBridge)tiEvaluator).release();
			tiEvaluator = null;
		}
		if (lifecycleListeners != null) {
			lifecycleListeners.clear();
		}
		if (serviceLifecycleListeners != null) {
			serviceLifecycleListeners.clear();
		}
	}

	public boolean isServiceContext() 
	{
		return serviceContext;
	}

	public void setServiceContext(boolean value)
	{
		serviceContext = true;
		if (value && serviceLifecycleListeners == null ) {
			serviceLifecycleListeners = new TiWeakList<OnServiceLifecycleEvent>(true);
		}
	}

	public boolean isLaunchContext()
	{
		return launchContext;
	}

	public void setLaunchContext(boolean launchContext)
	{
		this.launchContext = launchContext;
	}

	public ContextWrapper getAndroidContext()
	{
		if (weakActivity == null || weakActivity.get() == null) {
			return tiApp;
		}
		return weakActivity.get();
	}

	public void setBaseUrl(String baseUrl)
	{
		this.baseUrl.baseUrl = baseUrl;
		if (this.baseUrl.baseUrl == null) {
			this.baseUrl.baseUrl = TiC.URL_APP_PREFIX;
		}
	}
}
