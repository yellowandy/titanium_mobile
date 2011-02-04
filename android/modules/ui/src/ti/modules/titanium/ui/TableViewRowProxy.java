/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2010 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package ti.modules.titanium.ui;

import java.util.ArrayList;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.TiContext;
import org.appcelerator.titanium.proxy.TiViewProxy;
import org.appcelerator.titanium.util.Log;
import org.appcelerator.titanium.view.TiUIView;
import org.mozilla.javascript.Scriptable;

import ti.modules.titanium.ui.widget.tableview.TableViewModel;
import ti.modules.titanium.ui.widget.tableview.TableViewModel.Item;
import ti.modules.titanium.ui.widget.tableview.TiTableViewRowProxyItem;
import android.app.Activity;
import android.os.Message;
import android.test.IsolatedContext;

@Kroll.proxy(creatableInModule=UIModule.class)
public class TableViewRowProxy extends TiViewProxy
{
	private static final String LCAT = "TableViewRowProxy";
	
	protected ArrayList<TiViewProxy> controls;
	protected TiTableViewRowProxyItem tableViewItem;

	private static final int MSG_SET_DATA = TiViewProxy.MSG_LAST_ID + 5001;

	public TableViewRowProxy(TiContext tiContext) {
		super(tiContext);
	}
	
	@Override
	public void handleCreationDict(KrollDict options) {
		super.handleCreationDict(options);
		if (options.containsKey(TiC.PROPERTY_SELECTED_BACKGROUND_COLOR)) {
			Log.w(LCAT, "selectedBackgroundColor is deprecated, use backgroundSelectedColor instead");
			setProperty(TiC.PROPERTY_BACKGROUND_SELECTED_COLOR, options.get(TiC.PROPERTY_SELECTED_BACKGROUND_COLOR));
		}
		if (options.containsKey(TiC.PROPERTY_SELECTED_BACKGROUND_IMAGE)) {
			Log.w(LCAT, "selectedBackgroundImage is deprecated, use backgroundSelectedImage instead");
			setProperty(TiC.PROPERTY_BACKGROUND_SELECTED_IMAGE, options.get(TiC.PROPERTY_SELECTED_BACKGROUND_IMAGE));
		}
	}

	@Override
	public TiUIView createView(Activity activity) {
		return null;
	}

	public ArrayList<TiViewProxy> getControls() {
		return controls;
	}

	public boolean hasControls() {
		return (controls != null && controls.size() > 0);
	}
	
	@Override
	public TiViewProxy[] getChildren() {
		return controls.toArray(new TiViewProxy[controls.size()]);
	}

	public void add(TiViewProxy control) {
		if (controls == null) {
			controls = new ArrayList<TiViewProxy>();
		}
		controls.add(control);
		control.setParent(this);
		if (tableViewItem != null) {
			Message msg = getUIHandler().obtainMessage(MSG_SET_DATA);
			msg.sendToTarget();
		}
	}

	@Override
	public void remove(TiViewProxy control) {
		if (controls == null) {
			return;
		}
		controls.remove(control);
		if (tableViewItem != null) {
			Message msg = getUIHandler().obtainMessage(MSG_SET_DATA);
			msg.sendToTarget();
		}
	}

	public void setTableViewItem(TiTableViewRowProxyItem item) {
		this.tableViewItem = item;
	}

	public TableViewProxy getTable() {
		TiViewProxy parent = getParent();
		while (!(parent instanceof TableViewProxy) && parent != null) {
			parent = parent.getParent();
		}
		return (TableViewProxy)parent;
	}

  @Kroll.setProperty @Kroll.method
  public void setHasCheck(boolean check) { 
     Log.w(LCAT, "Trigger hasCheck set");
  }

	@Override
	public void set(Scriptable scope, String name, Object value)
			throws NoSuchFieldException {
		if (name.equals(TiC.PROPERTY_SELECTED_BACKGROUND_COLOR)) {
			Log.w(LCAT, "selectedBackgroundColor is deprecated, use backgroundSelectedColor instead");
			super.set(scope, TiC.PROPERTY_BACKGROUND_SELECTED_COLOR, value);
		} else if (name.equals(TiC.PROPERTY_SELECTED_BACKGROUND_IMAGE)) {
			Log.w(LCAT, "selectedBackgroundImage is deprecated, use backgroundSelectedImage instead");
			super.set(scope, TiC.PROPERTY_BACKGROUND_SELECTED_IMAGE, value);
		} else {
			super.set(scope, name, value);
		}
	}

	@Override
	public void setProperty(String name, Object value, boolean fireChange) {
		super.setProperty(name, value, fireChange);
		if (tableViewItem != null) {
			if (context.isUIThread()) {
				tableViewItem.setRowData(this);
			} else {
				Message msg = getUIHandler().obtainMessage(MSG_SET_DATA);
				msg.sendToTarget();
			}
		}
	}

	@Override
	public boolean handleMessage(Message msg) {
		if (msg.what == MSG_SET_DATA) {
			if (tableViewItem != null) {
				tableViewItem.setRowData(this);
			}
			return true;
		}
		return super.handleMessage(msg);
	}

	public static void fillClickEvent(KrollDict data, TableViewModel model, Item item) {
		data.put(TiC.PROPERTY_ROW_DATA, item.rowData);
		data.put(TiC.PROPERTY_SECTION, model.getSection(item.sectionIndex));
		data.put(TiC.EVENT_PROPERTY_ROW, item.proxy);
		data.put(TiC.EVENT_PROPERTY_INDEX, item.index);
		data.put(TiC.EVENT_PROPERTY_DETAIL, false);
	}

	@Override
	public boolean fireEvent(String eventName, KrollDict data) {
		if (eventName.equals(TiC.EVENT_CLICK)) {
			// inject row click data for events coming from row children
			TableViewProxy table = getTable();
			Item item = tableViewItem.getRowData();
			if (table != null && item != null) {
				fillClickEvent(data, table.getTableView().getModel(), item);
			}
		}
		return super.fireEvent(eventName, data);
	}

	public void setLabelsClickable(boolean clickable) {
		if (controls != null) {
			for (TiViewProxy control : controls) {
				if (control instanceof LabelProxy) {
					((LabelProxy)control).setClickable(clickable);
				}
			}
		}
	}

	@Override
	public void releaseViews() {
		super.releaseViews();
		if (tableViewItem != null) {
			tableViewItem.release();
			tableViewItem = null;
		}
		if (controls != null) {
			for (TiViewProxy control : controls) {
				control.releaseViews();
			}
		}
	}

	public TiTableViewRowProxyItem getTableViewRowProxyItem() {
		return tableViewItem;
	}
}
