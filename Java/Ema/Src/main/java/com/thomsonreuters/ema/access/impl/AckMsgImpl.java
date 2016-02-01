///*|-----------------------------------------------------------------------------
// *|            This source code is provided under the Apache 2.0 license      --
// *|  and is provided AS IS with no warranty or guarantee of fit for purpose.  --
// *|                See the project's LICENSE.md for details.                  --
// *|           Copyright Thomson Reuters 2015. All rights reserved.            --
///*|-----------------------------------------------------------------------------

package com.thomsonreuters.ema.access.impl;

import java.nio.ByteBuffer;

import com.thomsonreuters.ema.access.AckMsg;
import com.thomsonreuters.ema.access.ComplexType;
import com.thomsonreuters.ema.access.DataType;
import com.thomsonreuters.ema.access.DataType.DataTypes;
import com.thomsonreuters.ema.access.OmmError.ErrorCode;
import com.thomsonreuters.upa.codec.CodecReturnCodes;

public class AckMsgImpl extends MsgImpl implements AckMsg
{
	private final static String ACCESSDENIED_STRING = "AccessDenied";
	private final static String DENIEDBYSOURCE_STRING = "DeniedBySource";
	private final static String SOURCEDOWN_STRING = "SourceDown";
	private final static String SOURCEUNKNOWN_STRING = "SourceUnknown";
	private final static String NORESOURCES_STRING = "NoResources";
	private final static String NORESPONSE_STRING = "NoResponse";
	private final static String SYMBOLUNKNOWN_STRING = "SymbolUnknown";
	private final static String NOTOPEN_STRING = "NotOpen";
	private final static String GATEWAYDOWN_STRING = "GatewayDown";
	private final static String NONE_STRING = "None";
	private final static String INVALIDCONTENT_STRING = "InvalidContent";
	private final static String UNKNOWNNACKCODE_STRING = "Unknown NackCode value ";

	public AckMsgImpl()
	{
		super(DataTypes.ACK_MSG);
	}

	@Override
	public String nackCodeAsString()
	{
		switch (nackCode())
		{
		case NackCode.NONE:
			return NONE_STRING;
		case NackCode.ACCESS_DENIED:
			return ACCESSDENIED_STRING;
		case NackCode.DENIED_BY_SOURCE:
			return DENIEDBYSOURCE_STRING;
		case NackCode.SOURCE_DOWN:
			return SOURCEDOWN_STRING;
		case NackCode.SOURCE_UNKNOWN:
			return SOURCEUNKNOWN_STRING;
		case NackCode.NO_RESOURCES:
			return NORESOURCES_STRING;
		case NackCode.NO_RESPONSE:
			return NORESPONSE_STRING;
		case NackCode.GATEWAY_DOWN:
			return GATEWAYDOWN_STRING;
		case NackCode.SYMBOL_UNKNOWN:
			return SYMBOLUNKNOWN_STRING;
		case NackCode.NOT_OPEN:
			return NOTOPEN_STRING;
		case NackCode.INVALID_CONTENT:
			return INVALIDCONTENT_STRING;
		default:
			return (UNKNOWNNACKCODE_STRING + nackCode());
		}
	}

	@Override
	public boolean hasSeqNum()
	{
		return ((com.thomsonreuters.upa.codec.AckMsg) _rsslMsg).checkHasSeqNum();
	}

	@Override
	public boolean hasNackCode()
	{
		return ((com.thomsonreuters.upa.codec.AckMsg) _rsslMsg).checkHasNakCode();
	}

	@Override
	public boolean hasText()
	{
		return ((com.thomsonreuters.upa.codec.AckMsg) _rsslMsg).checkHasText();
	}

	@Override
	public long seqNum()
	{
		if (!hasSeqNum())
			throw oommIUExcept().message("Attempt to seqNum() while it is NOT set.");

		return ((com.thomsonreuters.upa.codec.AckMsg) _rsslMsg).seqNum();
	}

	@Override
	public long ackId()
	{
		return ((com.thomsonreuters.upa.codec.AckMsg) _rsslMsg).ackId();
	}

	@Override
	public int nackCode()
	{
		if (!hasNackCode())
			throw oommIUExcept().message("Attempt to nackCode() while it is NOT set.");

		return ((com.thomsonreuters.upa.codec.AckMsg) _rsslMsg).nakCode();
	}

	@Override
	public String text()
	{
		if (!hasText())
			throw oommIUExcept().message("Attempt to text() while it is NOT set.");

		return ((com.thomsonreuters.upa.codec.AckMsg) _rsslMsg).text().toString();
	}

	@Override
	public boolean privateStream()
	{
		return ((com.thomsonreuters.upa.codec.AckMsg) _rsslMsg).checkPrivateStream();
	}

	@Override
	public AckMsg clear()
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public AckMsg streamId(int streamId)
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public AckMsg domainType(int domainType)
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public AckMsg name(String name)
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public AckMsg nameType(int nameType)
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public AckMsg serviceName(String serviceName)
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public AckMsg serviceId(int serviceId)
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public AckMsg id(int id)
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public AckMsg filter(long filter)
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public AckMsg seqNum(long seqNum)
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public AckMsg ackId(long actId)
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public AckMsg nackCode(int nackCode)
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public AckMsg text(String text)
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public AckMsg attrib(ComplexType attrib)
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public AckMsg payload(ComplexType payload)
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public AckMsg extendedHeader(ByteBuffer buffer)
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public AckMsg privateStream(boolean privateStream)
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String toString()
	{
		return toString(0);
	}

	String toString(int indent)
	{
		_toString.setLength(0);
		Utilities.addIndent(_toString, indent++).append("AckMsg");
		Utilities.addIndent(_toString, indent, true).append("streamId=\"").append(streamId()).append("\"");
		Utilities.addIndent(_toString, indent, true).append("domain=\"")
				.append(Utilities.rdmDomainAsString(domainType())).append("\"");
		Utilities.addIndent(_toString, indent, true).append("ackId=\"")
				.append(Utilities.rdmDomainAsString(domainType())).append("\"");

		if (hasSeqNum())
			Utilities.addIndent(_toString, indent, true).append("seqNum=\"").append(seqNum()).append("\"");

		if (hasNackCode())
			Utilities.addIndent(_toString, indent, true).append("nackCode=\"").append(nackCodeAsString()).append("\"");

		if (hasText())
			Utilities.addIndent(_toString, indent, true).append("text=\"").append(text()).append("\"");

		indent--;
		if (hasMsgKey())
		{
			indent++;
			if (hasName())
				Utilities.addIndent(_toString, indent, true).append("name=\"").append(name()).append("\"");

			if (hasNameType())
				Utilities.addIndent(_toString, indent, true).append("nameType=\"").append(nameType()).append("\"");

			if (hasServiceId())
				Utilities.addIndent(_toString, indent, true).append("serviceId=\"").append(serviceId()).append("\"");

			if (hasServiceName())
				Utilities.addIndent(_toString, indent, true).append("serviceName=\"").append(serviceName())
						.append("\"");

			if (hasFilter())
				Utilities.addIndent(_toString, indent, true).append("filter=\"").append(filter()).append("\"");

			if (hasId())
				Utilities.addIndent(_toString, indent, true).append("id=\"").append(id()).append("\"");

			indent--;

			if (hasAttrib())
			{
				indent++;
				Utilities.addIndent(_toString, indent, true).append("Attrib dataType=\"")
						.append(DataType.asString(attribData().dataType())).append("\"\n");

				indent++;
				_toString.append(attribData().toString(indent));
				indent--;

				Utilities.addIndent(_toString, indent, true).append("AttribEnd");
				indent--;
			}
		}

		if (hasExtendedHeader())
		{
			indent++;
			Utilities.addIndent(_toString, indent, true).append("ExtendedHeader\n");

			indent++;
			Utilities.addIndent(_toString, indent);
			Utilities.asHexString(_toString, extendedHeader()).append("\"");
			indent--;

			Utilities.addIndent(_toString, indent, true).append("ExtendedHeaderEnd");
			indent--;
		}

		if (hasPayload())
		{
			indent++;
			Utilities.addIndent(_toString, indent, true).append("Payload dataType=\"")
					.append(DataType.asString(payloadData().dataType())).append("\"\n");

			indent++;
			_toString.append(payloadData().toString(indent));
			indent--;

			Utilities.addIndent(_toString, indent).append("PayloadEnd");
			indent--;
		}

		Utilities.addIndent(_toString, indent, true).append("AckMsgEnd\n");

		return _toString.toString();
	}

	@Override
	void decode(com.thomsonreuters.upa.codec.Msg rsslMsg, int majVer, int minVer,
			com.thomsonreuters.upa.codec.DataDictionary rsslDictionary)
	{
		_rsslMsg = rsslMsg;

		_rsslBuffer = _rsslMsg.encodedMsgBuffer();

		_rsslDictionary = rsslDictionary;

		_rsslMajVer = majVer;

		_rsslMinVer = minVer;

		_serviceNameSet = false;

		decodeAttribPayload();
	}

	@Override
	void decode(com.thomsonreuters.upa.codec.Buffer rsslBuffer, int majVer, int minVer,
			com.thomsonreuters.upa.codec.DataDictionary rsslDictionary, Object obj)
	{
		_rsslNestedMsg.clear();

		_rsslMsg = _rsslNestedMsg;

		_rsslDictionary = rsslDictionary;

		_rsslMajVer = majVer;

		_rsslMinVer = minVer;

		_serviceNameSet = false;

		_rsslDecodeIter.clear();

		int retCode = _rsslDecodeIter.setBufferAndRWFVersion(rsslBuffer, _errorCode, _rsslMinVer);
		if (CodecReturnCodes.SUCCESS != retCode)
		{
			_errorCode = ErrorCode.ITERATOR_SET_FAILURE;
			return;
		}

		retCode = _rsslMsg.decode(_rsslDecodeIter);
		switch (retCode)
		{
		case CodecReturnCodes.SUCCESS:
			_errorCode = ErrorCode.NO_ERROR;
			decodeAttribPayload();
			return;
		case CodecReturnCodes.ITERATOR_OVERRUN:
			_errorCode = ErrorCode.ITERATOR_OVERRUN;
			dataInstance(_attribDecoded, DataTypes.ERROR).decode(rsslBuffer, _errorCode);
			dataInstance(_payloadDecoded, DataTypes.ERROR).decode(rsslBuffer, _errorCode);
			return;
		case CodecReturnCodes.INCOMPLETE_DATA:
			_errorCode = ErrorCode.INCOMPLETE_DATA;
			dataInstance(_attribDecoded, DataTypes.ERROR).decode(rsslBuffer, _errorCode);
			dataInstance(_payloadDecoded, DataTypes.ERROR).decode(rsslBuffer, _errorCode);
			return;
		default:
			_errorCode = ErrorCode.UNKNOWN_ERROR;
			dataInstance(_attribDecoded, DataTypes.ERROR).decode(rsslBuffer, _errorCode);
			dataInstance(_payloadDecoded, DataTypes.ERROR).decode(rsslBuffer, _errorCode);
			return;
		}
	}
}