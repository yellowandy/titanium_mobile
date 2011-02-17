/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2010 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package ti.modules.titanium.xml;

import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.titanium.TiContext;
import org.w3c.dom.CDATASection;
import org.w3c.dom.DOMException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;

@Kroll.proxy
public class ElementProxy extends NodeProxy {

	private Element element;
	public ElementProxy(TiContext context, Element element)
	{
		super(context, element);
		this.element = element;
	}

	@Kroll.getProperty @Kroll.method
	public String getText()
	{
		StringBuilder sb = new StringBuilder();
		getTextImpl(element, sb);
		return sb.toString();
	}

	private void getTextImpl(Node node, StringBuilder builder)
	{
		NodeList children = node.getChildNodes();
		for (int i = 0; i < children.getLength(); i++)
		{
			Node child = children.item(i);
			switch (child.getNodeType()) {
				case Node.TEXT_NODE:
					builder.append(((Text)child).getNodeValue()); break;
				case Node.CDATA_SECTION_NODE:
					builder.append(((CDATASection)child).getData()); break;
				case Node.ENTITY_NODE:
				case Node.ELEMENT_NODE:
					getTextImpl(child, builder); break;
				default: break;
			}
		}
	}

	@Kroll.method
	public String getAttribute(String name)
	{
		return element.getAttribute(name);
	}

	@Kroll.method
	public AttrProxy getAttributeNode(String name)
	{
		return getProxy(element.getAttributeNode(name));
	}

	@Kroll.method
	public AttrProxy getAttributeNodeNS(String namespaceURI, String localName)
		throws DOMException
	{
		return getProxy(element.getAttributeNodeNS(namespaceURI, localName));
	}

	@Kroll.method
	public String getAttributeNS(String namespaceURI, String localName)
		throws DOMException
	{
		return element.getAttributeNS(namespaceURI, localName);
	}

	protected NodeListProxy filterThisFromNodeList(NodeList list)
	{
		// Android's Harmony XML impl adds the "this" node
		// to the front if it matches the tag name,
		// but DOM 2 says only descendant nodes should return.
		int offset = 0;
		if (list.getLength() > 0 && list.item(0).equals(element)) {
			offset = 1;
		}
		return new NodeListProxy(getTiContext(), list, offset);
	}

	@Kroll.method
	public NodeListProxy getElementsByTagName(String name)
	{
		return filterThisFromNodeList(element.getElementsByTagName(name));
	}

	@Kroll.method
	public NodeListProxy getElementsByTagNameNS(String namespaceURI, String localName)
		throws DOMException
	{
		return filterThisFromNodeList(element.getElementsByTagNameNS(namespaceURI, localName));
	}

	@Kroll.getProperty @Kroll.method
	public String getTagName()
	{
		return element.getTagName();
	}

	@Kroll.method
	public boolean hasAttribute(String name)
	{
		return element.hasAttribute(name);
	}

	@Kroll.method
	public boolean hasAttributeNS(String namespaceURI, String localName)
		throws DOMException
	{
		return element.hasAttributeNS(namespaceURI, localName);
	}

	@Kroll.method
	public void removeAttribute(String name)
		throws DOMException
	{
		element.removeAttribute(name);
	}

	@Kroll.method
	public AttrProxy removeAttributeNode(AttrProxy oldAttr)
		throws DOMException
	{
		return getProxy(element.removeAttributeNode(oldAttr.getAttr()));
	}

	@Kroll.method
	public void removeAttributeNS(String namespaceURI, String localName)
		throws DOMException
	{
		element.removeAttributeNS(namespaceURI, localName);
	}

	@Kroll.method
	public void setAttribute(String name, String value)
		throws DOMException
	{
		element.setAttribute(name, value);
	}

	@Kroll.method
	public AttrProxy setAttributeNode(AttrProxy newAttr)
		throws DOMException
	{
		return getProxy(element.setAttributeNode(newAttr.getAttr()));
	}

	@Kroll.method
	public AttrProxy setAttributeNodeNS(AttrProxy newAttr)
		throws DOMException
	{
		return getProxy(element.setAttributeNodeNS(newAttr.getAttr()));
	}

	@Kroll.method
	public void setAttributeNS(String namespaceURI, String qualifiedName, String value)
		throws DOMException
	{
		element.setAttributeNS(namespaceURI, qualifiedName, value);
	}
}
