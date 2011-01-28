/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2010 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package ti.modules.titanium.filesystem;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.titanium.TiBlob;
import org.appcelerator.titanium.TiContext;
import org.appcelerator.titanium.TiFile;
import org.appcelerator.titanium.io.TiBaseFile;
import org.appcelerator.titanium.io.TiFileFactory;
import org.appcelerator.titanium.util.TiConvert;
import org.appcelerator.titanium.util.TiFileHelper2;

import android.net.Uri;

@Kroll.proxy
public class FileProxy extends TiFile
{

	String path;
	TiBaseFile tbf; // The base file object.

	public static <T>
	String join(final Collection<T> objs, final String delimiter) {
	    if (objs == null || objs.isEmpty())
	        return "";
	    Iterator<T> iter = objs.iterator();
	    // remove the following two lines, if you expect the Collection will behave well
	    if (!iter.hasNext())
	        return "";
	    StringBuffer buffer = new StringBuffer(String.valueOf(iter.next()));
	    while (iter.hasNext())
	        buffer.append(delimiter).append(String.valueOf(iter.next()));
	    return buffer.toString();
	}

	public FileProxy(TiContext tiContext, String[] parts) {
		this(tiContext, parts, true);
	}
	
	public FileProxy(TiContext tiContext, String[] parts, boolean resolve) {
		super(tiContext);

		//String path = getTiContext().resolveUrl(join(Arrays.asList(parts), "/"));
		String scheme = "appdata-private://";
		String path = null;
		Uri uri = Uri.parse(parts[0]);
		if (uri.getScheme() != null) {
			scheme = uri.getScheme() + ":";
			ArrayList<String> pb = new ArrayList<String>();
			String s = parts[0].substring(scheme.length() + 2);
			if (s != null && s.length() > 0) {
				pb.add(s);
			}
			for (int i = 1; i < parts.length; i++) {
				pb.add(parts[i]);
			}
			String[] newParts = pb.toArray(new String[pb.size()]);
			path = TiFileHelper2.joinSegments(newParts);
			if (!path.startsWith("..") || !path.startsWith("/")) {
				path = "/" + path;
			}
			pb.clear();
		} else {
			path = TiFileHelper2.joinSegments(parts);
		}
		
		if (resolve) {
			path = getTiContext().resolveUrl(scheme, path);
		}
		tbf = TiFileFactory.createTitaniumFile(tiContext, new String[] { path }, false);
	}

	private FileProxy(TiContext tiContext, TiBaseFile tbf) {
		super(tiContext);
		this.tbf = tbf;
	}

	public TiBaseFile getBaseFile() {
		return tbf;
	}

	@Kroll.method
	public boolean isFile() {
		return tbf.isFile();
	}

	@Kroll.method
	public boolean isDirectory() {
		return tbf.isDirectory();
	}

	@Kroll.getProperty @Kroll.method
	public boolean getReadonly() {
		return tbf.isReadonly();
	}

	@Kroll.getProperty @Kroll.method
	public boolean getWritable() {
		return tbf.isWriteable();
	}

	@Kroll.method
	public boolean copy (String destination) throws IOException {
		return tbf.copy(destination);
	}

	@Kroll.method
	public boolean createDirectory(@Kroll.argument(optional=true) Object arg) {
		boolean recursive = true;

		if (arg != null) {
			recursive = TiConvert.toBoolean(arg);
		}
		return tbf.createDirectory(recursive);
	}

	@Kroll.method
	public boolean deleteDirectory(@Kroll.argument(optional=true) Object arg) {
		boolean recursive = false;

		if (arg != null) {
			recursive = TiConvert.toBoolean(arg);
		}
		return tbf.deleteDirectory(recursive);
	}

	@Kroll.method
	public boolean deleteFile() {
		return tbf.deleteFile();
	}

	@Kroll.method
	public boolean exists() {
		return tbf.exists();
	}

	@Kroll.method
	public String extension() {
		return tbf.extension();
	}

	@Kroll.getProperty @Kroll.method
	public boolean getSymbolicLink() {
		return tbf.isSymbolicLink();
	}

	@Kroll.getProperty @Kroll.method
	public boolean getExecutable() {
		return tbf.isExecutable();
	}

	@Kroll.getProperty @Kroll.method
	public boolean getHidden() {
		return tbf.isHidden();
	}

	@Kroll.getProperty @Kroll.method
	public String[] getDirectoryListing()
	{
		List<String> dl = tbf.getDirectoryListing();
		return dl != null ? dl.toArray(new String[0]) : null;
	}

	@Kroll.getProperty @Kroll.method
	public FileProxy getParent()
	{
		TiBaseFile bf = tbf.getParent();
		return bf != null ? new FileProxy(getTiContext(), bf) : null;
	}

	@Kroll.method
	public boolean move(String destination)
		throws IOException
	{
		return tbf.move(destination);
	}

	@Kroll.getProperty @Kroll.method
	public String getName() {
		return tbf.name();
	}

	@Kroll.getProperty @Kroll.method
	public String getNativePath() {
		return tbf.nativePath();
	}

	@Kroll.method
	public TiBlob read()
		throws IOException
	{
		return tbf.read();
	}

	@Kroll.method
	public String readLine()
		throws IOException
	{
		return tbf.readLine();
	}

	@Kroll.method
	public boolean rename(String destination)
	{
		return tbf.rename(destination);
	}

	@Kroll.method
	public TiBaseFile resolve() {
		return tbf.resolve();
	}

	@Kroll.getProperty @Kroll.method
	public double getSize() {
		return tbf.size();
	}

	@Kroll.method
	public double spaceAvailable() {
		return tbf.spaceAvailable();
	}

	@Kroll.method
	public void write(Object[] args)
		throws IOException
	{
		if (args != null && args.length > 0) {
			boolean append = false;
			if (args.length > 1 && args[1] instanceof Boolean) {
				append = ((Boolean)args[1]).booleanValue();
			}
			if (args[0] instanceof TiBlob) {
				tbf.write((TiBlob)args[0], append);
			} else if (args[0] instanceof String) {
				tbf.write((String)args[0], append);
			} else if (args[0] instanceof FileProxy) {
				tbf.write(((FileProxy)args[0]).read(), append);
			} else {
				throw new IOException("unable to write, unrecognized type");
			}
		}
	}

	@Kroll.method
	public void writeLine(String data)
		throws IOException
	{
		tbf.writeLine(data);
	}
	
	@Kroll.method
	public double createTimestamp() 
	{
		return tbf.createTimestamp();
	}
	
	@Kroll.method
	public double modificationTimestamp() 
	{
		return tbf.modificationTimestamp();
	}
	
}
