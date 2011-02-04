/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2010 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package org.appcelerator.titanium.proxy;

import java.lang.ref.WeakReference;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.TreeSet;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollProxy;
import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.TiContext;
import org.appcelerator.titanium.kroll.KrollCallback;
import org.appcelerator.titanium.util.AsyncResult;
import org.appcelerator.titanium.util.Log;
import org.appcelerator.titanium.util.TiAnimationBuilder;
import org.appcelerator.titanium.util.TiConfig;
import org.appcelerator.titanium.util.TiConvert;
import org.appcelerator.titanium.util.TiRHelper;
import org.appcelerator.titanium.view.TiAnimation;
import org.appcelerator.titanium.view.TiUIView;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.view.View;

@Kroll.proxy(propertyAccessors={
	// background properties
	"backgroundImage", "backgroundSelectedImage", "backgroundFocusedImage",
	"backgroundDisabledImage", "backgroundColor", "backgroundSelectedColor",
	"backgroundFocusedColor", "backgroundDisabledColor", "backgroundPadding",
	
	// border properties
	"borderColor", "borderRadius", "borderWidth",
	
	// layout / dimension (size/width/height have custom accessors)
	"left", "top", "right", "bottom", "layout", "zIndex",
	
	// others
	"focusable", "touchEnabled", "visible", "enabled", "opacity",
	"softKeyboardOnFocus", "transform"
	
})
public abstract class TiViewProxy extends KrollProxy implements Handler.Callback
{
	private static final String LCAT = "TiViewProxy";
	private static final boolean DBG = TiConfig.LOGD;

	private static final int MSG_FIRST_ID = KrollProxy.MSG_LAST_ID + 1;

	private static final int MSG_GETVIEW = MSG_FIRST_ID + 100;
	private static final int MSG_ADD_CHILD = MSG_FIRST_ID + 102;
	private static final int MSG_REMOVE_CHILD = MSG_FIRST_ID + 103;
	private static final int MSG_BLUR = MSG_FIRST_ID + 104;
	private static final int MSG_FOCUS = MSG_FIRST_ID + 105;
	private static final int MSG_SHOW = MSG_FIRST_ID + 106;
	private static final int MSG_HIDE = MSG_FIRST_ID + 107;
	private static final int MSG_ANIMATE = MSG_FIRST_ID + 108;
	private static final int MSG_TOIMAGE = MSG_FIRST_ID + 109;
	private static final int MSG_GETSIZE = MSG_FIRST_ID + 110;
	private static final int MSG_GETCENTER = MSG_FIRST_ID + 111;

	protected static final int MSG_LAST_ID = MSG_FIRST_ID + 999;

	protected ArrayList<TiViewProxy> children;
	protected WeakReference<TiViewProxy> parent;

	protected TiUIView view;
	protected TiAnimationBuilder pendingAnimation;

	public TiViewProxy(TiContext tiContext) {
		super(tiContext);
	}

	@Override
	public void handleCreationDict(KrollDict options) {
		options = handleStyleOptions(options);
		// lang conversion table
		KrollDict langTable = getLangConversionTable();
		if (langTable != null) {
			Activity activity = context.getActivity();
			for (String key : langTable.keySet()) {
				// if we have it already, ignore
				if (options.containsKey(key) == false) {
					String convertKey = (String) langTable.get(key);
					String langKey = (String) options.get(convertKey);
					if (langKey != null) {
						try {
							options.put(key, activity.getString(TiRHelper.getResource("string." + langKey)));
						}
						catch (TiRHelper.ResourceNotFoundException e) {}
					}
				}
			}
		}

		options = handleStyleOptions(options);
		super.handleCreationDict(options);
		
		eventManager.addOnEventChangeListener(this);
	}
	
	protected String getBaseUrlForStylesheet() {
		String baseUrl = getTiContext().getCurrentUrl();
		if (baseUrl == null) {
			baseUrl = "app://app.js";
		}
		
		int idx = baseUrl.lastIndexOf("/");
		if (idx != -1) {
			baseUrl = baseUrl.substring(idx + 1).replace(".js", "");
		}
		return baseUrl;
	}
	
	protected KrollDict handleStyleOptions(KrollDict options) {
		String viewId = getProxyId();
		TreeSet<String> styleClasses = new TreeSet<String>();
		styleClasses.add(getShortAPIName().toLowerCase());
		
		if (options.containsKey(TiC.PROPERTY_ID)) {
			viewId = TiConvert.toString(options, TiC.PROPERTY_ID);
		}
		if (options.containsKey(TiC.PROPERTY_CLASS_NAME)) {
			String className = TiConvert.toString(options, TiC.PROPERTY_CLASS_NAME);
			for (String clazz : className.split(" ")) {
				styleClasses.add(clazz);
			}
		}
		if (options.containsKey(TiC.PROPERTY_CLASS_NAMES)) {
			Object c = options.get(TiC.PROPERTY_CLASS_NAMES);
			if (c.getClass().isArray()) {
				int length = Array.getLength(c);
				for (int i = 0; i < length; i++) {
					Object clazz = Array.get(c, i);
					if (clazz != null) {
						styleClasses.add(clazz.toString());
					}
				}
			}
		}
		
		String baseUrl = getBaseUrlForStylesheet();
		KrollDict dict = context.getTiApp().getStylesheet(baseUrl, styleClasses, viewId);

		if (DBG) {
			Log.d(LCAT, "trying to get stylesheet for base:" + baseUrl + ",classes:" + styleClasses + ",id:" + viewId + ",dict:" + dict);
		}
		if (dict != null) {
			// merge in our stylesheet details to the passed in dictionary
			// our passed in dictionary takes precedence over the stylesheet
			dict.putAll(options);
			return dict;
		}
		return options;
	}
	
	protected KrollDict getLangConversionTable() {
		// subclasses override to return a table mapping of langid keys to actual keys
		// used for specifying things like titleid vs. title so that you can localize them
		return null;
	}

	public TiAnimationBuilder getPendingAnimation() {
		return pendingAnimation;
	}

	public void clearAnimation() {
		if (pendingAnimation != null) {
			pendingAnimation = null;
		}
	}

	//This handler callback is tied to the UI thread.
	public boolean handleMessage(Message msg)
	{
		switch(msg.what) {
			case MSG_GETVIEW : {
				AsyncResult result = (AsyncResult) msg.obj;
				result.setResult(handleGetView((Activity) result.getArg()));
				return true;
			}
			case MSG_ADD_CHILD : {
				AsyncResult result = (AsyncResult) msg.obj;
				handleAdd((TiViewProxy) result.getArg());
				result.setResult(null); //Signal added.
				return true;
			}
			case MSG_REMOVE_CHILD : {
				AsyncResult result = (AsyncResult) msg.obj;
				handleRemove((TiViewProxy) result.getArg());
				result.setResult(null); //Signal removed.
				return true;
			}
			case MSG_BLUR : {
				handleBlur();
				return true;
			}
			case MSG_FOCUS : {
				handleFocus();
				return true;
			}
			case MSG_SHOW : {
				handleShow((KrollDict) msg.obj);
				return true;
			}
			case MSG_HIDE : {
				handleHide((KrollDict) msg.obj);
				return true;
			}
			case MSG_ANIMATE : {
				handleAnimate();
				return true;
			}
			case MSG_TOIMAGE: {
				AsyncResult result = (AsyncResult) msg.obj;
				result.setResult(handleToImage());
				return true;
			}
			case MSG_GETSIZE : {
				AsyncResult result = (AsyncResult) msg.obj;
				KrollDict d = null;
				if (view != null) {
					View v = view.getNativeView();
					if (v != null) {
						d = new KrollDict();
						d.put(TiC.PROPERTY_WIDTH, v.getWidth());
						d.put(TiC.PROPERTY_HEIGHT, v.getHeight());
					}
				}
				if (d == null) {
					d = new KrollDict();
					d.put(TiC.PROPERTY_WIDTH, 0);
					d.put(TiC.PROPERTY_HEIGHT, 0);
				}

				result.setResult(d);
				return true;
			}
			case MSG_GETCENTER : {
				AsyncResult result = (AsyncResult) msg.obj;
				KrollDict d = null;
				if (view != null) {
					View v = view.getNativeView();
					if (v != null) {
						d = new KrollDict();
						d.put(TiC.EVENT_PROPERTY_X, (double)v.getLeft() + (double)v.getWidth() / 2);
						d.put(TiC.EVENT_PROPERTY_Y, (double)v.getTop() + (double)v.getHeight() / 2);
					}
				}
				if (d == null) {
					d = new KrollDict();
					d.put(TiC.EVENT_PROPERTY_X, 0);
					d.put(TiC.EVENT_PROPERTY_Y, 0);
				}

				result.setResult(d);
				return true;
			}
		}
		return super.handleMessage(msg);
	}

	public Context getContext()
	{
		return getTiContext().getActivity();
	}

	@Kroll.getProperty @Kroll.method
	public KrollDict getSize()
	{
		return (KrollDict) sendBlockingUiMessage(MSG_GETSIZE, getTiContext().getActivity());
	}

	@Kroll.getProperty @Kroll.method
	public int getWidth()
	{
		if (hasProperty(TiC.PROPERTY_WIDTH)) {
			return TiConvert.toInt(getProperty(TiC.PROPERTY_WIDTH));
		}
		
		KrollDict size = getSize();
		return size.getInt(TiC.PROPERTY_WIDTH);
	}

	@Kroll.setProperty(retain=false) @Kroll.method
	public void setWidth(Object width)
	{
		setProperty(TiC.PROPERTY_WIDTH, width, true);
	}

	@Kroll.getProperty @Kroll.method
	public int getHeight()
	{
		if (hasProperty(TiC.PROPERTY_HEIGHT)) {
			return TiConvert.toInt(getProperty(TiC.PROPERTY_HEIGHT));
		}
		
		KrollDict size = getSize();
		return size.getInt(TiC.PROPERTY_HEIGHT);
	}

	@Kroll.setProperty(retain=false) @Kroll.method
	public void setHeight(Object height)
	{
		setProperty(TiC.PROPERTY_HEIGHT, height, true);
	}

	@Kroll.getProperty @Kroll.method
	public KrollDict getCenter()
	{
		return (KrollDict) sendBlockingUiMessage(MSG_GETCENTER, getTiContext().getActivity());
	}

	public void clearView()
	{
		if (view != null) {
			view.release();
		}
		view = null;
	}

	public TiUIView peekView()
	{
		return view;
	}

	public void setView(TiUIView view)
	{
		this.view = view;
	}

	public TiUIView forceCreateView(Activity activity)
	{
		view = null;
		return getView(activity);
	}

	public TiUIView getView(Activity activity)
	{
		if (activity == null) {
			activity = getTiContext().getActivity();
		}
		if(getTiContext().isUIThread()) {
			return handleGetView(activity);
		}

		return (TiUIView) sendBlockingUiMessage(MSG_GETVIEW, activity);
	}

	protected TiUIView handleGetView(Activity activity)
	{
		if (view == null) {
			if (DBG) {
				Log.d(LCAT, "getView: " + getClass().getSimpleName());
			}

			view = createView(activity);
			realizeViews(activity, view);
			view.registerForTouch();
		}
		return view;
	}

	public void realizeViews(Activity activity, TiUIView view)
	{
		setModelListener(view);

		// Use a copy so bundle can be modified as it passes up the inheritance
		// tree. Allows defaults to be added and keys removed.
		if (children != null) {
			for (TiViewProxy p : children) {
				TiUIView cv = p.getView(activity);
				view.add(cv);
			}
		}
		
		if (pendingAnimation != null) {
			handlePendingAnimation(true);
		}
	}

	public void releaseViews()
	{
		if (view != null) {
			if  (children != null) {
				for (TiViewProxy p : children) {
					p.releaseViews();
				}
			}
			view.release();
			view = null;
		}
		setModelListener(null);
	}

	public abstract TiUIView createView(Activity activity);

	@Kroll.method
	public void add(TiViewProxy child) {
		if (children == null) {
			children = new ArrayList<TiViewProxy>();
		}
		if (peekView() != null) {
			if(getTiContext().isUIThread()) {
				handleAdd(child);
				return;
			}

			sendBlockingUiMessage(MSG_ADD_CHILD, child);
		} else {
			children.add(child);
			child.parent = new WeakReference<TiViewProxy>(this);
		}
		//TODO zOrder
	}

	public void handleAdd(TiViewProxy child)
	{
		children.add(child);
		child.parent = new WeakReference<TiViewProxy>(this);
		if (view != null) {
			TiUIView cv = child.getView(getTiContext().getActivity());
			view.add(cv);
		}
	}

	@Kroll.method
	public void remove(TiViewProxy child)
	{
		if (peekView() != null) {
			if (getTiContext().isUIThread()) {
				handleRemove(child);
				return;
			}

			sendBlockingUiMessage(MSG_REMOVE_CHILD, child);
		} else {
			if (children != null) {
				children.remove(child);
				if (child.parent != null && child.parent.get() == this) {
					child.parent = null;
				}
			}
		}
	}

	public void handleRemove(TiViewProxy child)
	{
		if (children != null) {
			children.remove(child);
			if (view != null) {
				view.remove(child.peekView());
			}
		}
	}

	@Kroll.method
	public void show(@Kroll.argument(optional=true) KrollDict options)
	{
		if (getTiContext().isUIThread()) {
			handleShow(options);
		} else {
			getUIHandler().obtainMessage(MSG_SHOW, options).sendToTarget();
		}
	}

	protected void handleShow(KrollDict options) {
		if (view != null) {
			view.show();
		}
	}

	@Kroll.method
	public void hide(@Kroll.argument(optional=true) KrollDict options) {
		if (getTiContext().isUIThread()) {
			handleHide(options);
		} else {
			getUIHandler().obtainMessage(MSG_HIDE, options).sendToTarget();
		}

	}

	protected void handleHide(KrollDict options) {
		if (view != null) {
			if (pendingAnimation != null) {
				handlePendingAnimation(false);
			}
			view.hide();
		}
	}

	@Kroll.method
	public void animate(Object arg, @Kroll.argument(optional=true) KrollCallback callback)
	{
		if (arg instanceof KrollDict) {
			KrollDict options = (KrollDict) arg;

			pendingAnimation = new TiAnimationBuilder();
			pendingAnimation.applyOptions(options);
			if (callback != null) {
				pendingAnimation.setCallback(callback);
			}
		} else if (arg instanceof TiAnimation) {
			TiAnimation anim = (TiAnimation) arg;
			pendingAnimation = new TiAnimationBuilder();
			pendingAnimation.applyAnimation(anim);
		} else {
			throw new IllegalArgumentException("Unhandled argument to animate: " + arg.getClass().getSimpleName());
		}
		handlePendingAnimation(false);
	}

	public void handlePendingAnimation(boolean forceQueue) {
		if (pendingAnimation != null && peekView() != null) {
			if (forceQueue || !getTiContext().isUIThread()) {
				getUIHandler().obtainMessage(MSG_ANIMATE).sendToTarget();
			} else {
				handleAnimate();
			}
		}
	}

	protected void handleAnimate() {
		TiUIView tiv = peekView();

		if (tiv != null) {
			tiv.animate();
		}
	}

	@Kroll.method
	public void blur()
	{
		if (getTiContext().isUIThread()) {
			handleBlur();
		} else {
			getUIHandler().sendEmptyMessage(MSG_BLUR);
		}
	}

	protected void handleBlur() {
		if (view != null) {
			view.blur();
		}
	}

	@Kroll.method
	public void focus()
	{
		if (getTiContext().isUIThread()) {
			handleFocus();
		} else {
			getUIHandler().sendEmptyMessage(MSG_FOCUS);
		}
	}

	protected void handleFocus() {
		if (view != null) {
			view.focus();
		}
	}

	@Kroll.method
	public KrollDict toImage() {
		if (getTiContext().isUIThread()) {
			return handleToImage();
		} else {
			return (KrollDict) sendBlockingUiMessage(MSG_TOIMAGE, getTiContext().getActivity());
		}
	}

	protected KrollDict handleToImage() {
		return getView(getTiContext().getActivity()).toImage();
	}

	@Override
	public boolean fireEvent(String eventName, KrollDict data) {
		if (data == null) data = new KrollDict();
		boolean handled = super.fireEvent(eventName, data);

		if (parent != null && parent.get() != null) {
			boolean parentHandled = parent.get().fireEvent(eventName, data);
			handled = handled || parentHandled;
		}
		return handled;
	}

	@Kroll.getProperty @Kroll.method
	public TiViewProxy getParent() {
		if (this.parent == null) { return null; }
		return this.parent.get();
	}

	public void setParent(TiViewProxy parent) {
		this.parent = new WeakReference<TiViewProxy>(parent);
	}

	@Override
	public TiContext switchContext(TiContext tiContext) {
		TiContext oldContext = super.switchContext(tiContext);
		if (children != null) {
			for (TiViewProxy child : children) {
				child.switchContext(tiContext);
			}
		}
		return oldContext;
	}

	@Kroll.getProperty @Kroll.method
	public TiViewProxy[] getChildren() {
		if (children == null) return new TiViewProxy[0];
		return children.toArray(new TiViewProxy[children.size()]);
	}

	@Override
	public void eventListenerAdded(String eventName, int count, KrollProxy proxy) {
		super.eventListenerAdded(eventName, count, proxy);
		if (eventName.equals(TiC.EVENT_CLICK) && proxy.equals(this) && count == 1 && !(proxy instanceof TiWindowProxy)) {
			if (!proxy.hasProperty(TiC.PROPERTY_TOUCH_ENABLED)
				|| TiConvert.toBoolean(proxy.getProperty(TiC.PROPERTY_TOUCH_ENABLED))) {
				setClickable(true);
			}
		}
	}

	@Override
	public void eventListenerRemoved(String eventName, int count, KrollProxy proxy) {
		super.eventListenerRemoved(eventName, count, proxy);
		if (eventName.equals(TiC.EVENT_CLICK) && count == 0 && proxy.equals(this) && !(proxy instanceof TiWindowProxy)) {
			if (proxy.hasProperty(TiC.PROPERTY_TOUCH_ENABLED)
				&& !TiConvert.toBoolean(proxy.getProperty(TiC.PROPERTY_TOUCH_ENABLED))) {
				setClickable(false);
			}
		}
	}

	public void setClickable(boolean clickable) {
		if (peekView() != null) {
			TiUIView v = getView(getTiContext().getActivity());
			if (v != null) {
				View nv = v.getNativeView();
				if (nv != null) {
					nv.setClickable(clickable);
				}
			}
		}
	}

	@Kroll.method
	public void addClass(Object[] classNames) {
		// This is a pretty naive implementation right now,
		// but it will work for our current needs
		String baseUrl = getBaseUrlForStylesheet();
		ArrayList<String> classes = new ArrayList<String>();
		for (Object c : classNames) {
			classes.add(TiConvert.toString(c));
		}
		KrollDict options = getTiContext().getTiApp().getStylesheet(baseUrl, classes, null);
		extend(options);
	}
}
