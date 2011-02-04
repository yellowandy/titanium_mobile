/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2010 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package org.appcelerator.kroll;

import org.appcelerator.titanium.TiContext;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

@SuppressWarnings("serial")
public class KrollObject extends ScriptableObject implements Function
{
	protected KrollProxy proxy;

	public KrollObject(KrollProxy proxy)
	{
		this.proxy = proxy;
	}

	@Override
	public String getClassName()
	{
		return "Ti."+proxy.getAPIName() + (proxy instanceof KrollModule ? "Module":"");
	}

	@Override
	public Object get(String name, Scriptable start)
	{
		Object value = null;
		try {
			value = proxy.get(start, name);
			if (value != null && value.equals(KrollProxy.UNDEFINED)) {
				return Scriptable.NOT_FOUND;
			}
		} catch (NoSuchFieldException e) {
			return Scriptable.NOT_FOUND;
		}
		TiContext context = TiContext.getCurrentTiContext();
		Scriptable scope = start;
		if (context != null) {
			scope = context.getScope();
		}
		KrollInvocation invocation = KrollInvocation.createPropertyGetInvocation(context, scope, start, name, null, proxy);
		Object result = KrollConverter.getInstance().convertNative(invocation, value);
		invocation.recycle();
		return result;
	}

	@Override
	public Object get(int index, Scriptable start)
	{
		// TODO: implement special array index getters in binding
		return super.get(index, start);
	}

	@Override
	public void put(String name, Scriptable start, Object value)
	{
		TiContext context = TiContext.getCurrentTiContext();
		Scriptable scope = start;
		if (context != null) {
			scope = context.getScope();
		}
		KrollInvocation invocation = KrollInvocation.createPropertyGetInvocation(context, scope, start, name, null, proxy);
		try {
			value = KrollConverter.getInstance().convertJavascript(invocation, value, Object.class);
			proxy.set(start, name, value);
		} catch (NoSuchFieldException e) {
			Context.throwAsScriptRuntimeEx(e);
		}
		invocation.recycle();
	}

	public void superPut(String name, Scriptable start, Object value)
	{
		super.put(name, start, value);
	}

	@Override
	public void put(int index, Scriptable start, Object value)
	{
		// TODO: implement special array index setters in binding
		super.put(index, start, value);
	}

	@Override
	public boolean has(int index, Scriptable start)
	{
		// TODO: implement special array "has" in binding
		return super.has(index, start);
	}

	@Override
	public boolean has(String name, Scriptable start)
	{
		return proxy.has(start, name);
	}

	@Override
	public Object call(Context cx, Scriptable scope, Scriptable thisObj,
		Object[] args)
	{
		// TODO: implement proxy-as-function binding
		return null;
	}

	@Override
	public Scriptable construct(Context cx, Scriptable scope, Object[] args)
	{
		// TODO: implement proxy-as-constructor binding
		return null;
	}

	public KrollProxy getProxy()
	{
		return proxy;
	}

	@Override
	public Object getDefaultValue(Class<?> typeHint)
	{
		return proxy.getDefaultValue(typeHint);
	}

	@Override
	protected Object equivalentValues(Object value)
	{
		if (value instanceof KrollObject) {
			KrollObject other = (KrollObject)value;
			return proxy.equals(other.getProxy()) ? Boolean.TRUE : Boolean.FALSE;
		}
		return super.equivalentValues(value);
	}
}
