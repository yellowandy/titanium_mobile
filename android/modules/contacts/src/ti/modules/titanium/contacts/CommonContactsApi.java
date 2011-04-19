/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2010 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */

package ti.modules.titanium.contacts;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.titanium.TiContext;
import org.appcelerator.titanium.util.Log;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.Contacts;
import android.provider.ContactsContract;

public abstract class CommonContactsApi 
{
	private static final boolean TRY_NEWER_API = (android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.DONUT);
	private static final String LCAT = "TiCommonContactsApi";
	
	protected static CommonContactsApi getInstance(TiContext tiContext)
	{
		boolean useNew = false;
		if (TRY_NEWER_API) {
			try {
				Class.forName("android.provider.ContactsContract"); // just a test for success
				useNew = true;
			} catch (ClassNotFoundException e) {
				Log.d(LCAT, "Unable to load newer contacts api: " + e.getMessage(), e);
				useNew = false;
			}
		} 
		Log.d(LCAT, "Using " + (useNew ? "newer " : "older ") + "contacts api.  Android SDK level: " + android.os.Build.VERSION.SDK_INT);
		if (useNew) {
			ContactsApiLevel5 c = new ContactsApiLevel5(tiContext);
			if (!c.loadedOk) {
				Log.d(LCAT, "ContactsApiLevel5 did not load successfully.  Falling back to L4.");
				return new ContactsApiLevel4(tiContext);
			} else {
				return c;
			}
		} else {
			return new ContactsApiLevel4(tiContext);
		}
	}
	
	protected static Bitmap getContactImage(TiContext context, long contact_id)
	{
		CommonContactsApi api = getInstance(context);
		return api.getContactImage(contact_id);
	}
	
	protected abstract PersonProxy getPersonById(long id);
	protected abstract PersonProxy getPersonByUri(Uri uri);
	protected abstract PersonProxy[] getAllPeople(int limit);
	protected abstract PersonProxy[] getPeopleWithName(String name);
	protected abstract Intent getIntentForContactsPicker();
	protected abstract Bitmap getContactImage(long id);
	
	protected PersonProxy[] getAllPeople()
	{
		return getAllPeople(Integer.MAX_VALUE);
	}
	
	protected PersonProxy[] proxifyPeople(Map<Long, LightPerson> persons, TiContext tiContext)
	{
		PersonProxy[] proxies = new PersonProxy[persons.size()];
		int index = 0;
		for (LightPerson person: persons.values()) {
			proxies[index] = person.proxify(tiContext);
			index++;
		}
		return proxies;
	}
	
	// Happily, these codes are common across api level
	protected static String getEmailTextType(int type) 
	{
		String key = "other";
		if (type == Contacts.ContactMethods.TYPE_HOME) {
			key = "home";
		} else if (type == Contacts.ContactMethods.TYPE_WORK) {
			key = "work";
		}
		return key;
	}
	
	protected static String getPhoneTextType(int type)
	{
		String key = "other";
		if (type == Contacts.Phones.TYPE_FAX_HOME) {
			key = "homeFax";
		}
		if (type == Contacts.Phones.TYPE_FAX_WORK) {
			key = "workFax";
		}
		if (type == Contacts.Phones.TYPE_HOME) {
			key = "home";
		}
		if (type == Contacts.Phones.TYPE_MOBILE) {
			key = "mobile";
		}
		if (type == Contacts.Phones.TYPE_PAGER) {
			key = "pager";
		}
		if (type == Contacts.Phones.TYPE_WORK) {
			key = "work";
		}
		return key;
	}
	
	
	protected static String getPostalAddressTextType(int type)
	{
		String key = "other";
		if (type == Contacts.ContactMethods.TYPE_HOME) {
			key = "home";
		} else if (type == Contacts.ContactMethods.TYPE_WORK) {
			key = "work";
		}
		return key;
	}
	
	protected static class LightPerson
	{
		long id;
		String name;
		String firstName;
		String lastName;
		String notes;
		boolean hasImage = false;
		Map<String, ArrayList<String>> emails = new HashMap<String, ArrayList<String>>();
		Map<String, ArrayList<String>> phones = new HashMap<String, ArrayList<String>>();
		Map<String, ArrayList<KrollDict>> addresses = new HashMap<String, ArrayList<KrollDict>>();
		
		void addPersonInfoFromL4Cursor(Cursor cursor)
		{
			this.id = cursor.getLong(ContactsApiLevel4.PEOPLE_COL_ID);
			this.name = cursor.getString(ContactsApiLevel4.PEOPLE_COL_NAME);
			this.notes = cursor.getString(ContactsApiLevel4.PEOPLE_COL_NOTES);
		}
		
		void addPersonInfoFromL5DataRow(Cursor cursor)
		{
			this.id = cursor.getLong(ContactsApiLevel5.DATA_COLUMN_CONTACT_ID);
			this.name = cursor.getString(ContactsApiLevel5.DATA_COLUMN_DISPLAY_NAME);
			this.hasImage = (cursor.getInt(ContactsApiLevel5.DATA_COLUMN_PHOTO_ID) > 0);
		}
		
		void addPersonInfoFromL5PersonRow(Cursor cursor)
		{
			this.id = cursor.getLong(ContactsApiLevel5.PEOPLE_COL_ID);
			this.name = cursor.getString(ContactsApiLevel5.PEOPLE_COL_NAME);
			this.hasImage = (cursor.getInt(ContactsApiLevel5.PEOPLE_COL_PHOTO_ID) > 0);
		}
		
		void addDataFromL5Cursor(Cursor cursor) {
			String kind = cursor.getString(ContactsApiLevel5.DATA_COLUMN_MIMETYPE);
			if (kind.equals(ContactsApiLevel5.KIND_ADDRESS)) {
				loadAddressFromL5DataRow(cursor);
			} else if (kind.equals(ContactsApiLevel5.KIND_EMAIL)) {
				loadEmailFromL5DataRow(cursor);
			} else if (kind.equals(ContactsApiLevel5.KIND_NAME)) {
				loadNameFromL5DataRow(cursor);
			} else if (kind.equals(ContactsApiLevel5.KIND_NOTE)) {
				loadNoteFromL5DataRow(cursor);
			} else if (kind.equals(ContactsApiLevel5.KIND_PHONE)) {
				loadPhoneFromL5DataRow(cursor);
			}
		}
		
		void loadPhoneFromL5DataRow(Cursor phonesCursor)
		{
			String phoneNumber = phonesCursor.getString(ContactsApiLevel5.DATA_COLUMN_PHONE_NUMBER);
			int type = phonesCursor.getInt(ContactsApiLevel5.DATA_COLUMN_PHONE_TYPE);
			String key = getPhoneTextType(type);
			ArrayList<String> collection;
			if (phones.containsKey(key)) {
				collection = phones.get(key);
			} else {
				collection = new ArrayList<String>();
				phones.put(key, collection);
			}
			collection.add(phoneNumber);
		}
		
		void loadNoteFromL5DataRow(Cursor cursor)
		{
			this.notes = cursor.getString(ContactsApiLevel5.DATA_COLUMN_NOTE);
		}
		
		void loadNameFromL5DataRow(Cursor cursor)
		{
			Log.d(LCAT, "**********  Begining to Load Names From Contact API");
			this.firstName = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME));
			this.lastName  = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME));
			Log.d(LCAT, "**********  First NAME IS: " + this.firstName);
			Log.d(LCAT, "**********  Last NAME IS: " + this.lastName);
		}
		
		void loadEmailFromL5DataRow(Cursor emailsCursor)
		{
			String emailAddress = emailsCursor.getString(ContactsApiLevel5.DATA_COLUMN_EMAIL_ADDR);
			int type = emailsCursor.getInt(ContactsApiLevel5.DATA_COLUMN_EMAIL_TYPE);
			String key = getEmailTextType(type);
			
			ArrayList<String> collection;
			if (emails.containsKey(key)) {
				collection = emails.get(key);
			} else {
				collection = new ArrayList<String>();
				emails.put(key, collection);
			}
			collection.add(emailAddress);
		}
		
		void loadAddressFromL5DataRow(Cursor cursor)
		{
			// TODO add structured addresss
			String fullAddress = cursor.getString(ContactsApiLevel5.DATA_COLUMN_ADDRESS_FULL);
			
			//Start			
			String street = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredPostal.STREET));
			String city = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredPostal.CITY));
			String state  = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredPostal.REGION));
			String postalCode = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredPostal.POSTCODE));			
			String country = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredPostal.COUNTRY));
			
			KrollDict dictValues = new KrollDict();
			dictValues.put("Street", street);
			dictValues.put("City", city);
			dictValues.put("State", state);
			dictValues.put("ZIP", postalCode);
			dictValues.put("Country", country);
			//END
			
			int type = cursor.getInt(ContactsApiLevel5.DATA_COLUMN_ADDRESS_TYPE);
			String key = getPostalAddressTextType(type);
			
			ArrayList<KrollDict> collection;
			if ( addresses.containsKey(key) ) 
			{
				collection = addresses.get(key);
			} 
			else 
			{
				collection = new ArrayList<KrollDict>();
				addresses.put(key, collection);
			}
			collection.add(dictValues);
		}
		
		void addEmailFromL4Cursor(Cursor emailsCursor)
		{
			String emailAddress = emailsCursor.getString(ContactsApiLevel4.CONTACT_METHOD_COL_DATA);
			int type = emailsCursor.getInt(ContactsApiLevel4.CONTACT_METHOD_COL_TYPE);
			String key = ContactsApiLevel4.getEmailTextType(type);
			
			ArrayList<String> collection;
			if (emails.containsKey(key)) {
				collection = emails.get(key);
			} else {
				collection = new ArrayList<String>();
				emails.put(key, collection);
			}
			collection.add(emailAddress);
		}
		
		void addPhoneFromL4Cursor(Cursor phonesCursor)
		{
			String phoneNumber = phonesCursor.getString(ContactsApiLevel4.PHONE_COL_NUMBER);
			int type = phonesCursor.getInt(ContactsApiLevel4.PHONE_COL_TYPE);
			String key = getPhoneTextType(type);
			ArrayList<String> collection;
			if (phones.containsKey(key)) {
				collection = phones.get(key);
			} else {
				collection = new ArrayList<String>();
				phones.put(key, collection);
			}
			collection.add(phoneNumber);
		}
		
		
		void addAddressFromL4Cursor(Cursor addressesCursor)
		{
			
		}
		
		void addPhotoInfoFromL4Cursor(Cursor photosCursor)
		{
			hasImage = true;
		}
		
		PersonProxy proxify(TiContext tiContext)
		{
			PersonProxy proxy = new PersonProxy(tiContext);
			proxy.firstName = firstName;
			proxy.lastName = lastName;
			proxy.fullName = name;
			proxy.note = notes;
			proxy.setEmailFromMap(emails);
			proxy.setPhoneFromMap(phones);
			proxy.setAddressFromMap(addresses);
			proxy.kind = ContactsModule.CONTACTS_KIND_PERSON;
			proxy.id = id;
			proxy.hasImage = this.hasImage;
			return proxy;
			
		}
	}
	
	
}
