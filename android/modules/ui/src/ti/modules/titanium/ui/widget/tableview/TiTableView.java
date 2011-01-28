/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2010 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package ti.modules.titanium.ui.widget.tableview;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollProxy;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.TiContext;
import org.appcelerator.titanium.proxy.TiViewProxy;
import org.appcelerator.titanium.util.Log;
import org.appcelerator.titanium.util.TiColorHelper;
import org.appcelerator.titanium.util.TiConfig;
import org.appcelerator.titanium.util.TiConvert;
import org.appcelerator.titanium.view.TiCompositeLayout;
import org.appcelerator.titanium.view.TiUIView;

import ti.modules.titanium.ui.LabelProxy;
import ti.modules.titanium.ui.TableViewProxy;
import ti.modules.titanium.ui.TableViewRowProxy;
import ti.modules.titanium.ui.widget.searchbar.TiUISearchBar.OnSearchChangeListener;
import ti.modules.titanium.ui.widget.tableview.TableViewModel.Item;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ListView;

public class TiTableView extends FrameLayout
	implements OnSearchChangeListener
{
	public static final int TI_TABLE_VIEW_ID = 101;
	private static final String LCAT = "TiTableView";
	private static final boolean DBG = TiConfig.LOGD;

	//TODO make this configurable
	protected static final int MAX_CLASS_NAMES = 32;

	private TableViewModel viewModel;
	private ListView listView;
	private TTVListAdapter adapter;
	private OnItemClickedListener itemClickListener;

	private HashMap<String, Integer> rowTypes;
	private AtomicInteger rowTypeCounter;

	private String filterAttribute;
	private String filterText;

	private TiContext tiContext;
	private TableViewProxy proxy;
	private boolean filterCaseInsensitive = true;
	private TiTableViewSelector selector;

	public interface OnItemClickedListener {
		public void onClick(KrollDict item);
	}

	class TTVListAdapter extends BaseAdapter {
		TableViewModel viewModel;
		ArrayList<Integer> index;
		private boolean filtered;

		TTVListAdapter(TableViewModel viewModel) {
			this.viewModel = viewModel;
			this.index = new ArrayList<Integer>(viewModel.getRowCount());
			reIndexItems();
		}

		protected void registerClassName(String className) {
			if (!rowTypes.containsKey(className)) {
				if (DBG) {
					Log.d(LCAT, "registering new className " + className);
				}
				rowTypes.put(className, rowTypeCounter.incrementAndGet());
			}
		}

		public void reIndexItems() {
			ArrayList<Item> items = viewModel.getViewModel();
			int count = items.size();
			index.clear();

			filtered = false;
			if (filterAttribute != null && filterText != null && filterAttribute.length() > 0 && filterText.length() > 0) {
				filtered = true;
				String filter = filterText;
				if (filterCaseInsensitive) {
					filter = filterText.toLowerCase();
				}
				for(int i = 0; i < count; i++) {
					boolean keep = true;
					Item item = items.get(i);
					registerClassName(item.className);
					if (item.proxy.hasProperty(filterAttribute)) {
						String t = TiConvert.toString(item.proxy.getProperty(filterAttribute));
						if (filterCaseInsensitive) {
							t = t.toLowerCase();
						}
						if(t.indexOf(filter) < 0) {
							keep = false;
						}
					}
					if (keep) {
						index.add(i);
					}
				}
			} else {
				for(int i = 0; i < count; i++) {
					Item item = items.get(i);
					registerClassName(item.className);
					index.add(i);
				}
			}
		}

		public int getCount() {
			//return viewModel.getViewModel().length();
			return index.size();
		}

		public Object getItem(int position) {
			if (position >= index.size()) {
				return null;
			}

			return viewModel.getViewModel().get(index.get(position));
		}

		public long getItemId(int position) {
			return position;
		}

		@Override
		public int getViewTypeCount() {
			return MAX_CLASS_NAMES;
		}

		@Override
		public int getItemViewType(int position) {
			Item item = (Item) getItem(position);
			registerClassName(item.className);
			return rowTypes.get(item.className);
		}

		public View getView(int position, View convertView, ViewGroup parent) {
			Item item = (Item) getItem(position);
			TiBaseTableViewItem v = null;
			
			if (convertView != null) {
				v = (TiBaseTableViewItem) convertView;
				// Default creates view for each Item
				boolean sameView = false;
				if (item.proxy instanceof TableViewRowProxy) {
					TableViewRowProxy row = (TableViewRowProxy)item.proxy;
					if (row.getTableViewRowProxyItem() != null) {
						sameView = row.getTableViewRowProxyItem().equals(convertView);
					}
				}
				if (!sameView) {
					if (v.getClassName().equals(TableViewProxy.CLASSNAME_DEFAULT)) {
						if (v.getRowData() != item) {
							v = null;
						}
					} else {
						// otherwise compare class names
						if (!v.getClassName().equals(item.className)) {
							Log.w(LCAT, "Handed a view to convert with className " + v.getClassName() + " expected " + item.className);
							v = null;
						}
					}
				}
			}
			if (v == null) {
				if (item.className.equals(TableViewProxy.CLASSNAME_HEADER)) {
					v = new TiTableViewHeaderItem(tiContext);
					v.setClassName(TableViewProxy.CLASSNAME_HEADER);
				} else if (item.className.equals(TableViewProxy.CLASSNAME_NORMAL)) {
					v = new TiTableViewRowProxyItem(tiContext);
					v.setClassName(TableViewProxy.CLASSNAME_NORMAL);
				} else if (item.className.equals(TableViewProxy.CLASSNAME_DEFAULT)) {
					v = new TiTableViewRowProxyItem(tiContext);
					v.setClassName(TableViewProxy.CLASSNAME_DEFAULT);
				} else {
					v = new TiTableViewRowProxyItem(tiContext);
					v.setClassName(item.className);
				}
				v.setLayoutParams(new AbsListView.LayoutParams(
					AbsListView.LayoutParams.FILL_PARENT, AbsListView.LayoutParams.FILL_PARENT));
			}
			v.setRowData(item);
			return v;
		}

		@Override
		public boolean areAllItemsEnabled() {
			return false;
		}

		@Override
		public boolean isEnabled(int position) {
			Item item = (Item) getItem(position);
			boolean enabled = true;
			if (item != null && item.className.equals(TableViewProxy.CLASSNAME_HEADER)) {
				enabled = false;
			}
			return enabled;
		}

		@Override
		public boolean hasStableIds() {
			return true;
		}

		@Override
		public void notifyDataSetChanged() {
			reIndexItems();
			super.notifyDataSetChanged();
		}

		public boolean isFiltered() {
			return filtered;
		}
	}

	public TiTableView(TiContext tiContext, TableViewProxy proxy)
	{
		super(tiContext.getActivity());
		this.tiContext = tiContext;
		this.proxy = proxy;

		rowTypes = new HashMap<String, Integer>();
		rowTypeCounter = new AtomicInteger(-1);
		rowTypes.put(TableViewProxy.CLASSNAME_HEADER, rowTypeCounter.incrementAndGet());
		rowTypes.put(TableViewProxy.CLASSNAME_NORMAL, rowTypeCounter.incrementAndGet());
		rowTypes.put(TableViewProxy.CLASSNAME_DEFAULT, rowTypeCounter.incrementAndGet());

		this.viewModel = new TableViewModel(tiContext, proxy);
		this.listView = new ListView(getContext()) {
			public boolean onKeyDown(int keyCode, android.view.KeyEvent event) {
				if (selector != null) {
					selector.keyEvent(keyCode, 1, event);
				}
				return super.onKeyDown(keyCode, event);
			}
			public boolean onKeyMultiple(int keyCode, int repeatCount, android.view.KeyEvent event) {
				if (selector != null) {
					selector.keyEvent(keyCode, repeatCount, event);
				}
				return super.onKeyMultiple(keyCode, repeatCount, event);
			}
			public boolean onKeyUp(int keyCode, android.view.KeyEvent event) {
				if (selector != null) {
					selector.keyEvent(keyCode, 1, event);
				}
				return super.onKeyUp(keyCode, event);
			}
		};
		listView.setId(TI_TABLE_VIEW_ID);

		listView.setFocusable(true);
		listView.setFocusableInTouchMode(true);
		listView.setBackgroundColor(Color.TRANSPARENT);
		listView.setCacheColorHint(Color.TRANSPARENT);
		final KrollProxy fProxy = proxy;
		listView.setOnScrollListener(new OnScrollListener()
		{
			@Override
			public void onScrollStateChanged(AbsListView view, int scrollState)
			{
				if (scrollState == OnScrollListener.SCROLL_STATE_IDLE){
					KrollDict eventArgs = new KrollDict();
					KrollDict size = new KrollDict();
					size.put("width", TiTableView.this.getWidth());
					size.put("height", TiTableView.this.getHeight());
					eventArgs.put("size", size);
					fProxy.fireEvent("scrollEnd", eventArgs);
				}
			}

			@Override
			public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount)
			{
				KrollDict eventArgs = new KrollDict();
				eventArgs.put("firstVisibleItem", firstVisibleItem);
				eventArgs.put("visibleItemCount", visibleItemCount);
				eventArgs.put("totalItemCount", totalItemCount);
				KrollDict size = new KrollDict();
				size.put("width", TiTableView.this.getWidth());
				size.put("height", TiTableView.this.getHeight());
				eventArgs.put("size", size);
				fProxy.fireEvent("scroll", eventArgs);
			}
		});

		if (proxy.getProperties().containsKey(TiC.PROPERTY_SEPARATOR_COLOR)) {
			setSeparatorColor(TiConvert.toString(proxy.getProperty(TiC.PROPERTY_SEPARATOR_COLOR)));
		}
		adapter = new TTVListAdapter(viewModel);
		if (proxy.hasProperty(TiC.PROPERTY_HEADER_VIEW)) {
			TiViewProxy view = (TiViewProxy) proxy.getProperty(TiC.PROPERTY_HEADER_VIEW);
			listView.addHeaderView(layoutHeaderOrFooter(view), null, false);
		}
		if (proxy.hasProperty(TiC.PROPERTY_FOOTER_VIEW)) {
			TiViewProxy view = (TiViewProxy) proxy.getProperty(TiC.PROPERTY_FOOTER_VIEW);
			listView.addFooterView(layoutHeaderOrFooter(view), null, false);
		}

		listView.setAdapter(adapter);
		listView.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				if (itemClickListener != null) {
					if (!(view instanceof TiBaseTableViewItem)) {
						return;
					}
					if (TiTableView.this.proxy.hasProperty(TiC.PROPERTY_HEADER_VIEW)) {
						position -= 1;
					}
					rowClicked((TiBaseTableViewItem)view, position);
				}
			}
		});
		addView(listView);
	}

	public void enableCustomSelector() {
		Drawable currentSelector = listView.getSelector();
		if (currentSelector != selector) {
			selector = new TiTableViewSelector(this);
			listView.setSelector(selector);
		}
	}
	
	protected Item getItemAtPosition(int position) {
		return viewModel.getViewModel().get(adapter.index.get(position));
	}

	protected void rowClicked(TiBaseTableViewItem rowView, int position) {
		String viewClicked = rowView.getLastClickedViewName();
		Item item = getItemAtPosition(position);
		KrollDict event = new KrollDict();
		TableViewRowProxy.fillClickEvent(event, viewModel, item);
		if (viewClicked != null) {
			event.put(TiC.EVENT_PROPERTY_LAYOUT_NAME, viewClicked);
		}
		event.put(TiC.EVENT_PROPERTY_SEARCH_MODE, adapter.isFiltered());

		if(item.proxy != null && item.proxy instanceof TableViewRowProxy) {
			TableViewRowProxy rp = (TableViewRowProxy) item.proxy;
			if (rp.hasListeners(TiC.EVENT_CLICK)) {
				rp.fireEvent(TiC.EVENT_CLICK, event);
			}
		}
		itemClickListener.onClick(event);
	}

	private View layoutHeaderOrFooter(TiViewProxy viewProxy)
	{
		TiUIView tiView = viewProxy.getView(tiContext.getActivity());
		View nativeView = tiView.getNativeView();
		TiCompositeLayout.LayoutParams params = tiView.getLayoutParams();

		int width = AbsListView.LayoutParams.WRAP_CONTENT;
		int height = AbsListView.LayoutParams.WRAP_CONTENT;
		if (params.autoHeight) {
			if (params.autoFillsHeight) {
				height = AbsListView.LayoutParams.FILL_PARENT;
			}
		} else {
			height = params.optionHeight.getAsPixels(listView);
		}
		if (params.autoWidth) {
			if (params.autoFillsWidth) {
				width = AbsListView.LayoutParams.FILL_PARENT;
			}
		} else {
			width = params.optionWidth.getAsPixels(listView);
		}
		AbsListView.LayoutParams p = new AbsListView.LayoutParams(width, height);
		nativeView.setLayoutParams(p);
		return nativeView;
	}

	public void dataSetChanged() {
		if (adapter != null) {
			adapter.notifyDataSetChanged();
		}
	}

	public void setOnItemClickListener(OnItemClickedListener listener) {
		this.itemClickListener = listener;
	}

	public void setSeparatorColor(String colorstring) {
		int sepColor = TiColorHelper.parseColor(colorstring);
		int dividerHeight = listView.getDividerHeight();
		listView.setDivider(new ColorDrawable(sepColor));
		listView.setDividerHeight(dividerHeight);
	}

	public TableViewModel getTableViewModel() {
		return this.viewModel;
	}

	public ListView getListView() {
		return listView;
	}

	@Override
	public void filterBy(String text) {
		filterText = text;
		if (adapter != null) {
			tiContext.getActivity().runOnUiThread(new Runnable() {
				public void run() {
					dataSetChanged();
				}
			});
		}
	}

	public void setFilterAttribute(String filterAttribute) {
		this.filterAttribute = filterAttribute;
	}

	public void setFilterCaseInsensitive(boolean filterCaseInsensitive) {
		this.filterCaseInsensitive  = filterCaseInsensitive;
	}

	public void release() {
		adapter = null;
		if (listView != null) {
			listView.setAdapter(null);
		}
		listView = null;
		if (viewModel != null) {
			viewModel.release();
		}
		viewModel = null;
		itemClickListener = null;
		tiContext = null;
	}
}
