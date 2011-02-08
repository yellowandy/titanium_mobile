/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2011 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package ti.modules.titanium.ui;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollProxy;
import org.appcelerator.titanium.TiActivity;
import org.appcelerator.titanium.TiActivityWindow;
import org.appcelerator.titanium.TiActivityWindows;
import org.appcelerator.titanium.TiBaseActivity;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.TiContext;
import org.appcelerator.titanium.TiMessageQueue;
import org.appcelerator.titanium.TiModalActivity;
import org.appcelerator.titanium.proxy.ActivityProxy;
import org.appcelerator.titanium.proxy.TiViewProxy;
import org.appcelerator.titanium.proxy.TiWindowProxy;
import org.appcelerator.titanium.util.Log;
import org.appcelerator.titanium.util.TiBindingHelper;
import org.appcelerator.titanium.util.TiConfig;
import org.appcelerator.titanium.util.TiConvert;
import org.appcelerator.titanium.util.TiFileHelper;
import org.appcelerator.titanium.util.TiPropertyResolver;
import org.appcelerator.titanium.util.TiUIHelper;
import org.appcelerator.titanium.util.TiUrl;
import org.appcelerator.titanium.view.ITiWindowHandler;
import org.appcelerator.titanium.view.TiCompositeLayout;
import org.appcelerator.titanium.view.TiCompositeLayout.LayoutArrangement;
import org.appcelerator.titanium.view.TiUIView;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.view.View;
import android.view.View.OnFocusChangeListener;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;

public class TiUIWindow extends TiUIView
	implements Handler.Callback, TiActivityWindow
{
	private static final String LCAT = "TiUIWindow";
	private static final boolean DBG = TiConfig.LOGD;

	private static final int WINDOW_ZINDEX = Integer.MAX_VALUE - 2; // Arbitrary number;
	private static final int MSG_ACTIVITY_CREATED = 1000;
	private static final int MSG_ANIMATE = 100;

	// Intent.FLAG_ACTIVITY_NO_ANIMATION not available in API 4
	private static final int INTENT_FLAG_ACTIVITY_NO_ANIMATION = 65536;
	private static final String[] NEW_ACTIVITY_REQUIRED_KEYS = {
		TiC.PROPERTY_FULLSCREEN, TiC.PROPERTY_NAV_BAR_HIDDEN,
		TiC.PROPERTY_MODAL, TiC.PROPERTY_WINDOW_SOFT_INPUT_MODE
	};
	private static final String WINDOW_ID_PREFIX = "window$";
	
	protected String activityKey;
	protected Activity windowActivity;
	protected TiContext windowContext;
	protected String windowUrl;
	protected int windowId;
	protected TiCompositeLayout lightWindow;

	protected boolean lightWeight, newActivity, animate;
	protected TiPropertyResolver resolver;
	protected Handler handler;

	protected Messenger messenger;
	protected int messageId;
	protected int lastWidth, lastHeight;

	private static AtomicInteger idGenerator;

	public TiUIWindow(TiViewProxy proxy, KrollDict options, Messenger messenger, int messageId)
	{
		super(proxy);
		animate = true;
		//proxy.setModelListener(this);
		if (idGenerator == null) {
			idGenerator = new AtomicInteger(0);
		}
		this.messenger = messenger;
		this.messageId = messageId;
		this.handler = new Handler(Looper.getMainLooper(), this);

		this.lastWidth = LayoutParams.FILL_PARENT;
		this.lastHeight = LayoutParams.FILL_PARENT;

		resolver = new TiPropertyResolver(options, proxy.getProperties());
		newActivity = requiresNewActivity();
		if (!newActivity && options != null && options.containsKey(TiC.PROPERTY_TAB_OPEN)) {
			newActivity = TiConvert.toBoolean(options, TiC.PROPERTY_TAB_OPEN);
		}
		lightWeight = !newActivity;
		initContext();
		if (newActivity) {
			createNewActivity();
		} else {
			lightWindow = new TiCompositeLayout(proxy.getContext(), getLayoutArrangement());
			layoutParams.autoFillsHeight = true;
			layoutParams.autoFillsWidth = true;

			setNativeView(lightWindow);
			proxy.setModelListener(this);
			handleWindowCreated();
			handleBooted();
		}
	}

	public TiUIWindow(TiViewProxy proxy, Activity activity)
	{
		super(proxy);
		if (idGenerator == null) {
			idGenerator = new AtomicInteger(0);
		}

		newActivity = false;
		windowActivity = activity;
		lightWeight = false;

		this.handler = new Handler(Looper.getMainLooper(), this);
		initContext();
		handleWindowCreated();
		handleBooted();
	}

	protected void initContext()
	{
		// if url, create a new context.
		if (proxy.hasProperty(TiC.PROPERTY_URL)) {
			if (newActivity) {
				windowId = TiActivityWindows.addWindow(this);
			}
			String url = TiConvert.toString(proxy.getProperty(TiC.PROPERTY_URL));
			String baseUrl = proxy.getTiContext().getBaseUrl();
			TiUrl tiUrl = TiUrl.normalizeWindowUrl(baseUrl, url);
			windowUrl = tiUrl.url;
			Activity activity = null;
			if (!newActivity) {
				activity = windowActivity;
				if (activity == null) {
					activity = proxy.getTiContext().getActivity();
				}
			}
			windowContext = TiContext.createTiContext(activity, tiUrl.baseUrl, tiUrl.url);
			ActivityProxy activityProxy = ((TiWindowProxy) proxy).getActivity(windowContext);
			if (windowActivity != null) {
				bindWindowActivity(windowContext, windowActivity);
			}
			TiBindingHelper.bindCurrentWindowAndActivity(windowContext, proxy, activityProxy);
		} else if (!lightWeight) {
			windowContext = TiContext.createTiContext(windowActivity, proxy.getTiContext().getBaseUrl(), proxy.getTiContext().getCurrentUrl());
			ActivityProxy activityProxy = ((TiWindowProxy) proxy).getActivity(windowContext);
			if (windowActivity != null) {
				bindWindowActivity(windowContext, windowActivity);
			}
			if (newActivity) {
				windowId = TiActivityWindows.addWindow(this);
			}
			TiBindingHelper.bindCurrentWindowAndActivity(windowContext, proxy, activityProxy);
			bindProxies();
		} else {
			bindWindowActivity(proxy.getTiContext(), proxy.getTiContext().getActivity());
		}
		if (!newActivity && !lightWeight) {
			proxy.switchContext(windowContext);
		}
	}

	protected void createNewActivity()
	{
		Activity activity = proxy.getTiContext().getActivity();
		Intent intent = createIntent(activity);
		KrollDict d = resolver.findProperty(TiC.PROPERTY_ANIMATED);
		if (d != null) {
			animate = TiConvert.toBoolean(d, TiC.PROPERTY_ANIMATED);
		}
		if (!animate) {
			intent.addFlags(INTENT_FLAG_ACTIVITY_NO_ANIMATION);
			intent.putExtra(TiC.PROPERTY_ANIMATE, false);
			activity.startActivity(intent);
			TiUIHelper.overridePendingTransition(activity);
		} else {
			activity.startActivity(intent);
		}
		proxy.switchContext(windowContext);
	}

	public void windowCreated(TiBaseActivity activity)
	{
		windowActivity = activity;
		windowContext.setActivity(windowActivity);
		bindWindowActivity(windowContext, windowActivity);
		bindProxies();
		handleWindowCreated();
		TiMessageQueue.getMainMessageQueue().stopBlocking();
	}
	
	protected void handleWindowCreated()
	{
		if (windowUrl != null) {
			try {
				windowContext.evalFile(windowUrl);
			} catch (IOException e) {
				Log.e(LCAT, "Error opening URL: " + windowUrl, e);
			}
		}
	}

	protected ActivityProxy bindWindowActivity(TiContext tiContext, Activity activity)
	{
		ActivityProxy activityProxy = null;
		if (activity instanceof TiBaseActivity) {
			activityProxy = ((TiBaseActivity)activity).getActivityProxy();
		}
		if (activityProxy == null) {
			activityProxy = ((TiWindowProxy) proxy).getActivity(tiContext);
			activityProxy.setActivity(tiContext, activity);
			if (activity instanceof TiBaseActivity) {
				((TiBaseActivity)activity).setActivityProxy(activityProxy);
			}
		}
		return activityProxy;
	}

	protected void bindProxies()
	{
		if (windowActivity instanceof TiBaseActivity) {
			TiBaseActivity tiActivity = (TiBaseActivity)windowActivity;
			TiWindowProxy windowProxy = (TiWindowProxy)proxy;
			tiActivity.setActivityProxy(windowProxy.getActivity(proxy.getTiContext()));
			tiActivity.setWindowProxy(windowProxy);
		}
	}

	protected void handleBooted()
	{
		//TODO unique key per window, params for intent
		activityKey = WINDOW_ID_PREFIX + idGenerator.incrementAndGet();
		View layout = getLayout();
		layout.setClickable(true);
		registerForTouch(layout);
		layout.setOnFocusChangeListener(new OnFocusChangeListener() {
			public void onFocusChange(View view, boolean hasFocus) {
				proxy.fireEvent(hasFocus ? TiC.EVENT_FOCUS : TiC.EVENT_BLUR, new KrollDict());
			}
		});

		if (messenger != null) {
			Message msg = Message.obtain();
			msg.what = messageId;
			try {
				messenger.send(msg);
			} catch (RemoteException e) {
				Log.e(LCAT, "Unable to send message: " + e.getMessage(), e);
			} finally {
				messenger = null;
			}
		}
		if (lightWeight) {
			ITiWindowHandler windowHandler = proxy.getTiContext().getTiApp().getWindowHandler();
			if (windowHandler != null) {
				TiCompositeLayout.LayoutParams params = getLayoutParams();
				params.optionZIndex = WINDOW_ZINDEX;
				windowHandler.addWindow(lightWindow, params);
			}
			handler.obtainMessage(MSG_ANIMATE).sendToTarget();
		} else if (windowActivity != null && windowActivity instanceof TiActivity) {
			layout.requestFocus();
			((TiActivity) windowActivity).fireInitialFocus(); 
		}
	}

	public void close(KrollDict options) 
	{
		KrollDict props = proxy.getProperties();
		TiPropertyResolver resolver = new TiPropertyResolver(options, props);
		props = resolver.findProperty(TiC.PROPERTY_ANIMATED);
		boolean animateOnClose = animate;
		if (props != null && props.containsKey(TiC.PROPERTY_ANIMATED)) {
			animateOnClose = props.getBoolean(TiC.PROPERTY_ANIMATED);
		}

		if (!lightWeight) {
			if (windowActivity != null) {
				if (!animateOnClose) {
					windowActivity.finish();
					TiUIHelper.overridePendingTransition(windowActivity);
				} else {
					windowActivity.finish();
				}
				windowActivity = null;
			}
		} else {
			if (lightWindow != null) {
				// Only fire close event for lightweights.  For heavyweights, the
				// Activity finish will result in close firing.
				KrollDict data = new KrollDict();
				data.put(TiC.EVENT_PROPERTY_SOURCE, proxy);
				proxy.fireEvent(TiC.EVENT_CLOSE, data);
				ITiWindowHandler windowHandler = proxy.getTiContext().getTiApp().getWindowHandler();
				if (windowHandler != null) {
					windowHandler.removeWindow(lightWindow);
				}
				lightWindow.removeAllViews();
				lightWindow = null;
			}
		}
	}

	@Override
	public boolean handleMessage(Message msg)
	{
		switch (msg.what) {
			case MSG_ACTIVITY_CREATED :
				if (DBG) {
					Log.d(LCAT, "Received Activity creation message");
				}
				if (windowActivity == null) {
					windowActivity = (Activity) msg.obj;
				}
				proxy.setModelListener(this);
				handleBooted();
				return true;
			case MSG_ANIMATE : {
				animate();
				return true;
			}
		}
		return false;
	}

	@Override
	public View getNativeView()
	{
		View v = super.getNativeView();
		if (!lightWeight) {
			v = getLayout();
		}
		return v;
	}

	public View getLayout()
	{
		View layout = nativeView;
		if (!lightWeight) {
			TiActivity tia = (TiActivity) windowActivity;
			if (tia == null) {
				return null;
			}
			layout = tia.getLayout();
		}
		return layout;
	}

	private void handleBackgroundColor(KrollDict d)
	{
		if (proxy.getProperty(TiC.PROPERTY_BACKGROUND_COLOR) != null) {
			Integer bgColor = TiConvert.toColor(d, TiC.PROPERTY_BACKGROUND_COLOR);
			Drawable cd = new ColorDrawable(bgColor);
			if (lightWeight) {
				nativeView.setBackgroundDrawable(cd);
			} else {
				Window w = windowActivity.getWindow();
				w.setBackgroundDrawable(cd);
			}
		} else {
			Log.w(LCAT, "Unable to set opacity w/o a backgroundColor");
		}
	}

	@Override
	public void processProperties(KrollDict d)
	{
		// Prefer image to color.
		if (d.containsKey(TiC.PROPERTY_BACKGROUND_IMAGE)) {
			String path = proxy.getTiContext().resolveUrl(null, TiConvert.toString(d, TiC.PROPERTY_BACKGROUND_IMAGE));
			TiFileHelper tfh = new TiFileHelper(proxy.getContext().getApplicationContext());
			Drawable bd = tfh.loadDrawable(proxy.getTiContext(), path, false);
			if (bd != null) {
				if (!lightWeight) {
					windowActivity.getWindow().setBackgroundDrawable(bd);
				} else {
					nativeView.setBackgroundDrawable(bd);
				}
			}
		} else if (d.containsKey(TiC.PROPERTY_BACKGROUND_COLOR)) {
			ColorDrawable bgColor = TiConvert.toColorDrawable(d, TiC.PROPERTY_BACKGROUND_COLOR);
			if (!lightWeight) {
				windowActivity.getWindow().setBackgroundDrawable(bgColor);
			} else {
				nativeView.setBackgroundDrawable(bgColor);
			}
		}
		if (d.containsKey(TiC.PROPERTY_TITLE)) {
			String title = TiConvert.toString(d, TiC.PROPERTY_TITLE);
			if (windowActivity != null) {
				windowActivity.setTitle(title);
			} else {
				proxy.getTiContext().getActivity().setTitle(title);
			}
		}

		// Don't allow default processing.
		d.remove(TiC.PROPERTY_BACKGROUND_IMAGE);
		d.remove(TiC.PROPERTY_BACKGROUND_COLOR);
		super.processProperties(d);
	}

	@Override
	public void propertyChanged(String key, Object oldValue, Object newValue, KrollProxy proxy)
	{
		if (key.equals(TiC.PROPERTY_BACKGROUND_IMAGE)) {
			if (newValue != null) {
				String path = proxy.getTiContext().resolveUrl(null, TiConvert.toString(newValue));
				TiFileHelper tfh = new TiFileHelper(proxy.getTiContext().getTiApp());
				Drawable bd = tfh.loadDrawable(proxy.getTiContext(), path, false);
				if (bd != null) {
					if (!lightWeight) {
						windowActivity.getWindow().setBackgroundDrawable(bd);
					} else {
						nativeView.setBackgroundDrawable(bd);
					}
				}
			} else {
				handleBackgroundColor(proxy.getProperties());
			}
		} else if (key.equals(TiC.PROPERTY_BACKGROUND_COLOR)) {
			KrollDict d = proxy.getProperties();
			handleBackgroundColor(d);
		} else if (key.equals(TiC.PROPERTY_WIDTH) || key.equals(TiC.PROPERTY_HEIGHT)) {
			Window w = proxy.getTiContext().getActivity().getWindow();
			int width = lastWidth;
			int height = lastHeight;

			if (key.equals(TiC.PROPERTY_WIDTH)) {
				if (newValue != null) {
					width = TiConvert.toInt(newValue);
				} else {
					width = LayoutParams.FILL_PARENT;
				}
			}
			if (key.equals(TiC.PROPERTY_HEIGHT)) {
				if (newValue != null) {
					height = TiConvert.toInt(newValue);
				} else {
					height = LayoutParams.FILL_PARENT;
				}
			}
			w.setLayout(width, height);

			lastWidth = width;
			lastHeight = height;
		} else if (key.equals(TiC.PROPERTY_TITLE)) {
			String title = TiConvert.toString(newValue);
			if (windowActivity != null) {
				windowActivity.setTitle(title);
			} else {
				proxy.getTiContext().getActivity().setTitle(title);
			}
		} else if (key.equals(TiC.PROPERTY_LAYOUT)) {
			if (!lightWeight) {
				TiCompositeLayout layout = null;
				if (windowActivity instanceof TiActivity) {
					layout = ((TiActivity)windowActivity).getLayout();
				} else if (windowActivity instanceof TiTabActivity) {
					layout = ((TiTabActivity)windowActivity).getLayout();
				}
				if (layout != null) {
					layout.setLayoutArrangement(TiConvert.toString(newValue));
				}
			}
		} else {
			super.propertyChanged(key, oldValue, newValue, proxy);
		}
	}

	protected boolean requiresNewActivity()
	{
		return resolver.hasAnyOf(NEW_ACTIVITY_REQUIRED_KEYS);
	}

	protected LayoutArrangement getLayoutArrangement()
	{
		LayoutArrangement arrangement = LayoutArrangement.DEFAULT;
		KrollDict d = resolver.findProperty(TiC.PROPERTY_LAYOUT);
		if (d != null) {
			if (TiConvert.toString(d, TiC.PROPERTY_LAYOUT).equals(TiC.LAYOUT_VERTICAL)) {
				arrangement = LayoutArrangement.VERTICAL;
			} else if (TiConvert.toString(d, TiC.PROPERTY_LAYOUT).equals(TiC.LAYOUT_HORIZONTAL)) {
				arrangement = LayoutArrangement.HORIZONTAL;
			}
		}
		return arrangement;
	}

	protected Intent createIntent(Activity activity)
	{
		Intent intent = new Intent(activity, TiActivity.class);

		KrollDict props = resolver.findProperty(TiC.PROPERTY_FULLSCREEN);
		if (props != null && props.containsKey(TiC.PROPERTY_FULLSCREEN)) {
			intent.putExtra(TiC.PROPERTY_FULLSCREEN, TiConvert.toBoolean(props, TiC.PROPERTY_FULLSCREEN));
		}
		props = resolver.findProperty(TiC.PROPERTY_NAV_BAR_HIDDEN);
		if (props != null && props.containsKey(TiC.PROPERTY_NAV_BAR_HIDDEN)) {
			intent.putExtra(TiC.PROPERTY_NAV_BAR_HIDDEN, TiConvert.toBoolean(props, TiC.PROPERTY_NAV_BAR_HIDDEN));
		}
		props = resolver.findProperty(TiC.PROPERTY_MODAL);
		if (props != null && props.containsKey(TiC.PROPERTY_MODAL)) {
			intent.setClass(activity, TiModalActivity.class);
			intent.putExtra(TiC.PROPERTY_MODAL, TiConvert.toBoolean(props, TiC.PROPERTY_MODAL));
		}
		props = resolver.findProperty(TiC.PROPERTY_URL);
		if (props != null && props.containsKey(TiC.PROPERTY_URL)) {
			intent.putExtra(TiC.PROPERTY_URL, TiConvert.toString(props, TiC.PROPERTY_URL));
		}
		props = resolver.findProperty(TiC.PROPERTY_LAYOUT);
		if (props != null && props.containsKey(TiC.PROPERTY_LAYOUT)) {
			intent.putExtra(TiC.INTENT_PROPERTY_LAYOUT, TiConvert.toString(props, TiC.PROPERTY_LAYOUT));
		}
		props = resolver.findProperty(TiC.PROPERTY_WINDOW_SOFT_INPUT_MODE);
		if (props != null && props.containsKey(TiC.PROPERTY_WINDOW_SOFT_INPUT_MODE)) {
			intent.putExtra(TiC.PROPERTY_WINDOW_SOFT_INPUT_MODE, TiConvert.toInt(props, TiC.PROPERTY_WINDOW_SOFT_INPUT_MODE));
		}

		boolean finishRoot = false;
		props = resolver.findProperty(TiC.PROPERTY_EXIT_ON_CLOSE);
		if (props != null && props.containsKey(TiC.PROPERTY_EXIT_ON_CLOSE)) {
			finishRoot = TiConvert.toBoolean(props, TiC.PROPERTY_EXIT_ON_CLOSE);
		}

		intent.putExtra(TiC.INTENT_PROPERTY_FINISH_ROOT, finishRoot);
		Messenger messenger = new Messenger(handler);
		intent.putExtra(TiC.INTENT_PROPERTY_MESSENGER, messenger);
		intent.putExtra(TiC.INTENT_PROPERTY_MSG_ACTIVITY_CREATED_ID, MSG_ACTIVITY_CREATED);
		intent.putExtra(TiC.INTENT_PROPERTY_USE_ACTIVITY_WINDOW, true);
		intent.putExtra(TiC.INTENT_PROPERTY_WINDOW_ID, windowId);
		return intent;
	}

	@Override
	public void setOpacity(float opacity)
	{
		View view = null;
		if (!lightWeight) {
			view = windowActivity.getWindow().getDecorView();
		} else {
			view = nativeView;
		}
		
		super.setOpacity(view, opacity);
	}

	@Override
	public void release()
	{
		super.release();
		if (lightWindow != null) {
			lightWindow.removeAllViews();
			lightWindow = null;
		}
		messenger = null;
		handler = null;
		windowActivity = null;
	}
	
	public Activity getActivity() {
		return windowActivity;
	}
}
