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
import org.appcelerator.titanium.view.TiCompositeLayout;
import org.appcelerator.titanium.view.TiUIView;

import ti.modules.titanium.ui.TabGroupProxy;
import ti.modules.titanium.ui.TabProxy;
import ti.modules.titanium.ui.TiTabActivity;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TabHost;
import android.widget.TabWidget;
import android.widget.TabHost.OnTabChangeListener;
import android.widget.TabHost.TabSpec;

public class TiUITabGroup extends TiUIView
	implements OnTabChangeListener
{
	private static final String LCAT = "TiUITabGroup";
	private static final boolean DBG = TiConfig.LOGD;

	private TabHost tabHost;
	private TabWidget tabWidget;
	private FrameLayout tabContent;
	private boolean addingTab;

	private String lastTabId;
	private KrollDict tabChangeEventData;

	public TiUITabGroup(TiViewProxy proxy, TiTabActivity activity)
	{
		super(proxy);
		tabHost = new TabHost(activity);
		tabHost.setOnTabChangedListener(this);

		tabWidget = new TabWidget(proxy.getContext());
		tabWidget.setId(android.R.id.tabs); // Required by contract w/ host

		tabContent = new FrameLayout(proxy.getContext()) {
			@Override
			protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
			{
				tabContent.setPadding(0, tabWidget.getMeasuredHeight(), 0, 0);
				super.onMeasure(widthMeasureSpec, heightMeasureSpec);
			}
		};
		tabContent.setId(android.R.id.tabcontent);

		tabHost.addView(tabWidget, new LinearLayout.LayoutParams(
			LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT));
		tabHost.addView(tabContent, new LinearLayout.LayoutParams(
			LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT));
		//tabHost.setup(proxy.getTiContext().getRootActivity().getLocalActivityManager());
		tabHost.setup(activity.getLocalActivityManager());

		tabHost.setBackgroundDrawable(new ColorDrawable(TiConvert.toColor("#ff1a1a1a")));

		setNativeView(tabHost);
		TiCompositeLayout.LayoutParams params = new TiCompositeLayout.LayoutParams();
		params.autoFillsHeight = true;
		params.autoFillsWidth = true;
		activity.getLayout().addView(tabHost, params);

		lastTabId = null;
	}

	public TabSpec newTab(String id)
	{
		return tabHost.newTabSpec(id);
	}

	public void addTab(TabSpec tab)
	{
		addingTab = true;
		tabHost.addTab(tab);
		addingTab = false;
	}

	public void setActiveTab(int index)
	{
		if (tabHost != null) {
			tabHost.setCurrentTab(index);
		}
	}

	@Override
	protected KrollDict getFocusEventObject(boolean hasFocus)
	{
		if (tabChangeEventData == null) {
			TabHost th = (TabHost) getNativeView();
			return ((TabGroupProxy) proxy).buildFocusEvent(th.getCurrentTabTag(), lastTabId);
		} else {
			return tabChangeEventData;
		}
	}

	@Override
	public void onFocusChange(View v, boolean hasFocus)
	{
		// ignore focus change for tab group.
		// we can simply fire focus/blur from onTabChanged (to avoid chicken/egg event problems)
	}
	
	@Override
	public void onTabChanged(String id)
	{
		TabGroupProxy tabGroupProxy = ((TabGroupProxy) proxy);
		if (DBG) {
			Log.d(LCAT,"Tab change from " + lastTabId + " to " + id);
		}

		proxy.setProperty(TiC.PROPERTY_ACTIVE_TAB, tabGroupProxy.getTabList().get (tabHost.getCurrentTab()));

		if (!addingTab) {
			if (tabChangeEventData != null) {
				proxy.fireEvent(TiC.EVENT_BLUR, tabChangeEventData);
			}
			
			tabChangeEventData = tabGroupProxy.buildFocusEvent(id, lastTabId);
			proxy.fireEvent(TiC.EVENT_FOCUS, tabChangeEventData);
		}
		
		lastTabId = id;
	}

	public void changeActiveTab(Object t)
	{
		if (t != null) {
			Integer index = null;
			if (t instanceof Number) {
				index = TiConvert.toInt(t);

				int len = tabHost.getTabWidget().getTabCount();
				if (index >= len) {
					// TODO consider throwing an exception to JS.
					Log.w(LCAT, "Index out of bounds. Attempt to set active tab to " + index + ". There are " + len + " tabs.");
					index = null;
				} else {
					tabHost.setCurrentTab(index);
				}
			} else if (t instanceof TabProxy) {
				TabProxy tab = (TabProxy) t;
				String tag = TiConvert.toString(tab.getProperty("tag"));
				if (tag != null) {
					tabHost.setCurrentTabByTag(tag);
				}
			} else {
				Log.w(LCAT, "Attempt to set active tab using a non-supported argument. Ignoring");
			}
		}
	}

	public int getActiveTab()
	{
		if(tabHost != null) {
			return tabHost.getCurrentTab();
		} else {
			return -1;
		}
	}

	@Override
	public void propertyChanged(String key, Object oldValue, Object newValue, KrollProxy proxy)
	{
		if ("activeTab".equals(key)) {
			changeActiveTab(newValue);
		} else {
			super.propertyChanged(key, oldValue, newValue, proxy);
		}
	}
}
