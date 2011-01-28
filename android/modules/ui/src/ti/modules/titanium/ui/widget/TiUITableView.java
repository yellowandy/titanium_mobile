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

import ti.modules.titanium.ui.TableViewProxy;
import ti.modules.titanium.ui.widget.searchbar.TiUISearchBar;
import ti.modules.titanium.ui.widget.tableview.TableViewModel;
import ti.modules.titanium.ui.widget.tableview.TiTableView;
import ti.modules.titanium.ui.widget.tableview.TiTableView.OnItemClickedListener;
import android.view.Gravity;
import android.widget.ListView;
import android.widget.RelativeLayout;

public class TiUITableView extends TiUIView
	implements OnItemClickedListener
{
	private static final String LCAT = "TitaniumTableView";	
	private static final boolean DBG = TiConfig.LOGD;
	
	protected TiTableView tableView;
	
	public TiUITableView(TiViewProxy proxy) {
		super(proxy);
		getLayoutParams().autoFillsHeight = true;
		getLayoutParams().autoFillsWidth = true;
	}

	@Override
	public void onClick(KrollDict data) {
		proxy.fireEvent(TiC.EVENT_CLICK, data);
	}
	
	public void setModelDirty() {
		tableView.getTableViewModel().setDirty();
	}
	
	public TableViewModel getModel() {
		return tableView.getTableViewModel();
	}
	
	public void updateView() {
		tableView.dataSetChanged();
	}	

	public void scrollToIndex(final int index) {
		tableView.getListView().setSelection(index);
	}

	public TiTableView getTableView() {
		return tableView;
	}
	
	public ListView getListView() {
		return tableView.getListView();
	}

	@Override
	public void processProperties(KrollDict d) {
		tableView = new TiTableView(proxy.getTiContext(), (TableViewProxy) proxy);
		tableView.setOnItemClickListener(this);
	
		if (d.containsKey(TiC.PROPERTY_SEARCH)) {
			RelativeLayout layout = new RelativeLayout(proxy.getTiContext().getActivity());
			layout.setGravity(Gravity.NO_GRAVITY);
			layout.setPadding(0, 0, 0, 0);
			
			TiViewProxy searchView = (TiViewProxy) d.get(TiC.PROPERTY_SEARCH);
			TiUISearchBar searchBar = (TiUISearchBar)searchView.getView(proxy.getTiContext().getActivity());
			searchBar.setOnSearchChangeListener(tableView);
			searchBar.getNativeView().setId(102);
			
			RelativeLayout.LayoutParams p = new RelativeLayout.LayoutParams(
					RelativeLayout.LayoutParams.FILL_PARENT,
					RelativeLayout.LayoutParams.FILL_PARENT);
			p.addRule(RelativeLayout.ALIGN_PARENT_TOP);
			p.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
			p.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
			p.height = 52;
			
			layout.addView(searchBar.getNativeView(), p);
			
			p = new RelativeLayout.LayoutParams(
				RelativeLayout.LayoutParams.FILL_PARENT,
				RelativeLayout.LayoutParams.FILL_PARENT);
			p.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
			p.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
			p.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
			p.addRule(RelativeLayout.BELOW, 102);
			layout.addView(tableView, p);
			setNativeView(layout);
		} else {
			setNativeView(tableView);
		}

		if (d.containsKey(TiC.PROPERTY_FILTER_ATTRIBUTE)) {
			tableView.setFilterAttribute(TiConvert.toString(d, TiC.PROPERTY_FILTER_ATTRIBUTE));
		} else {
			// Default to title to match iPhone default.
			proxy.setProperty(TiC.PROPERTY_FILTER_ATTRIBUTE, TiC.PROPERTY_TITLE, false);
			tableView.setFilterAttribute(TiC.PROPERTY_TITLE);
		}

		boolean filterCaseInsensitive = true;
		if (d.containsKey(TiC.PROPERTY_FILTER_CASE_INSENSITIVE)) {
			filterCaseInsensitive = TiConvert.toBoolean(d, TiC.PROPERTY_FILTER_CASE_INSENSITIVE);
		}
		tableView.setFilterCaseInsensitive(filterCaseInsensitive);
		super.processProperties(d);
	}

	@Override
	public void release() {
		if (tableView != null) {
			tableView.release();
			tableView  = null;
		}
		nativeView  = null;
		super.release();
	}

	@Override
	public void propertyChanged(String key, Object oldValue, Object newValue,
			KrollProxy proxy) {
		if (DBG) {
			Log.d(LCAT, "Property: " + key + " old: " + oldValue + " new: " + newValue);
		}
		if (key.equals(TiC.PROPERTY_SEPARATOR_COLOR)) {
			tableView.setSeparatorColor(TiConvert.toString(newValue));
		} else {
			super.propertyChanged(key, oldValue, newValue, proxy);
		}
	}
}
