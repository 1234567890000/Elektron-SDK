///*|-----------------------------------------------------------------------------
// *|            This source code is provided under the Apache 2.0 license      --
// *|  and is provided AS IS with no warranty or guarantee of fit for purpose.  --
// *|                See the project's LICENSE.md for details.                  --
// *|           Copyright Thomson Reuters 2015. All rights reserved.            --
///*|-----------------------------------------------------------------------------

package com.thomsonreuters.ema.access.impl;

import java.nio.ByteBuffer;

import com.thomsonreuters.ema.access.DataType;
import com.thomsonreuters.ema.access.FilterEntry;

public class FilterEntryImpl extends EntryImpl implements FilterEntry
{
	private final static String SET_STRING 				= "Set";
	private final static String UPDATE_STRING 			= "Update";
	private final static String CLEAR_STRING 			= "Clear";
	private final static String DEFAULTACTION_STRING 	= "Unknown FilterAction value ";
	
	private ByteBuffer _permData;
	private com.thomsonreuters.upa.codec.FilterEntry _rsslFilterEntry;
	
	FilterEntryImpl(FilterListImpl filterList, com.thomsonreuters.upa.codec.FilterEntry rsslFilterEntry, DataImpl load)
	{
		super(filterList, load);
		_rsslFilterEntry = rsslFilterEntry;
	}
	
	@Override
	public String filterActionAsString()
	{
		switch (action())
		{
			case FilterAction.SET:
				return SET_STRING;
			case FilterAction.UPDATE:
				return UPDATE_STRING;
			case FilterAction.CLEAR:
				return CLEAR_STRING;
			default:
				return DEFAULTACTION_STRING + action();
		}
	}

	@Override
	public boolean hasPermissionData()
	{
		return _rsslFilterEntry.checkHasPermData();
	}

	@Override
	public int action()
	{
		return _rsslFilterEntry.action();
	}

	@Override
	public ByteBuffer permissionData()
	{
		if (!hasPermissionData())
			throw oommIUExcept().message("Attempt to permissionData() while it is NOT set.");
		
		GlobalPool.releaseByteBuffer(_permData);
		_permData = DataImpl.asByteBuffer(_rsslFilterEntry.permData());
		
		return _permData;
	}

	@Override
	public int filterId()
	{
		return _rsslFilterEntry.id();
	}
	
	@Override
	public String toString()
	{
		_toString.setLength(0);
		_toString.append("FilterEntry ")
				.append(" action=\"").append(filterActionAsString()).append("\"")
				.append(" filterId=\"").append(filterId());

		if (hasPermissionData())
		{
			_toString.append("\" permissionData=\"").append(permissionData()).append("\"");
			Utilities.asHexString(_toString, permissionData()).append("\"");
		}
		
		_toString.append("\" dataType=\"").append(DataType.asString(_load.dataType())).append("\"\n");
		_toString.append(_load.toString(1));
		Utilities.addIndent(_toString, 0).append("FilterEntryEnd\n");

		return _toString.toString();
	}
}