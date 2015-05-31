/*|-----------------------------------------------------------------------------
 *|            This source code is provided under the Apache 2.0 license      --
 *|  and is provided AS IS with no warranty or guarantee of fit for purpose.  --
 *|                See the project's LICENSE.md for details.                  --
 *|           Copyright Thomson Reuters 2015. All rights reserved.            --
 *|-----------------------------------------------------------------------------
 */

#include <stdio.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <ctype.h>
#include <stdlib.h>
#ifndef WIN32
#include <unistd.h>
#endif

#include "OmmConsumerActiveConfig.h"
#include "Map.h"
#include "ElementList.h"
#include "FieldList.h"
#include "ReqMsg.h"
#include "ReqMsgEncoder.h"
#include "ExceptionTranslator.h"
#include "OmmConsumerConfigImpl.h"
#include "DefaultXML.h"
#include "StaticDecoder.h"

using namespace thomsonreuters::ema::access;

extern const EmaString& getDTypeAsString( DataType::DataTypeEnum dType );

OmmConsumerConfigImpl::OmmConsumerConfigImpl() :
	_operationModel( OmmConsumerConfig::ApiDispatchEnum ),
	_pEmaConfig( new XMLnode("EmaConfig", 0, 0) ),
	_loginRdmReqMsg( *this ),
	_pDirectoryReqRsslMsg( 0 ),
	_pRdmFldReqRsslMsg( 0 ),
	_pEnumDefReqRsslMsg( 0 ),
	_pProgrammaticConfigure(0)
{
	createNameToValueHashTable();

	EmaString tmp("EmaConfig.xml");
	OmmLoggerClient::Severity result( readXMLconfiguration( tmp ) );
	if ( result == OmmLoggerClient::ErrorEnum || result == OmmLoggerClient::VerboseEnum )
	{
		EmaString errorMsg( "failed to extract configuration from [" );
		errorMsg.append( tmp ).append( "]" );
		_pEmaConfig->appendErrorMessage( errorMsg, result );
	}
}

OmmConsumerConfigImpl::~OmmConsumerConfigImpl()
{
	if ( _pDirectoryReqRsslMsg )
		delete _pDirectoryReqRsslMsg;
	
	if ( _pRdmFldReqRsslMsg )
		delete _pRdmFldReqRsslMsg;
	
	if ( _pEnumDefReqRsslMsg )
		delete _pEnumDefReqRsslMsg;
	
     if (_pProgrammaticConfigure)
	{
		delete _pProgrammaticConfigure;
		_pProgrammaticConfigure = 0;
	}
	delete _pEmaConfig;
}

void OmmConsumerConfigImpl::clear()
{
	_operationModel = OmmConsumerConfig::ApiDispatchEnum;
	_loginRdmReqMsg.clear();
	if ( _pDirectoryReqRsslMsg )
		_pDirectoryReqRsslMsg->clear();
	
	if ( _pRdmFldReqRsslMsg )
		_pRdmFldReqRsslMsg->clear();
	
	if ( _pEnumDefReqRsslMsg )
		_pEnumDefReqRsslMsg->clear();
}

void OmmConsumerConfigImpl::username( const EmaString& username )
{
	_loginRdmReqMsg.username( username );
}

void OmmConsumerConfigImpl::password( const EmaString& password )
{
	_loginRdmReqMsg.password( password );
}

void OmmConsumerConfigImpl::position( const EmaString& position )
{
	_loginRdmReqMsg.position( position );
}

void OmmConsumerConfigImpl::applicationId( const EmaString& applicationId )
{
	_loginRdmReqMsg.applicationId( applicationId );
}

void OmmConsumerConfigImpl::applicationName( const EmaString& applicationName )
{
	_loginRdmReqMsg.applicationName( applicationName );
}

void OmmConsumerConfigImpl::consumerName(const EmaString& consumerName)
{
	EmaString item( "ConsumerGroup|ConsumerList|Consumer." );
	item.append( consumerName ).append( "|Name" );
	EmaString name;
	if ( get( item, name ) )
		set( "ConsumerGroup|DefaultConsumer", consumerName);
	else
	{
		EmaString errorMsg( "OmmConsumerConfigImpl::consumerName parameter [" );
		errorMsg.append( consumerName ).append( "] is an non-existent consumer name" );
		throwIceException( errorMsg );
	}
}

void OmmConsumerConfigImpl::host( const EmaString& host )
{
	Int32 index = host.find( ":", 0 );
    if ( index == -1 ) 
	{
		_portSetViaFunctionCall = DEFAULT_SERVICE_NAME;

		if ( host.length() )
			_hostnameSetViaFunctionCall = host;
		else
			_hostnameSetViaFunctionCall = DEFAULT_HOST_NAME;
	}

	else if ( index == 0 )
	{ 
		_hostnameSetViaFunctionCall = DEFAULT_HOST_NAME;

		if ( host.length() > 1 )
			_portSetViaFunctionCall = host.substr( 1, host.length() - 1 );
		else
			_portSetViaFunctionCall = DEFAULT_SERVICE_NAME;
	}

	else
	{
		_hostnameSetViaFunctionCall = host.substr( 0, index );
		if ( host.length() > static_cast< UInt32 >( index + 1 ) )
			_portSetViaFunctionCall = host.substr( index + 1, host.length() - index - 1 );
		else
			_portSetViaFunctionCall = DEFAULT_SERVICE_NAME;
	}
} 

void OmmConsumerConfigImpl::operationModel( OmmConsumerConfig::OperationModel operationModel )
{
	_operationModel = operationModel;
}

const EmaString& OmmConsumerConfigImpl::getHostname() const
{
	static EmaString hostname;
	get( "Hostname", hostname );
	return hostname;
}

const EmaString& OmmConsumerConfigImpl::getPort() const
{
	static EmaString port;
	get( "Port", port );
	return port;
}

OmmConsumerConfig::OperationModel OmmConsumerConfigImpl::getOperationModel() const
{
	return _operationModel;
}

void OmmConsumerConfigImpl::config( const Data& config )
{
	if ( config.getDataType() == DataType::MapEnum )
	{
		if ( !_pProgrammaticConfigure )
		{
			_pProgrammaticConfigure = new ProgrammaticConfigure( static_cast<const Map&>( config ), _pEmaConfig->errors() );
		}
		else
		{
			_pProgrammaticConfigure->addConfigure( static_cast<const Map&>( config ) );
		}
	}
	else
	{
		EmaString temp( "Invalid Data type='" );
		temp.append( getDTypeAsString( config.getDataType() ) ).append( "' for Programmatic Configure." );
		EmaConfigError* mce( new EmaConfigError( temp, OmmLoggerClient::ErrorEnum ) );
		_pEmaConfig->errors().add( mce );
	}
}

void OmmConsumerConfigImpl::addAdminMsg( const ReqMsg& reqMsg )
{
	RsslRequestMsg* pRsslRequestMsg = static_cast<const ReqMsgEncoder&>( reqMsg.getEncoder() ).getRsslRequestMsg();

	switch ( pRsslRequestMsg->msgBase.domainType )
	{
	case RSSL_DMT_LOGIN :
		addLoginReqMsg( pRsslRequestMsg );
		break;
	case RSSL_DMT_DICTIONARY :
		addDictionaryReqMsg( pRsslRequestMsg );
		break;
	case RSSL_DMT_SOURCE :
		addDirectoryReqMsg( pRsslRequestMsg );
		break;
	default :
		{
			EmaString temp( "Request message with unhandled domain passed into addAdminMsg( const ReqMsg& ). Domain type='" );
			temp.append( pRsslRequestMsg->msgBase.domainType ).append( "'. " );
			EmaConfigError * mce( new EmaConfigError( temp, OmmLoggerClient::ErrorEnum ) );
			_pEmaConfig->errors().add ( mce );
		}
		break;
	}
}

EmaString OmmConsumerConfigImpl::getConsumerName() const
{
	EmaString retVal;

	if ( _pProgrammaticConfigure && _pProgrammaticConfigure->getDefaultConsumer( retVal ) )
		return retVal;

	if ( _pEmaConfig->get< EmaString >( "ConsumerGroup|DefaultConsumer", retVal ) )
	{
		EmaString expectedName( "ConsumerGroup|ConsumerList|Consumer." );
		expectedName.append( retVal ).append( "|Name" );
		EmaString checkValue;
		if ( _pEmaConfig->get( expectedName, checkValue ) )
            return retVal;
		else
		{		
			EmaString errorMsg( "default consumer name [" );
			errorMsg.append( retVal ).append( "] is an non-existent consumer name; DefaultConsumer specification ignored" );
			_pEmaConfig->appendErrorMessage( errorMsg, OmmLoggerClient::ErrorEnum );
		}
	}
	
    if ( _pEmaConfig->get< EmaString >( "ConsumerGroup|ConsumerList|Consumer|Name", retVal ) )
        return retVal;

    return "EmaConsumer";
}

EmaString OmmConsumerConfigImpl::getChannelName( const EmaString& consumerName ) const
{
	EmaString retVal;

	if ( _pProgrammaticConfigure && _pProgrammaticConfigure->getActiveChannelName(consumerName, retVal ) )
	{
		return retVal;
	}

	EmaString nodeName("ConsumerGroup|ConsumerList|Consumer.");
	nodeName.append( consumerName );
	nodeName.append("|Channel");

	if ( get<EmaString>( nodeName, retVal ) )
	{
		return retVal;
	}

	return retVal;
}

EmaString  OmmConsumerConfigImpl::getLoggerName( const EmaString& consumerName ) const
{
	EmaString retVal;

	if ( _pProgrammaticConfigure && _pProgrammaticConfigure->getActiveLoggerName(consumerName, retVal ) )
	{
		return retVal;
	}

	EmaString nodeName("ConsumerGroup|ConsumerList|Consumer.");
	nodeName.append( consumerName );
	nodeName.append("|Logger");

	if ( get<EmaString>( nodeName, retVal ) )
	{
		return retVal;
	}

	return retVal;
}

EmaString OmmConsumerConfigImpl::getDictionaryName( const EmaString& consumerName ) const
{
	EmaString retVal;

	if ( _pProgrammaticConfigure && _pProgrammaticConfigure->getActiveDictionaryName(consumerName, retVal ) )
	{
		return retVal;
	}

	EmaString nodeName("ConsumerGroup|ConsumerList|Consumer.");
	nodeName.append( consumerName );
	nodeName.append("|Dictionary");

	if ( get<EmaString>( nodeName, retVal ) )
	{
		return retVal;
	}

	return retVal;
}

void OmmConsumerConfigImpl::addLoginReqMsg( RsslRequestMsg* pRsslRequestMsg )
{
	_loginRdmReqMsg.set( pRsslRequestMsg );
}

void OmmConsumerConfigImpl::addDictionaryReqMsg( RsslRequestMsg* pRsslRequestMsg )
{
	if ( !( pRsslRequestMsg->msgBase.msgKey.flags & RSSL_MKF_HAS_NAME ) )
	{
		EmaString temp( "Received dicionary request message contains no dictionary name. Message ignored." );
		_pEmaConfig->appendErrorMessage( temp, OmmLoggerClient::ErrorEnum );
		return;
	}
	else if ( !( pRsslRequestMsg->msgBase.msgKey.flags & RSSL_MKF_HAS_SERVICE_ID ) )
	{
		EmaString temp( "Received dicionary request message contains no serviceId. Message ignored." );
		_pEmaConfig->appendErrorMessage( temp, OmmLoggerClient::ErrorEnum );
		return;
	}
	else if ( !( pRsslRequestMsg->msgBase.msgKey.flags & RSSL_MKF_HAS_FILTER ) )
	{
		EmaString temp( "Received dicionary request message contains no filter. Message ignored." );
		_pEmaConfig->appendErrorMessage( temp, OmmLoggerClient::ErrorEnum );
		return;
	}
	else if ( pRsslRequestMsg->flags & RSSL_RQMF_NO_REFRESH )
	{
		EmaString temp( "Received dicionary request message contains no_refresh flag. Message ignored." );
		_pEmaConfig->appendErrorMessage( temp, OmmLoggerClient::ErrorEnum );
		return;
	}

	RsslBuffer rdmFldDictionaryName;
	rdmFldDictionaryName.data = "RWFFld";
	rdmFldDictionaryName.length = 6;

	RsslBuffer enumtypedefName;
	enumtypedefName.data = "RWFEnum";
	enumtypedefName.length = 7;

	if ( rsslBufferIsEqual( &pRsslRequestMsg->msgBase.msgKey.name , &rdmFldDictionaryName ) )
	{
		if ( !_pRdmFldReqRsslMsg )
			_pRdmFldReqRsslMsg = new AdminReqMsg( *this );

		_pRdmFldReqRsslMsg->set( pRsslRequestMsg );
	}
	else if ( rsslBufferIsEqual( &pRsslRequestMsg->msgBase.msgKey.name , &enumtypedefName ) )
	{
		if ( !_pEnumDefReqRsslMsg )
			_pEnumDefReqRsslMsg = new AdminReqMsg( *this );

		_pEnumDefReqRsslMsg->set( pRsslRequestMsg );
	}
	else
	{
		EmaString temp( "Received dicionary request message contains unrecognized dictionary name. Message ignored." );
		_pEmaConfig->appendErrorMessage( temp, OmmLoggerClient::ErrorEnum );
	}
}

void OmmConsumerConfigImpl::addDirectoryReqMsg( RsslRequestMsg* pRsslRequestMsg )
{
	if ( !_pDirectoryReqRsslMsg )
		_pDirectoryReqRsslMsg = new AdminReqMsg( *this );

	_pDirectoryReqRsslMsg->set( pRsslRequestMsg );
}

RsslRDMLoginRequest* OmmConsumerConfigImpl::getLoginReq()
{
	return _loginRdmReqMsg.get();
}

RsslRequestMsg* OmmConsumerConfigImpl::getDirectoryReq()
{
	return _pDirectoryReqRsslMsg ? _pDirectoryReqRsslMsg->get() : 0;
}

RsslRequestMsg* OmmConsumerConfigImpl::getRdmFldDictionaryReq()
{
	return _pRdmFldReqRsslMsg ? _pRdmFldReqRsslMsg->get() : 0;
}

RsslRequestMsg* OmmConsumerConfigImpl::getEnumDefDictionaryReq()
{
	return _pEnumDefReqRsslMsg ? _pEnumDefReqRsslMsg->get() : 0;
}

OmmLoggerClient::Severity OmmConsumerConfigImpl::readXMLconfiguration( const EmaString & fileName )
{
#ifdef WIN32
	char * fileLocation = _getcwd(0, 0);
#else
	char * fileLocation = getcwd(0, 0);
#endif
	EmaString message( "reading configuration file [" );
	message.append( fileName ).append( "] from [" ).append( fileLocation ).append( "]" );
	_pEmaConfig->appendErrorMessage( message, OmmLoggerClient::VerboseEnum );
	free(fileLocation);

#ifdef WIN32
	struct _stat statBuffer;
	int statResult( _stat( fileName, &statBuffer ) );
#else
	struct stat statBuffer;
	int statResult( stat( fileName.c_str(), &statBuffer ) );
#endif
	if ( statResult )
	{
		EmaString errorMsg( "error reading configuration file [" );
		errorMsg.append( fileName ).append( "]; system error message [" ).append( strerror( errno ) ).append( "]" );
		_pEmaConfig->appendErrorMessage( errorMsg, OmmLoggerClient::VerboseEnum );
		return OmmLoggerClient::VerboseEnum;
	}
	if ( ! statBuffer.st_size)
	{
		EmaString errorMsg( "error reading configuration file [" );
		errorMsg.append( fileName ).append( "]; file is empty" );
		_pEmaConfig->appendErrorMessage( errorMsg, OmmLoggerClient::ErrorEnum );
		return OmmLoggerClient::ErrorEnum;
	}
	FILE *fp;
	fp = fopen( fileName.c_str(), "r" );
	if ( ! fp ) {
		EmaString errorMsg( "error reading configuration file [" );
		errorMsg.append( fileName ).append( "]; could not open file; system error message [" ).append( strerror( errno ) ).append( "]" );
		_pEmaConfig->appendErrorMessage( errorMsg, OmmLoggerClient::ErrorEnum );
		return OmmLoggerClient::ErrorEnum;
	}

	char * xmlData = reinterpret_cast<char *>( malloc(statBuffer.st_size + 1) );
	size_t bytesRead( fread( reinterpret_cast<void *>(xmlData), sizeof(char), statBuffer.st_size, fp ) );
	if ( ! bytesRead )
	{
		EmaString errorMsg( "error reading configuration file [" );
		errorMsg.append( fileName ).append( "]; fread failed; system error message [" ).append( strerror( errno ) ).append( "]" );
		_pEmaConfig->appendErrorMessage( errorMsg, OmmLoggerClient::ErrorEnum );
		free(xmlData);
		return OmmLoggerClient::ErrorEnum; 
	}
	fclose(fp);
	xmlData[bytesRead] = 0;
	bool retVal(extractXMLdataFromCharBuffer( fileName, xmlData, static_cast<int>( bytesRead ) ) );
	free(xmlData);
	return ( retVal == true ? OmmLoggerClient::SuccessEnum : OmmLoggerClient::ErrorEnum );
}

bool OmmConsumerConfigImpl::extractXMLdataFromCharBuffer( const EmaString & what, const char * xmlData, int length )
{
	LIBXML_TEST_VERSION

	EmaString note( "extracting XML data from " );
	note.append( what );
	_pEmaConfig->appendErrorMessage( note, OmmLoggerClient::VerboseEnum );

	xmlDocPtr xmlDoc = xmlReadMemory( xmlData, length, NULL, "notnamed.xml", XML_PARSE_HUGE );
	if ( xmlDoc == NULL ) {
		EmaString errorMsg( "extractXMLdataFromCharBuffer: xmlReadMemory failed while processing " );
		errorMsg.append(what);
		_pEmaConfig->appendErrorMessage(errorMsg, OmmLoggerClient::ErrorEnum);
		xmlFreeDoc( xmlDoc );
		return false;
	}

	xmlNodePtr _xmlNodePtr = xmlDocGetRootElement( xmlDoc );
	if ( _xmlNodePtr == NULL ) {
		EmaString errorMsg( "extractXMLdataFromCharBuffer: xmlDocGetRootElement failed while processing " );
		errorMsg.append(what);
		_pEmaConfig->appendErrorMessage(errorMsg, OmmLoggerClient::ErrorEnum);
		xmlFreeDoc( xmlDoc );
		return false;
	}

	if ( xmlStrcmp(_xmlNodePtr->name, (const xmlChar *) "EmaConfig" ) ) {
		EmaString errorMsg( "extractXMLdataFromCharBuffer: document has wrong type; expected [EmaConfig] while processing " );
		errorMsg.append(what);
		_pEmaConfig->appendErrorMessage(errorMsg, OmmLoggerClient::ErrorEnum);
		xmlFreeDoc( xmlDoc );
		return false;
	}

	processXMLnodePtr(_pEmaConfig, _xmlNodePtr);
	xmlFreeDoc( xmlDoc );
	return true;
}

void OmmConsumerConfigImpl::processXMLnodePtr(XMLnode * theNode, const xmlNodePtr & nodePtr)
{
	// add attibutes
	if ( nodePtr->properties )
	{
		xmlChar *value(0);
		for (xmlAttrPtr attrPtr = nodePtr->properties; attrPtr != NULL; attrPtr = attrPtr->next)
		{
			if (! xmlStrcmp(attrPtr->name, reinterpret_cast<const xmlChar *>("value")))
				value = xmlNodeListGetString(attrPtr->doc, attrPtr->children, 1);
			else {
				EmaString errorMsg( "got unexpected name [" );
				errorMsg.append( reinterpret_cast<const char *>( attrPtr->name ) ).append( "] while processing XML data; ignored" );
				theNode->appendErrorMessage(errorMsg, OmmLoggerClient::VerboseEnum);
			}
		}

		static EmaString errorMsg;
		ConfigElement * e( createConfigElement( reinterpret_cast< const char * >( nodePtr->name ), theNode->parent(),
			reinterpret_cast< const char * >( value ), errorMsg ) );
		if (e)
			theNode->addAttribute(e);
		else if ( ! errorMsg.empty() ) {
			theNode->appendErrorMessage(errorMsg, OmmLoggerClient::ErrorEnum);
			errorMsg.clear();
		}

		if (value)
			xmlFree(value);
	}

	for ( xmlNodePtr childNodePtr = nodePtr->children; childNodePtr; childNodePtr = childNodePtr->next )
	{
		if ( xmlIsBlankNode(childNodePtr) )
			continue;
		if (childNodePtr->type == XML_COMMENT_NODE)
			continue;

		switch ( childNodePtr->type )
		{
		case XML_TEXT_NODE:
		case XML_PI_NODE:
			break;
		case XML_ELEMENT_NODE:
			{
				XMLnode * child(new XMLnode(reinterpret_cast<const char *>(childNodePtr->name), theNode->level() + 1, theNode ) );
				static int instance(0);
				++instance;
				processXMLnodePtr(child, childNodePtr);
				if (child->errorCount()) {
					theNode->errors().add(child->errors());
					child->errors().clear();
				}
				--instance;

				if (childNodePtr->properties && ! childNodePtr->children) {
					theNode->appendAttributes(child->attributes(), true);
					delete child;
				}
				else if (! childNodePtr->properties && childNodePtr->children) {
					if (theNode->addChild(child))
					{
						delete child;
					}
				}
				else if (! childNodePtr->properties && ! childNodePtr->children) {
					EmaString errorMsg( "node [" );
					errorMsg.append( reinterpret_cast<const char *>( childNodePtr->name ) ).append( "has neither children nor attributes" );
					theNode->appendErrorMessage(errorMsg, OmmLoggerClient::VerboseEnum);
				}
				else {
					EmaString errorMsg( "node [" );
					errorMsg.append( reinterpret_cast<const char *>( childNodePtr->name ) ).append( "has both children and attributes; node was ignored" );
					theNode->appendErrorMessage(errorMsg, OmmLoggerClient::ErrorEnum);
				}
				break;
			}
		default:
			EmaString errorMsg( "childNodePtr has unhandled type [" );
			errorMsg.append( childNodePtr->type ).append( "]" );
			theNode->appendErrorMessage(errorMsg, OmmLoggerClient::VerboseEnum);
		}
	}
}
void OmmConsumerConfigImpl::createNameToValueHashTable()
{
	for( int i = 0; i < sizeof AsciiValues / sizeof( EmaString ); ++i )
		nameToValueHashTable.insert(AsciiValues[i], ConfigElement::ConfigElementTypeAscii);
	for ( int i = 0; i < sizeof EnumeratedValues / sizeof( EmaString ); ++i)
		nameToValueHashTable.insert(EnumeratedValues[i], ConfigElement::ConfigElementTypeEnum);
	for( int i = 0; i < sizeof Int64Values / sizeof( EmaString ); ++i )
		nameToValueHashTable.insert(Int64Values[i], ConfigElement::ConfigElementTypeInt64);
	for( int i = 0; i < sizeof UInt64Values / sizeof( EmaString ); ++i )
		nameToValueHashTable.insert(UInt64Values[i], ConfigElement::ConfigElementTypeUInt64);
}

ConfigElement* OmmConsumerConfigImpl::createConfigElement( const char * name, XMLnode * parent, const char* value, EmaString & errorMsg )
{
	ConfigElement * e( 0 );
	ConfigElement::ConfigElementType * elementType = nameToValueHashTable.find(name);
	if ( elementType == 0 )
		errorMsg.append( "unsupported configuration element [").append(name).append("]; element ignored");
	else switch( *elementType )
	{
	case ConfigElement::ConfigElementTypeAscii:
		e = new XMLConfigElement<EmaString>( name, parent, ConfigElement::ConfigElementTypeAscii, value );
		break;
	case ConfigElement::ConfigElementTypeEnum:
		e = convertEnum(name, parent, value, errorMsg);
		break;
	case ConfigElement::ConfigElementTypeInt64:
		{
			if ( ! validateConfigElement( value, ConfigElement::ConfigElementTypeInt64 ) )
			{
				errorMsg.append( "value [").append(value).append("] for config element [").append(name).append("] is not a signed integer; element ignored");
				break;
			}
            Int64 converted;
#ifdef WIN32
            converted = _strtoi64( value, 0, 0 );
#else
            converted = strtoll(value, 0, 0);
#endif
			e = new XMLConfigElement<Int64>( name, parent, ConfigElement::ConfigElementTypeInt64, converted );
		}
		break;
	case ConfigElement::ConfigElementTypeUInt64:
		{
			if ( ! validateConfigElement( value, ConfigElement::ConfigElementTypeUInt64 ) )
			{
				errorMsg.append( "value [").append(value).append("] for config element [").append(name).append("] is not an unsigned integer; element ignored");
				break;
			}
            UInt64 converted;
#ifdef WIN32
            converted = _strtoui64( value, 0, 0 );
#else
            converted = strtoull(value, 0, 0);
#endif
			e = new XMLConfigElement<UInt64>( name, parent, ConfigElement::ConfigElementTypeUInt64, converted );
		}
		break;
	default:
		errorMsg.append( "config element [").append(name).append("] had unexpected elementType [").append(*elementType).append("; element ignored");
		break;
	}
	return e;
}

bool OmmConsumerConfigImpl::validateConfigElement( const char * value, ConfigElement::ConfigElementType valueType )
{
	if ( valueType == ConfigElement::ConfigElementTypeInt64 || valueType == ConfigElement::ConfigElementTypeUInt64 )
	{
		if ( ! strlen( value ) )
			return false;
		const char *p = value;
		if ( valueType == ConfigElement::ConfigElementTypeInt64 && ! isdigit ( *p ) )
		{
			if ( *p++ != '-' )
				return false;
			if ( ! *p )
				return false;
		}
		for (; *p ; ++p)
			if ( ! isdigit( *p ) )
				return false;
		return true;
	}

	return false;
}


ConfigElement* OmmConsumerConfigImpl::convertEnum(const char * name, XMLnode * parent, const char * value, EmaString & errorMsg)
{
	EmaString enumValue(value);
	int colonPosition(enumValue.find("::"));
	if (colonPosition == -1) {
		errorMsg.append( "invalid Enum value format [" ).append( value ).append( "]; expected typename::value (e.g., OperationModel::ApiDispatch)" );
		return 0;
	}

	EmaString enumType(enumValue.substr(0, colonPosition));
	enumValue = enumValue.substr(colonPosition + 2, enumValue.length() - colonPosition - 2);

	if ( ! strcmp(enumType, "LoggerSeverity"))
	{
		static struct { const char * configInput; OmmLoggerClient::Severity convertedValue; } converter[] = 
		{
			{"Verbose", OmmLoggerClient::VerboseEnum},
			{"Success", OmmLoggerClient::SuccessEnum},
			{"Warning", OmmLoggerClient::WarningEnum},
			{"Error", OmmLoggerClient::ErrorEnum},
			{"NoLogMsg", OmmLoggerClient::NoLogMsgEnum}
		};
		for (int i = 0; i < sizeof converter/sizeof converter[0]; i++)
			if ( ! strcmp(converter[i].configInput, enumValue) )
				return new XMLConfigElement<OmmLoggerClient::Severity>(name, parent, ConfigElement::ConfigElementTypeEnum, converter[i].convertedValue);
	}
	else if (! strcmp( enumType, "LoggerType" ) )
	{
		static struct { const char * configInput; OmmLoggerClient::LoggerType convertedValue; } converter[] = 
		{
			{ "File", OmmLoggerClient::FileEnum},
			{ "Stdout", OmmLoggerClient::StdoutEnum},
		};
		for (int i = 0; i < sizeof converter/sizeof converter[0]; i++)
			if ( ! strcmp(converter[i].configInput, enumValue) )
				return new XMLConfigElement<OmmLoggerClient::LoggerType>(name, parent, ConfigElement::ConfigElementTypeEnum, converter[i].convertedValue);
	}
	else if (! strcmp( enumType, "DictionaryType" ) )
	{
		static struct { const char * configInput; Dictionary::DictionaryType convertedValue; } converter[] = 
		{
			{ "FileDictionary", Dictionary::FileDictionaryEnum},
			{ "ChannelDictionary", Dictionary::ChannelDictionaryEnum},
		};
		for (int i = 0; i < sizeof converter/sizeof converter[0]; i++)
			if ( ! strcmp(converter[i].configInput, enumValue) )
				return new XMLConfigElement<Dictionary::DictionaryType>(name, parent, ConfigElement::ConfigElementTypeEnum, converter[i].convertedValue);
	}
	else if (! strcmp( enumType, "ChannelType" ) )
	{
		static struct { const char * configInput; RsslConnectionTypes convertedValue; } converter[] = 
		{
			{ "RSSL_SOCKET", RSSL_CONN_TYPE_SOCKET},
			{ "RSSL_HTTP", RSSL_CONN_TYPE_HTTP},
			{ "RSSL_ENCRYPTED", RSSL_CONN_TYPE_ENCRYPTED},
			{ "RSSL_RELIABLE_MCAST", RSSL_CONN_TYPE_RELIABLE_MCAST},
		};
		for (int i = 0; i < sizeof converter/sizeof converter[0]; i++)
			if ( ! strcmp(converter[i].configInput, enumValue) )
				return new XMLConfigElement<RsslConnectionTypes>(name, parent, ConfigElement::ConfigElementTypeEnum, converter[i].convertedValue);
	}
	else if (! strcmp( enumType, "CompressionType" ) )
	{
		static struct { const char * configInput; RsslCompTypes convertedValue; } converter[] = 
		{
			{ "None", RSSL_COMP_NONE},
			{ "ZLib", RSSL_COMP_ZLIB},
			{ "LZ4", RSSL_COMP_LZ4},
		};
		for (int i = 0; i < sizeof converter/sizeof converter[0]; i++)
			if ( ! strcmp(converter[i].configInput, enumValue) )
				return new XMLConfigElement<RsslCompTypes>(name, parent, ConfigElement::ConfigElementTypeEnum, converter[i].convertedValue);
	}
	else {
		errorMsg.append( "no implementation in convertEnum for enumType [" ).append( enumType.c_str() ).append( "]");
		return 0;
	}

	errorMsg.append("convertEnum has an implementation for enumType [" ).append( enumType.c_str() ).append( "] but no appropriate conversion for value [" ).append( enumValue.c_str() ).append( "]" );
	return 0;
}

void XMLnode::print(int tabs)
{
	printf("%s (level %d, this %p, parent %p)\n", _name.c_str(), _level, this, _parent);
	fflush(stdout);
	++tabs;
	_attributes->print(tabs);
	_children->print(tabs);
	--tabs;
}

void XMLnode::appendErrorMessage( const EmaString & errorMsg, OmmLoggerClient::Severity severity )
{
	if ( _parent )
		_parent->appendErrorMessage( errorMsg, severity );
	else
	{
		EmaConfigError * mce( new EmaConfigError( errorMsg, severity ) );
		_errors.add( mce );
	}
}

void EmaConfigErrorList::add(EmaConfigError * mce)
{
	listElement * le( new listElement( mce) );
	if (theList) {
		listElement * p;
		for (p = theList; p; p = p->next)
			if (! p->next)
				break;
		p->next = le;
	}
	else
		theList = le;
	++_count;
}

void EmaConfigErrorList::add(EmaConfigErrorList & eL)
{
	if ( eL._count ) {
		if (theList) {
			listElement *p = theList;
			while (p->next)
				p = p->next;
			p->next = eL.theList;
		}
		else
			theList = eL.theList;
		_count += eL.count();
	}
};

void EmaConfigErrorList::clear()
{
	if ( theList )
	{
		listElement * q;
		for ( listElement * p = theList; p; p = q )
		{
			q = p->next;
			delete( p->error );
			delete( p );
		}
		theList = 0;
		
	}
	_count = 0;
}

void EmaConfigErrorList::printErrors(thomsonreuters::ema::access::OmmLoggerClient::Severity severity)
{
	bool printed( false );
	if ( theList )
	{
		for ( listElement * p = theList; p; p = p= p->next )
			if ( p->error->severity() >= severity )
			{
				if ( ! printed )
				{
					printf( "begin configuration errors:\n" );
					printed = true;
				}
				printf( "\t[%s] %s\n", OmmLoggerClient::loggerSeverityString(p->error->severity()), p->error->errorMsg().c_str() );
			}
		if (printed)
			printf( "end configuration errors\n" );	
		else
			printf( "no configuration errors existed with level equal to or exceeding %s\n", OmmLoggerClient::loggerSeverityString( severity ) );
	}
	else
		printf( "no configuration errors found\n" );
	
}

void EmaConfigErrorList::log(OmmLoggerClient * logger, OmmLoggerClient::Severity severity)
{
	for ( listElement * p = theList; p; p = p->next )
		if (p->error->severity() >= severity)
				logger->log("EmaConfig", p->error->severity(), p->error->errorMsg().c_str());
}

namespace thomsonreuters {

namespace ema {

namespace access {

template<>
bool
XMLConfigElement<Int64>::operator== ( const XMLConfigElement<Int64> & rhs ) const
{
	return _value == rhs._value;
}

template<>
bool
XMLConfigElement<EmaString>::operator== ( const XMLConfigElement<EmaString> & rhs ) const
{
	return _value == rhs._value;
}

}

}

}

bool ConfigElement::operator== (const ConfigElement & rhs) const
{
	if ( _name == rhs._name && type() == rhs.type() )
		switch(type()) {
		case ConfigElementTypeInt64:
			{
				const XMLConfigElement<Int64> & l = dynamic_cast<const XMLConfigElement<Int64> &>(*this);
				const XMLConfigElement<Int64> & r = dynamic_cast<const XMLConfigElement<Int64> &>(rhs);
				return l == r;
				break;
			}
		case ConfigElementTypeAscii:
			{
				const XMLConfigElement<EmaString> & l = dynamic_cast<const XMLConfigElement<EmaString> &>( *this );
				const XMLConfigElement<EmaString> & r = dynamic_cast<const XMLConfigElement<EmaString> &>( rhs);
				return l == r;
				break;
			}
		case ConfigElementTypeEnum :
			{
				break;
			}
		}
	return false;
}

void ConfigElement::appendErrorMessage( EmaString & errorMsg, OmmLoggerClient::Severity severity )
{
	_parent->appendErrorMessage( errorMsg, severity );
}

namespace thomsonreuters {

namespace ema {

namespace access {

template< typename T >
EmaString
XMLConfigElement< T >::changeMessage( const EmaString & actualName, const XMLConfigElement< T > & newElement ) const
{
	EmaString msg( "value for element [");
	if ( actualName.empty() )
		msg.append( newElement.name() );
	else
		msg.append( actualName ).append( "|" ).append( newElement.name() );
	msg.append("] changing from [").append(*(const_cast<XMLConfigElement<T> *>(this)->value())).append("] to [")
		.append(*(const_cast<XMLConfigElement<T> &>(newElement).value())).append("]");
	return msg;
}

}

}

}

EmaString ConfigElement::changeMessage( const EmaString & actualName, const ConfigElement & newElement ) const
{
	switch(type()) {
	case ConfigElementTypeAscii:
		{
			const XMLConfigElement<EmaString> & l = dynamic_cast<const XMLConfigElement<EmaString> &>( *this );
			const XMLConfigElement<EmaString> & r = dynamic_cast<const XMLConfigElement<EmaString> &>( newElement );
			EmaString retVal(l.changeMessage(actualName, r));
			return retVal;
		}
	case ConfigElementTypeInt64:
		{
			const XMLConfigElement< Int64 > & l = dynamic_cast< const XMLConfigElement< Int64 > & >( *this );
			const XMLConfigElement< Int64 > & r = dynamic_cast< const XMLConfigElement< Int64 > & >( newElement );
			EmaString retVal(l.changeMessage(actualName, r));
			return retVal;
		}
	case ConfigElementTypeUInt64:
		{
			const XMLConfigElement< UInt64 > & l = dynamic_cast< const XMLConfigElement< UInt64 > & >( *this );
			const XMLConfigElement< UInt64 > & r = dynamic_cast< const XMLConfigElement< UInt64 > & >( newElement );
			EmaString retVal(l.changeMessage(actualName, r));
			return retVal;
		}
	default:
		{
			EmaString defaultMsg("element [");
			defaultMsg.append(newElement.name()).append("] change; no information on exact change in values");
			return defaultMsg;
		}
	}
}

AdminReqMsg::AdminReqMsg( OmmConsumerConfigImpl& ommConsConfigImpl ) :
 _ommConsConfigImpl( ommConsConfigImpl )
{
	rsslClearRequestMsg( &_rsslMsg );
	rsslClearBuffer( &_name );
	rsslClearBuffer( &_header );
	rsslClearBuffer( &_attrib );
	rsslClearBuffer( &_payload );
}

AdminReqMsg::~AdminReqMsg()
{
	if ( _name.data )
		free( _name.data );

	if ( _attrib.data )
		free( _attrib.data );

	if ( _payload.data )
		free( _payload.data );
}

AdminReqMsg& AdminReqMsg::set( RsslRequestMsg* pRsslRequestMsg )
{
	_rsslMsg = *pRsslRequestMsg;

	if ( _rsslMsg.flags & RSSL_RQMF_HAS_EXTENDED_HEADER )
	{
		if ( _rsslMsg.extendedHeader.length > _header.length )
		{
			if ( _header.data ) free( _header.data );

			_header.data = (char*)malloc( _rsslMsg.extendedHeader.length );
			_header.length = _rsslMsg.extendedHeader.length;
		}

		memcpy( _header.data, _rsslMsg.extendedHeader.data, _header.length );

		_rsslMsg.extendedHeader = _header;
	}
	else
	{
		rsslClearBuffer( &_rsslMsg.extendedHeader );
	}

	if ( _rsslMsg.msgBase.containerType != RSSL_DT_NO_DATA )
	{
		if ( _rsslMsg.msgBase.encDataBody.length > _payload.length )
		{
			if ( _payload.data ) free( _payload.data );

			_payload.data = (char*)malloc( _rsslMsg.msgBase.encDataBody.length );
			_payload.length = _rsslMsg.msgBase.encDataBody.length;
		}

		memcpy( _payload.data, _rsslMsg.msgBase.encDataBody.data, _payload.length );

		_rsslMsg.msgBase.encDataBody = _payload;
	}
	else
	{
		rsslClearBuffer( & _rsslMsg.msgBase.encDataBody );
	}

	if ( _rsslMsg.msgBase.msgKey.flags & RSSL_MKF_HAS_ATTRIB )
	{
		if ( _rsslMsg.msgBase.msgKey.encAttrib.length > _attrib.length )
		{
			if ( _attrib.data ) free( _attrib.data );

			_attrib.data = (char*)malloc( _rsslMsg.msgBase.msgKey.encAttrib.length );
			_attrib.length = _rsslMsg.msgBase.msgKey.encAttrib.length;
		}

		memcpy( _attrib.data, _rsslMsg.msgBase.msgKey.encAttrib.data, _attrib.length );

		_rsslMsg.msgBase.msgKey.encAttrib = _attrib;
	}
	else
	{
		rsslClearBuffer( & _rsslMsg.msgBase.msgKey.encAttrib );
	}

	if ( _rsslMsg.msgBase.msgKey.flags & RSSL_MKF_HAS_NAME )
	{
		if ( _rsslMsg.msgBase.msgKey.name.length > _name.length )
		{
			if ( _name.data ) free( _name.data );

			_name.data = (char*)malloc( _rsslMsg.msgBase.msgKey.name.length );
			_name.length = _rsslMsg.msgBase.msgKey.name.length;
		}

		memcpy( _name.data, _rsslMsg.msgBase.msgKey.name.data, _name.length );

		_rsslMsg.msgBase.msgKey.name = _name;
	}
	else
	{
		rsslClearBuffer( & _rsslMsg.msgBase.msgKey.name );
	}

	return *this;
}

AdminReqMsg& AdminReqMsg::clear()
{
	rsslClearRequestMsg( &_rsslMsg );

	return *this;
}

RsslRequestMsg* AdminReqMsg::get()
{
	return &_rsslMsg;
}

LoginRdmReqMsg::LoginRdmReqMsg( OmmConsumerConfigImpl& ommConsConfigImpl ) :
 _ommConsConfigImpl( ommConsConfigImpl ),
 _username(),
 _password(),
 _position(),
 _applicationId(),
 _applicationName()
{
	rsslClearRDMLoginRequest( &_rsslRdmLoginRequest );
	_rsslRdmLoginRequest.rdmMsgBase.streamId = 1;
}

LoginRdmReqMsg::~LoginRdmReqMsg()
{
}

LoginRdmReqMsg& LoginRdmReqMsg::clear()
{
	_username.clear();
	_password.clear();
	_position.clear();
	_applicationId.clear();
	_applicationName.clear();
	rsslClearRDMLoginRequest( &_rsslRdmLoginRequest );
	return *this;
}

LoginRdmReqMsg& LoginRdmReqMsg::set( RsslRequestMsg* pRsslRequestMsg )
{	
	_rsslRdmLoginRequest.rdmMsgBase.domainType = RSSL_DMT_LOGIN;
	_rsslRdmLoginRequest.rdmMsgBase.rdmMsgType = RDM_LG_MT_REQUEST;
	_rsslRdmLoginRequest.flags = RDM_LG_RQF_NONE;

	if ( pRsslRequestMsg->msgBase.msgKey.flags & RSSL_MKF_HAS_NAME_TYPE )
	{
		_rsslRdmLoginRequest.userNameType = pRsslRequestMsg->msgBase.msgKey.nameType;
		_rsslRdmLoginRequest.flags |= RDM_LG_RQF_HAS_USERNAME_TYPE;
	}
	else
		_rsslRdmLoginRequest.flags &= ~RDM_LG_RQF_HAS_USERNAME_TYPE;

	if ( pRsslRequestMsg->msgBase.msgKey.flags & RSSL_MKF_HAS_NAME )
	{
		_username.set( pRsslRequestMsg->msgBase.msgKey.name.data, pRsslRequestMsg->msgBase.msgKey.name.length );
		_rsslRdmLoginRequest.userName.data = (char*)_username.c_str();
		_rsslRdmLoginRequest.userName.length = _username.length();
	}
	else
	{
		_username.clear();
		rsslClearBuffer( &_rsslRdmLoginRequest.userName );
	}

	if ( ( pRsslRequestMsg->msgBase.msgKey.flags & RSSL_MKF_HAS_ATTRIB ) &&
		pRsslRequestMsg->msgBase.msgKey.attribContainerType == RSSL_DT_ELEMENT_LIST )
	{
		RsslDecodeIterator dIter;

		rsslClearDecodeIterator( &dIter );

		RsslRet retCode = rsslSetDecodeIteratorRWFVersion( &dIter, RSSL_RWF_MAJOR_VERSION, RSSL_RWF_MINOR_VERSION );
		if ( retCode != RSSL_RET_SUCCESS )
		{
			EmaString temp( "Internal error. Failed to set RsslDecodeIterator's version in LoginRdmReqMsg::set(). Attributes will be skipped." );
			_ommConsConfigImpl.appendConfigError( temp, OmmLoggerClient::ErrorEnum );
			return *this;
		}

		retCode = rsslSetDecodeIteratorBuffer( &dIter, &pRsslRequestMsg->msgBase.msgKey.encAttrib );
		if ( retCode != RSSL_RET_SUCCESS )
		{
			EmaString temp( "Internal error. Failed to set RsslDecodeIterator's Buffer in LoginRdmReqMsg::set(). Attributes will be skipped." );
			_ommConsConfigImpl.appendConfigError( temp, OmmLoggerClient::ErrorEnum );
			return *this;
		}

		RsslElementList elementList;
		rsslClearElementList( &elementList );
		retCode = rsslDecodeElementList( &dIter, &elementList, 0 );
		if ( retCode != RSSL_RET_SUCCESS )
		{
			if ( retCode != RSSL_RET_NO_DATA )
			{
				EmaString temp( "Internal error while decoding element list containing login attributes. Error='" );
				temp.append( rsslRetCodeToString( retCode ) ).append( "'. Attributes will be skipped." );
				_ommConsConfigImpl.appendConfigError( temp, OmmLoggerClient::ErrorEnum );
				return *this;
			}

			return *this;
		}

		RsslElementEntry elementEntry;
		rsslClearElementEntry( &elementEntry );

		retCode = rsslDecodeElementEntry( &dIter, &elementEntry );

		while ( retCode != RSSL_RET_END_OF_CONTAINER )
		{
			if ( retCode != RSSL_RET_SUCCESS )
			{
				EmaString temp( "Internal error while decoding element entry with a login attribute. Error='" );
				temp.append( rsslRetCodeToString( retCode ) ).append( "'. Attribute will be skipped." );
				_ommConsConfigImpl.appendConfigError( temp, OmmLoggerClient::WarningEnum );
				continue;
			}

			if ( rsslBufferIsEqual( &elementEntry.name, &RSSL_ENAME_SINGLE_OPEN ) )
			{
				retCode = rsslDecodeUInt( &dIter, &_rsslRdmLoginRequest.singleOpen );
				if ( retCode != RSSL_RET_SUCCESS )
				{
					EmaString temp( "Internal error while decoding login attribute of single open. Error='" );
					temp.append( rsslRetCodeToString( retCode ) ).append( "'. Attribute will be skipped." );
					_ommConsConfigImpl.appendConfigError( temp, OmmLoggerClient::WarningEnum );
					continue;
				}
			
				_rsslRdmLoginRequest.flags |= RDM_LG_RQF_HAS_SINGLE_OPEN;
			}
			else if ( rsslBufferIsEqual( &elementEntry.name, &RSSL_ENAME_ALLOW_SUSPECT_DATA ) )
			{
				retCode = rsslDecodeUInt( &dIter, &_rsslRdmLoginRequest.allowSuspectData );
				if ( retCode != RSSL_RET_SUCCESS )
				{
					EmaString temp( "Internal error while decoding login attribute of allow suspect data. Error='" );
					temp.append( rsslRetCodeToString( retCode ) ).append( "'. Attribute will be skipped." );
					_ommConsConfigImpl.appendConfigError( temp, OmmLoggerClient::WarningEnum );
					continue;
				}

				_rsslRdmLoginRequest.flags |= RDM_LG_RQF_HAS_ALLOW_SUSPECT_DATA;
			}
			else if ( rsslBufferIsEqual( &elementEntry.name, &RSSL_ENAME_APPID ) )
			{
				retCode = rsslDecodeBuffer( &dIter, &_rsslRdmLoginRequest.applicationId );
				if ( retCode != RSSL_RET_SUCCESS )
				{
					EmaString temp( "Internal error while decoding login attribute of application id. Error='" );
					temp.append( rsslRetCodeToString( retCode ) ).append( "'. Attribute will be skipped." );
					_ommConsConfigImpl.appendConfigError( temp, OmmLoggerClient::WarningEnum );
					continue;
				}

				_applicationId.set( _rsslRdmLoginRequest.applicationId.data, _rsslRdmLoginRequest.applicationId.length );
				_rsslRdmLoginRequest.applicationId.data = (char*)_applicationId.c_str();
				_rsslRdmLoginRequest.applicationId.length = _applicationId.length();
				_rsslRdmLoginRequest.flags |= RDM_LG_RQF_HAS_APPLICATION_ID;
			}
			else if ( rsslBufferIsEqual( &elementEntry.name, &RSSL_ENAME_APPNAME ) )
			{
				retCode = rsslDecodeBuffer( &dIter, &_rsslRdmLoginRequest.applicationName );
				if ( retCode != RSSL_RET_SUCCESS )
				{
					EmaString temp( "Internal error while decoding login attribute of application name. Error='" );
					temp.append( rsslRetCodeToString( retCode ) ).append( "'. Attribute will be skipped." );
					_ommConsConfigImpl.appendConfigError( temp, OmmLoggerClient::WarningEnum );
					continue;
				}

				_applicationName.set( _rsslRdmLoginRequest.applicationName.data, _rsslRdmLoginRequest.applicationName.length );
				_rsslRdmLoginRequest.applicationName.data = (char*)_applicationName.c_str();
				_rsslRdmLoginRequest.applicationName.length = _applicationName.length();
				_rsslRdmLoginRequest.flags |= RDM_LG_RQF_HAS_APPLICATION_NAME;
			}
			else if ( rsslBufferIsEqual( &elementEntry.name, &RSSL_ENAME_POSITION ) )
			{
				retCode = rsslDecodeBuffer( &dIter, &_rsslRdmLoginRequest.position );
				if ( retCode != RSSL_RET_SUCCESS )
				{
					EmaString temp( "Internal error while decoding login attribute of position. Error='" );
					temp.append( rsslRetCodeToString( retCode ) ).append( "'. Attribute will be skipped." );
					_ommConsConfigImpl.appendConfigError( temp, OmmLoggerClient::WarningEnum );
					continue;
				}

				_position.set( _rsslRdmLoginRequest.position.data, _rsslRdmLoginRequest.position.length );
				_rsslRdmLoginRequest.position.data = (char*)_position.c_str();
				_rsslRdmLoginRequest.position.length = _position.length();
				_rsslRdmLoginRequest.flags |= RDM_LG_RQF_HAS_POSITION;
			}
			else if ( rsslBufferIsEqual( &elementEntry.name, &RSSL_ENAME_PASSWORD ) )
			{
				retCode = rsslDecodeBuffer( &dIter, &_rsslRdmLoginRequest.password );
				if ( retCode != RSSL_RET_SUCCESS )
				{
					EmaString temp( "Internal error while decoding login attribute of password. Error='" );
					temp.append( rsslRetCodeToString( retCode ) ).append( "'. Attribute will be skipped." );
					_ommConsConfigImpl.appendConfigError( temp, OmmLoggerClient::WarningEnum );
					continue;
				}

				_password.set( _rsslRdmLoginRequest.password.data, _rsslRdmLoginRequest.password.length );
				_rsslRdmLoginRequest.password.data = (char*)_password.c_str();
				_rsslRdmLoginRequest.password.length = _password.length();
				_rsslRdmLoginRequest.flags |= RDM_LG_RQF_HAS_PASSWORD;
			}
			else if ( rsslBufferIsEqual( &elementEntry.name, &RSSL_ENAME_PROV_PERM_PROF ) )
			{
				retCode = rsslDecodeUInt( &dIter, &_rsslRdmLoginRequest.providePermissionProfile );
				if ( retCode != RSSL_RET_SUCCESS )
				{
					EmaString temp( "Internal error while decoding login attribute of provide permission profile. Error='" );
					temp.append( rsslRetCodeToString( retCode ) ).append( "'. Attribute will be skipped." );
					_ommConsConfigImpl.appendConfigError( temp, OmmLoggerClient::WarningEnum );
					continue;
				}

				_rsslRdmLoginRequest.flags |= RDM_LG_RQF_HAS_PROVIDE_PERM_PROFILE;
			}
			else if ( rsslBufferIsEqual( &elementEntry.name, &RSSL_ENAME_PROV_PERM_EXP ) )
			{
				retCode = rsslDecodeUInt( &dIter, &_rsslRdmLoginRequest.providePermissionExpressions );
				if ( retCode != RSSL_RET_SUCCESS )
				{
					EmaString temp( "Internal error while decoding login attribute of provide permission expressions. Error='" );
					temp.append( rsslRetCodeToString( retCode ) ).append( "'. Attribute will be skipped." );
					_ommConsConfigImpl.appendConfigError( temp, OmmLoggerClient::WarningEnum );
					continue;
				}

				_rsslRdmLoginRequest.flags |= RDM_LG_RQF_HAS_PROVIDE_PERM_EXPR;
			}
			else if ( rsslBufferIsEqual( &elementEntry.name, &RSSL_ENAME_SUPPORT_PROVIDER_DICTIONARY_DOWNLOAD ) )
			{
				retCode = rsslDecodeUInt( &dIter, &_rsslRdmLoginRequest.supportProviderDictionaryDownload );
				if ( retCode != RSSL_RET_SUCCESS )
				{
					EmaString temp( "Internal error while decoding login attribute of support provider dictionary download. Error='" );
					temp.append( rsslRetCodeToString( retCode ) ).append( "'. Attribute will be skipped." );
					_ommConsConfigImpl.appendConfigError( temp, OmmLoggerClient::WarningEnum );
					continue;
				}

				_rsslRdmLoginRequest.flags |= RDM_LG_RQF_HAS_SUPPORT_PROV_DIC_DOWNLOAD;
			}
			else if ( rsslBufferIsEqual( &elementEntry.name, &RSSL_ENAME_DOWNLOAD_CON_CONFIG ) )
			{
				retCode = rsslDecodeUInt( &dIter, &_rsslRdmLoginRequest.downloadConnectionConfig );
				if ( retCode != RSSL_RET_SUCCESS )
				{
					EmaString temp( "Internal error while decoding login attribute of download connection configure. Error='" );
					temp.append( rsslRetCodeToString( retCode ) ).append( "'. Attribute will be skipped." );
					_ommConsConfigImpl.appendConfigError( temp, OmmLoggerClient::WarningEnum );
					continue;
				}

				_rsslRdmLoginRequest.flags |= RDM_LG_RQF_HAS_DOWNLOAD_CONN_CONFIG;
			}
			else if ( rsslBufferIsEqual( &elementEntry.name, &RSSL_ENAME_INST_ID ) )
			{
				retCode = rsslDecodeBuffer( &dIter, &_rsslRdmLoginRequest.instanceId );
				if ( retCode != RSSL_RET_SUCCESS )
				{
					EmaString temp( "Internal error while decoding login attribute of instance Id. Error='" );
					temp.append( rsslRetCodeToString( retCode ) ).append( "'. Attribute will be skipped." );
					_ommConsConfigImpl.appendConfigError( temp, OmmLoggerClient::WarningEnum );
					continue;
				}

				_rsslRdmLoginRequest.flags |= RDM_LG_RQF_HAS_INSTANCE_ID;
			}
			else if ( rsslBufferIsEqual( &elementEntry.name, &RSSL_ENAME_ROLE ) )
			{
				retCode = rsslDecodeUInt( &dIter, &_rsslRdmLoginRequest.role );
				if ( retCode != RSSL_RET_SUCCESS )
				{
					EmaString temp( "Internal error while decoding login attribute of role. Error='" );
					temp.append( rsslRetCodeToString( retCode ) ).append( "'. Attribute will be skipped." );
					_ommConsConfigImpl.appendConfigError( temp, OmmLoggerClient::WarningEnum );
					continue;
				}

				_rsslRdmLoginRequest.flags |= RDM_LG_RQF_HAS_ROLE;
			}

			retCode = rsslDecodeElementEntry( &dIter, &elementEntry );
		}
	}

	return *this;
}

RsslRDMLoginRequest* LoginRdmReqMsg::get()
{
	return &_rsslRdmLoginRequest;
}

LoginRdmReqMsg& LoginRdmReqMsg::username( const EmaString& value )
{
	_username = value;
	_rsslRdmLoginRequest.userName.data = (char*)_username.c_str();
	_rsslRdmLoginRequest.userName.length = _username.length();
	return *this;
}

LoginRdmReqMsg& LoginRdmReqMsg::position( const EmaString& value )
{
	_position = value;
	_rsslRdmLoginRequest.position.data = (char*)_position.c_str();
	_rsslRdmLoginRequest.position.length = _position.length();
	_rsslRdmLoginRequest.flags |= RDM_LG_RQF_HAS_POSITION;
	return *this;
}

LoginRdmReqMsg& LoginRdmReqMsg::password( const EmaString& value )
{
	_password = value;
	_rsslRdmLoginRequest.password.data = (char*)_password.c_str();
	_rsslRdmLoginRequest.password.length = _password.length();
	_rsslRdmLoginRequest.flags |= RDM_LG_RQF_HAS_PASSWORD;
	return *this;
}

LoginRdmReqMsg& LoginRdmReqMsg::applicationId( const EmaString& value )
{
	_applicationId = value;
	_rsslRdmLoginRequest.applicationId.data = (char*)_applicationId.c_str();
	_rsslRdmLoginRequest.applicationId.length = _applicationId.length();
	_rsslRdmLoginRequest.flags |= RDM_LG_RQF_HAS_APPLICATION_ID;
	return *this;
}

LoginRdmReqMsg& LoginRdmReqMsg::applicationName( const EmaString& value )
{
	_applicationName = value;
	_rsslRdmLoginRequest.applicationName.data = (char*)_applicationName.c_str();
	_rsslRdmLoginRequest.applicationName.length = _applicationName.length();
	_rsslRdmLoginRequest.flags |= RDM_LG_RQF_HAS_APPLICATION_NAME;
	return *this;
}

ProgrammaticConfigure::ProgrammaticConfigure( const Map& map, EmaConfigErrorList& emaConfigErrList ) :
_emaConfigErrList( emaConfigErrList ),
_configList(),
_nameflags( 0 ),
_loadnames( false ),
_consumerName(),
_channelName(),
_loggerName(),
_dictionaryName()
{
	addConfigure( map );
}

void ProgrammaticConfigure::clear()
{
	_nameflags = 0;
	_loadnames = false;
	_consumerName.clear();
	_channelName.clear();
	_loggerName.clear();
	_dictionaryName.clear();
}

void ProgrammaticConfigure::addConfigure( const Map& map )
{
	for( UInt32 i = 0; i < _configList.size() ; i ++ )
	{ 
		if ( _configList[i] == &map )
			return;
	}

	_configList.push_back( &map );
}

bool ProgrammaticConfigure::getDefaultConsumer ( EmaString& defaultConsumer )
{
	bool found = false;

	clear();

	for ( UInt32 i = 0 ; i < _configList.size() ; i++ )
		found = ProgrammaticConfigure::retrieveDefaultConsumer( *_configList[i], defaultConsumer );

	return found;
}

void ProgrammaticConfigure::retrieveDependencyNames( const Map& map, const EmaString& consumerName, UInt8& flags, EmaString& channelName, 
													EmaString& loggerName, EmaString& dictionaryName )
{
	map.reset();
	while ( !map.forth() )
	{
		const MapEntry & mapEntry = map.getEntry();

		if ( mapEntry.getKey().getDataType() == DataType::AsciiEnum && mapEntry.getKey().getAscii() == "ConsumerGroup" )
		{
			if ( mapEntry.getLoadType() == DataType::ElementListEnum )
			{
				const ElementList & elementList = mapEntry.getElementList();

				while ( !elementList.forth() )
				{
					const ElementEntry & elementEntry = elementList.getEntry();

					if ( elementEntry.getLoadType() == DataType::MapEnum )
					{
						if ( elementEntry.getName() == "ConsumerList" )
						{
							const Map& map = elementEntry.getMap();

							while ( !map.forth() )
							{
								const MapEntry & mapEntry = map.getEntry();

								if ( ( mapEntry.getKey().getDataType() == DataType::AsciiEnum ) && ( mapEntry.getKey().getAscii() == consumerName ) )
								{
									if ( mapEntry.getLoadType() == DataType::ElementListEnum )
									{
										const ElementList & elementListConsumer = mapEntry.getElementList();

										while ( !elementListConsumer.forth() )
										{
											const ElementEntry & consumerEntry = elementListConsumer.getEntry();

											switch ( consumerEntry.getLoadType() )
											{
												case DataType::AsciiEnum:
													if ( consumerEntry.getName() == "Channel" )
													{
														channelName = consumerEntry.getAscii();
														flags |= 0x01;
													}
													else if ( consumerEntry.getName() == "Logger" )
													{
														loggerName = consumerEntry.getAscii();
														flags |= 0x02;
													}
													else if ( consumerEntry.getName() == "Dictionary" )
													{
														dictionaryName = consumerEntry.getAscii();
														flags |= 0x04;
													}
													break;
											}
										}
									}
								}
							}
						}
					}
				}
			}
		}
	}
}

bool ProgrammaticConfigure::getActiveChannelName( const EmaString& consumerName, EmaString& channelName )
{
	if ( !_loadnames )
	{
		for ( UInt32 i = 0 ; i < _configList.size() ; i++ )
			retrieveDependencyNames( *_configList[i], consumerName, _nameflags,_channelName, _loggerName, _dictionaryName );

		_loadnames = true;
	}

	if ( _nameflags & 0x01 )
	{
		channelName = _channelName;
		return true;
	}
	else
		return false;
}

bool ProgrammaticConfigure::getActiveLoggerName( const EmaString& consumerName, EmaString& loggerName )
{
	if ( !_loadnames )
	{
		for ( UInt32 i = 0 ; i < _configList.size() ; i++ )
			retrieveDependencyNames( *_configList[i], consumerName, _nameflags,_channelName, _loggerName, _dictionaryName );

		_loadnames = true;
	}

	if ( _nameflags & 0x02 )
	{
		loggerName = _loggerName;
		return true;
	}
	else
		return false;
}


bool ProgrammaticConfigure::getActiveDictionaryName( const EmaString& consumerName, EmaString& dictionaryName )
{
	if ( !_loadnames )
	{
		for ( UInt32 i = 0 ; i < _configList.size() ; i++ )
			retrieveDependencyNames( *_configList[i], consumerName, _nameflags,_channelName, _loggerName, _dictionaryName );

		_loadnames = true;
	}

	if ( _nameflags & 0x04 )
	{
		dictionaryName = _dictionaryName;
		return true;
	}
	else
		return false;
}

bool ProgrammaticConfigure::retrieveDefaultConsumer( const Map& map, EmaString& defaultConsumer )
{
	bool foundDefaultConsumer = false;

	StaticDecoder::setData( &const_cast<Map &>(map), 0);

	while ( !map.forth() )
	{
		const MapEntry & mapEntry = map.getEntry();

		if ( mapEntry.getLoad().getDataType() == DataType::ElementListEnum )
		{
			const ElementList & elementList = mapEntry.getElementList();

			while ( !elementList.forth() )
			{
				const ElementEntry & elementEntry = elementList.getEntry();

				if ( elementEntry.getLoadType() == DataType::AsciiEnum )
				{
					if ( elementEntry.getName() == "DefaultConsumer" )
					{
						defaultConsumer = elementEntry.getAscii();
						foundDefaultConsumer = true;
					}
				}
			}
		}
	}

	return foundDefaultConsumer;
}

void  ProgrammaticConfigure::retrieveConsumerConfig( const EmaString& consumerName, OmmConsumerActiveConfig& activeConfig )
{
	for ( UInt32 i = 0 ; i < _configList.size() ; i++ )
		ProgrammaticConfigure::retrieveConsumer( *_configList[i], consumerName, _emaConfigErrList, activeConfig );
}

void  ProgrammaticConfigure::retrieveChannelConfig( const EmaString& channelName,  OmmConsumerActiveConfig& activeConfig, bool hostFnCalled )
{
	for ( UInt32 i = 0 ; i < _configList.size() ; i++ )
		ProgrammaticConfigure::retrieveChannel( *_configList[i], channelName, _emaConfigErrList, activeConfig, hostFnCalled );
}

void  ProgrammaticConfigure::retrieveLoggerConfig( const EmaString& loggerName, OmmConsumerActiveConfig& activeConfig )
{
	for ( UInt32 i = 0 ; i < _configList.size() ; i++ )
		ProgrammaticConfigure::retrieveLogger( *_configList[i], loggerName, _emaConfigErrList, activeConfig );
}

void  ProgrammaticConfigure::retrieveDictionaryConfig( const EmaString& dictionaryName, OmmConsumerActiveConfig& activeConfig )
{
	for ( UInt32 i = 0 ; i < _configList.size() ; i++ )
		ProgrammaticConfigure::retrieveDictionary( *_configList[i], dictionaryName, _emaConfigErrList, activeConfig );
}

void ProgrammaticConfigure::retrieveConsumer( const Map& map, const EmaString& consumerName, EmaConfigErrorList& emaConfigErrList,
												  OmmConsumerActiveConfig& ommConsumerActiveConfig )
{
	map.reset();
	while ( !map.forth() )
	{
		const MapEntry & mapEntry = map.getEntry();

		if ( mapEntry.getKey().getDataType() == DataType::AsciiEnum && mapEntry.getKey().getAscii() == "ConsumerGroup" )
		{
			if ( mapEntry.getLoadType() == DataType::ElementListEnum )
			{
				const ElementList & elementList = mapEntry.getElementList();

				while ( !elementList.forth() )
				{
					const ElementEntry & elementEntry = elementList.getEntry();

					if ( elementEntry.getLoadType() == DataType::MapEnum )
					{
						if ( elementEntry.getName() == "ConsumerList" )
						{
							const Map& map = elementEntry.getMap();

							while ( !map.forth() )
							{
								const MapEntry & mapEntry = map.getEntry();

								if ( ( mapEntry.getKey().getDataType() == DataType::AsciiEnum ) && ( mapEntry.getKey().getAscii() == consumerName ) )
								{
									if ( mapEntry.getLoadType() == DataType::ElementListEnum )
									{
										const ElementList & elementListConsumer = mapEntry.getElementList();

										while ( !elementListConsumer.forth() )
										{
											const ElementEntry & consumerEntry = elementListConsumer.getEntry();

											switch ( consumerEntry.getLoadType() )
											{
												case DataType::AsciiEnum:
													break;

												case DataType::UIntEnum:
													if ( consumerEntry.getName() == "ItemCountHint" )
													{
														ommConsumerActiveConfig.setItemCountHint(consumerEntry.getUInt());
													}
													else if ( consumerEntry.getName() == "ServiceCountHint" )
													{
														ommConsumerActiveConfig.setServiceCountHint(consumerEntry.getUInt());
													}
													else if ( consumerEntry.getName() == "ObeyOpenWindow" )
													{
														ommConsumerActiveConfig.setObeyOpenWindow(consumerEntry.getUInt());
													}
													else if ( consumerEntry.getName() == "PostAckTimeout" )
													{
														ommConsumerActiveConfig.setPostAckTimeout(consumerEntry.getUInt());
													}
													else if ( consumerEntry.getName() == "RequestTimeout" )
													{
														ommConsumerActiveConfig.setRequestTimeout(consumerEntry.getUInt());
													}
													else if ( consumerEntry.getName() == "MaxOutstandingPosts" )
													{
														ommConsumerActiveConfig.setMaxOutstandingPosts(consumerEntry.getUInt());
													}
													else if ( consumerEntry.getName() == "CatchUnhandledException" )
													{
														ommConsumerActiveConfig.setCatchUnhandledException(consumerEntry.getUInt());
													}
													else if ( consumerEntry.getName() == "MaxDispatchCountApiThread" )
													{
														ommConsumerActiveConfig.setMaxDispatchCountApiThread(consumerEntry.getUInt());
													}
													else if ( consumerEntry.getName() == "MaxDispatchCountUserThread" )
													{
														ommConsumerActiveConfig.setMaxDispatchCountUserThread(consumerEntry.getUInt());
													}
													else if ( consumerEntry.getName() == "LoginRequestTimeout" )
													{
														ommConsumerActiveConfig.setLoginRequestTimeOut(consumerEntry.getUInt());
													}
													else if ( consumerEntry.getName() == "DirectoryRequestTimeout" )
													{
														ommConsumerActiveConfig.setDirectoryRequestTimeOut(consumerEntry.getUInt());
													}
													break;
									
												case DataType::IntEnum:

													if ( consumerEntry.getName() == "DispatchTimeoutApiThread" )
													{
														ommConsumerActiveConfig.dispatchTimeoutApiThread = consumerEntry.getInt();
													}
													else if ( consumerEntry.getName() == "PipePort" )
													{
														ommConsumerActiveConfig.pipePort = consumerEntry.getInt();
													}
													break;
											}
										}
									}
								}
							}
						}
					}
				}
			}
		}
	}
}

void ProgrammaticConfigure::retrieveChannel( const Map& map, const EmaString& channelName, EmaConfigErrorList& emaConfigErrList,
											OmmConsumerActiveConfig& ommConsumerActiveConfig, bool hostFnCalled )
{
	map.reset();
	while ( !map.forth() )
	{
		const MapEntry& mapEntry = map.getEntry();

		if ( mapEntry.getKey().getDataType() == DataType::AsciiEnum && mapEntry.getKey().getAscii() == "ChannelGroup" )
		{
			if ( mapEntry.getLoadType() == DataType::ElementListEnum )
			{
				const ElementList& elementList = mapEntry.getElementList();

				while ( !elementList.forth())
				{
					const ElementEntry& elementEntry = elementList.getEntry();

					if ( elementEntry.getLoadType() == DataType::MapEnum )
					{
						if ( elementEntry.getName() == "ChannelList" )
						{
							const Map& map = elementEntry.getMap();

							while ( !map.forth() )
							{
								const MapEntry& mapEntry = map.getEntry();

								if ( ( mapEntry.getKey().getDataType() == DataType::AsciiEnum ) && ( mapEntry.getKey().getAscii() == channelName ) )
								{
									if ( mapEntry.getLoadType() == DataType::ElementListEnum )
									{
										const ElementList& elementListChannel = mapEntry.getElementList();

										EmaString name, interfaceName, host, port, xmlTraceFileName;
										thomsonreuters::ema::access::UInt16 channelType, compressionType;
										thomsonreuters::ema::access::Int64 reconnectAttemptLimit, reconnectMinDelay, reconnectMaxDelay, xmlTraceMaxFileSize;
										thomsonreuters::ema::access::UInt64 guaranteedOutputBuffers, connectionPingTimeout, tcpNodelay, xmlTraceToFile, xmlTraceToStdout,
											xmlTraceToMultipleFiles, xmlTraceWrite, xmlTraceRead, msgKeyInUpdates;

										thomsonreuters::ema::access::UInt64 flags = 0;

										while ( !elementListChannel.forth() )
										{
											const ElementEntry & channelEntry = elementListChannel.getEntry();

											switch ( channelEntry.getLoadType() )
											{
											case DataType::AsciiEnum:
												if ( channelEntry.getName() == "Host" )
												{
													host = channelEntry.getAscii();
													flags |= 0x02;
												}
												else if ( channelEntry.getName() == "Port" )
												{
													port = channelEntry.getAscii();
													flags |= 0x04;
												}
												else if ( channelEntry.getName() == "XmlTraceFileName" )
												{
													xmlTraceFileName = channelEntry.getAscii();
													flags |= 0x08;
												}
												else if ( channelEntry.getName() == "InterfaceName" )
												{
													interfaceName = channelEntry.getAscii();
													flags |= 0x80000;
												}
												break;

											case DataType::EnumEnum:
												if ( channelEntry.getName() == "ChannelType" )
												{
													channelType = channelEntry.getEnum();

													switch ( channelType )
													{
													case RSSL_CONN_TYPE_SOCKET:
													case RSSL_CONN_TYPE_RELIABLE_MCAST:
													case RSSL_CONN_TYPE_HTTP:
													case RSSL_CONN_TYPE_ENCRYPTED:
														flags |= 0x10;
														break;
													default:
														EmaString text( "Invalid ChannelType [");
														text.append( channelType );
														text.append("] in Programmatic Configuration. Use default ChannelType [");
														text.append( DEFAULT_CONNECTION_TYPE );
														text.append( "]");
														EmaConfigError * mce( new EmaConfigError( text, OmmLoggerClient::ErrorEnum ) );
														emaConfigErrList.add( mce );
														break;
													}
												}
												else if ( channelEntry.getName() == "CompressionType" )
												{
													compressionType = channelEntry.getEnum();

													if ( compressionType > 2 )
													{
														EmaString text( "Invalid CompressionType [");
														text.append( compressionType );
														text.append("] in Programmatic Configuration. Use default CompressionType [");
														text.append( DEFAULT_COMPRESSION_TYPE );
														text.append( "] ");
														EmaConfigError * mce( new EmaConfigError( text, OmmLoggerClient::ErrorEnum ) );
														emaConfigErrList.add( mce );
													}
													else
													{
														flags |= 0x20;
													}
												}
												break;

											case DataType::UIntEnum:
												if ( channelEntry.getName() == "GuaranteedOutputBuffers" )
												{
													guaranteedOutputBuffers = channelEntry.getUInt();
													flags |= 0x40;
												}
												else if ( channelEntry.getName() == "TcpNodelay" )
												{
													tcpNodelay = channelEntry.getUInt();
													flags |= 0x80;
												}
												else if ( channelEntry.getName() == "XmlTraceToFile" )
												{
													xmlTraceToFile = channelEntry.getUInt();
													flags |= 0x200;
												}
												else if ( channelEntry.getName() == "XmlTraceToStdout" )
												{
													xmlTraceToStdout = channelEntry.getUInt();
													flags |= 0x400;
												}
												else if ( channelEntry.getName() == "XmlTraceToMultipleFiles" )
												{
													xmlTraceToMultipleFiles = channelEntry.getUInt();
													flags |= 0x800;
												}
												else if ( channelEntry.getName() == "XmlTraceWrite" )
												{
													xmlTraceWrite = channelEntry.getUInt();
													flags |= 0x1000;
												}
												else if ( channelEntry.getName() == "XmlTraceRead" )
												{
													xmlTraceRead = channelEntry.getUInt();
													flags |= 0x2000;
												}
												else if ( channelEntry.getName() == "MsgKeyInUpdates" )
												{
													msgKeyInUpdates = channelEntry.getUInt();
													flags |= 0x4000;
												}
												else if ( channelEntry.getName() == "ConnectionPingTimeout" )
												{
													connectionPingTimeout = channelEntry.getUInt();
													flags |= 0x8000;
												}
												break;

											case DataType::IntEnum:
												if ( channelEntry.getName() == "XmlTraceMaxFileSize" )
												{
													xmlTraceMaxFileSize = channelEntry.getInt();
													flags |= 0x100;
												}
												else if ( channelEntry.getName() == "ReconnectAttemptLimit" )
												{
													reconnectAttemptLimit = channelEntry.getInt();
													flags |= 0x10000;
												}
												else if ( channelEntry.getName() == "ReconnectMinDelay" )
												{
													reconnectMinDelay = channelEntry.getInt();
													flags |= 0x20000;
												}
												else if ( channelEntry.getName() == "ReconnectMaxDelay" )
												{
													reconnectMaxDelay = channelEntry.getInt();
													flags |= 0x40000;
												}
												break;
											}
										}
										
										if ( flags & 0x10 )
										{
											if ( hostFnCalled )
												channelType = RSSL_CONN_TYPE_SOCKET;

											if ( ommConsumerActiveConfig.channelConfig )
											{
												if ( ommConsumerActiveConfig.channelConfig->getType() != channelType )
												{
													delete ommConsumerActiveConfig.channelConfig;
													ommConsumerActiveConfig.channelConfig = 0;
												}
											}

											try
											{
												if ( channelType == RSSL_CONN_TYPE_SOCKET )
												{
													SocketChannelConfig * socketChannelConfig;
													if ( ommConsumerActiveConfig.channelConfig == 0 )
													{
														socketChannelConfig = new SocketChannelConfig();
														ommConsumerActiveConfig.channelConfig = socketChannelConfig;
													}
													else
													{
														socketChannelConfig = (SocketChannelConfig *)ommConsumerActiveConfig.channelConfig;
													}

													if ( flags & 0x80 )
														socketChannelConfig->tcpNodelay = tcpNodelay ? true : false;

													if ( flags & 0x02 && ! hostFnCalled )
														socketChannelConfig->hostName = host;

													if ( flags & 0x04 && ! hostFnCalled )
														socketChannelConfig->serviceName = port;
												}
												else if ( channelType == RSSL_CONN_TYPE_RELIABLE_MCAST )
												{
													if ( ommConsumerActiveConfig.channelConfig == 0 )
													{
														ommConsumerActiveConfig.channelConfig = new ReliableMcastChannelConfig();
													}
												}
												else if ( channelType == RSSL_CONN_TYPE_HTTP )
												{
													HttpChannelConfig *httpChanelConfig;
													if ( ommConsumerActiveConfig.channelConfig == 0 )
													{
														httpChanelConfig = new HttpChannelConfig();
														ommConsumerActiveConfig.channelConfig = httpChanelConfig;
													}
													else
													{
														httpChanelConfig = (HttpChannelConfig *)ommConsumerActiveConfig.channelConfig;
													}

													if ( flags & 0x80 )
														httpChanelConfig->tcpNodelay = tcpNodelay ? true : false;

													if ( flags & 0x02 )
														httpChanelConfig->hostName = host;

													if ( flags & 0x04 )
														httpChanelConfig->serviceName = port;
												}
												else if ( channelType == RSSL_CONN_TYPE_ENCRYPTED )
												{
													EncryptedChannelConfig* encryptChanelConfig;
													if ( ommConsumerActiveConfig.channelConfig == 0 )
													{
														encryptChanelConfig = new EncryptedChannelConfig();
														ommConsumerActiveConfig.channelConfig = encryptChanelConfig;
													}
													else
													{
														encryptChanelConfig = (EncryptedChannelConfig*)ommConsumerActiveConfig.channelConfig;
													}

													if ( flags & 0x80 )
														encryptChanelConfig->tcpNodelay = tcpNodelay ? true : false;

													if ( flags & 0x02 )
														encryptChanelConfig->hostName = host;

													if ( flags & 0x04 )
														encryptChanelConfig->serviceName = port;;
												}
											} catch ( std::bad_alloc ) 
											{
												const char* temp = "Failed to allocate memory for ChannelConfig. Out of memory!";
												throwMeeException( temp );
											}

											ommConsumerActiveConfig.channelConfig->name = channelName;

											if ( flags & 0x80000 )
												ommConsumerActiveConfig.channelConfig->interfaceName = interfaceName;

											if ( flags & 0x20 )
												ommConsumerActiveConfig.channelConfig->compressionType = (RsslCompTypes)compressionType;

											if ( flags & 0x40 )
												ommConsumerActiveConfig.channelConfig->setGuaranteedOutputBuffers(guaranteedOutputBuffers);

											if ( flags & 0x8000 )
												ommConsumerActiveConfig.channelConfig->connectionPingTimeout = (UInt32)connectionPingTimeout;

											if ( flags & 0x10000 )
												ommConsumerActiveConfig.channelConfig->reconnectAttemptLimit = reconnectAttemptLimit;

											if ( flags & 0x20000 )
												ommConsumerActiveConfig.channelConfig->reconnectMinDelay = reconnectMinDelay;

											if ( flags & 0x40000 )
												ommConsumerActiveConfig.channelConfig->reconnectMaxDelay = reconnectMaxDelay;

											if ( flags & 0x08 )
												ommConsumerActiveConfig.channelConfig->xmlTraceFileName = xmlTraceFileName;

											if ( flags & 0x100 )
												ommConsumerActiveConfig.channelConfig->xmlTraceMaxFileSize = xmlTraceMaxFileSize;

											if ( flags & 0x200 )
												ommConsumerActiveConfig.channelConfig->xmlTraceToFile = xmlTraceToFile ? true : false;

											if ( flags & 0x400 )
												ommConsumerActiveConfig.channelConfig->xmlTraceToStdout = xmlTraceToStdout ? true : false;

											if ( flags & 0x800 )
												ommConsumerActiveConfig.channelConfig->xmlTraceToMultipleFiles = xmlTraceToMultipleFiles ? true : false;

											if ( flags & 0x1000 )
												ommConsumerActiveConfig.channelConfig->xmlTraceWrite = xmlTraceWrite ? true : false;

											if ( flags & 0x2000 )
												ommConsumerActiveConfig.channelConfig->xmlTraceRead = xmlTraceRead ? true : false;

											if ( flags & 0x4000 )
												ommConsumerActiveConfig.channelConfig->msgKeyInUpdates = msgKeyInUpdates ? true : false;
										}
									}
								}
							}
						}
					}
				}
			}
		}
	}
}

void ProgrammaticConfigure::retrieveLogger( const Map& map, const EmaString& loggerName, EmaConfigErrorList& emaConfigErrList,
										  OmmConsumerActiveConfig& ommConsumerActiveConfig )
{
	map.reset();
	while ( !map.forth() )
	{
		const MapEntry & mapEntry = map.getEntry();

		if ( mapEntry.getKey().getDataType() == DataType::AsciiEnum && mapEntry.getKey().getAscii() == "LoggerGroup" )
		{
			if ( mapEntry.getLoadType() == DataType::ElementListEnum )
			{
				const ElementList & elementList = mapEntry.getElementList();

				while ( !elementList.forth() )
				{
					const ElementEntry & elementEntry = elementList.getEntry();

					if ( elementEntry.getLoadType() == DataType::MapEnum )
					{
						if ( elementEntry.getName() == "LoggerList" )
						{
							const Map & map = elementEntry.getMap();

							while ( !map.forth() )
							{
								const MapEntry & mapEntry = map.getEntry();

								if ( ( mapEntry.getKey().getDataType() == DataType::AsciiEnum ) && ( mapEntry.getKey().getAscii() == loggerName ) )
								{
									if ( mapEntry.getLoadType() == DataType::ElementListEnum )
									{
										const ElementList & elementListLogger = mapEntry.getElementList();

										while ( !elementListLogger.forth() )
										{
											const ElementEntry & loggerEntry = elementListLogger.getEntry();

											switch ( loggerEntry.getLoadType() )
											{
											case DataType::AsciiEnum:
												if ( loggerEntry.getName() == "FileName" )
												{
													ommConsumerActiveConfig.loggerConfig.loggerFileName = loggerEntry.getAscii();
												}
												break;

											case DataType::EnumEnum:
												if ( loggerEntry.getName() == "LoggerType" )
												{
													UInt16 loggerType = loggerEntry.getEnum();

													if ( loggerType > 1 )
													{
														EmaString text( "Invalid LoggerType [");
														text.append( loggerType );
														text.append("] in Programmatic Configuration. Use default LoggerType [");
														text.append( DEFAULT_LOGGER_TYPE );
														text.append( "]" );
														EmaConfigError * mce( new EmaConfigError( text, OmmLoggerClient::ErrorEnum ) );
														emaConfigErrList.add( mce );
													}
													else
													{
														ommConsumerActiveConfig.loggerConfig.loggerType = (OmmLoggerClient::LoggerType)loggerType;
													}
												}
												else if ( loggerEntry.getName() == "LoggerSeverity" )
												{
													UInt16 severityType = loggerEntry.getEnum();

													if ( severityType > 4 )
													{
														EmaString text( "Invalid LoggerSeverity [");
														text.append( severityType );
														text.append("] in Programmatic Configuration. Use default LoggerSeverity [");
														text.append( DEFAULT_LOGGER_SEVERITY );
														text.append( "]" );
														EmaConfigError * mce( new EmaConfigError( text, OmmLoggerClient::ErrorEnum ) );
														emaConfigErrList.add( mce );
													}
													else
													{
														ommConsumerActiveConfig.loggerConfig.minLoggerSeverity = (OmmLoggerClient::Severity)severityType;
													}
												}
												break;
											}
										}
									}
								}
							}
						}
					}
				}
			}
		}
	}
}

void ProgrammaticConfigure::retrieveDictionary( const Map& map, const EmaString& dictionaryName, EmaConfigErrorList& emaConfigErrList,
											  OmmConsumerActiveConfig& ommConsumerActiveConfig )
{
	map.reset();
	while ( !map.forth() )
	{
		const MapEntry & mapEntry = map.getEntry();

		if ( mapEntry.getKey().getDataType() == DataType::AsciiEnum && mapEntry.getKey().getAscii() == "DictionaryGroup" )
		{
			if ( mapEntry.getLoadType() == DataType::ElementListEnum )
			{
				const ElementList & elementList = mapEntry.getElementList();

				while ( !elementList.forth() )
				{
					const ElementEntry & elementEntry = elementList.getEntry();

					if ( elementEntry.getLoadType() == DataType::MapEnum )
					{
						if ( elementEntry.getName() == "DictionaryList" )
						{
							const Map & map = elementEntry.getMap();

							while ( !map.forth() )
							{
								const MapEntry & mapEntry = map.getEntry();

								if ( ( mapEntry.getKey().getDataType() == DataType::AsciiEnum ) && ( mapEntry.getKey().getAscii() == dictionaryName ) )
								{
									if ( mapEntry.getLoadType() == DataType::ElementListEnum )
									{
										const ElementList & elementListDictionary = mapEntry.getElementList();

										while ( !elementListDictionary.forth() )
										{
											const ElementEntry & loggerEntry = elementListDictionary.getEntry();

											switch ( loggerEntry.getLoadType() )
											{
											case DataType::AsciiEnum:

												if ( loggerEntry.getName() == "RdmFieldDictionaryFileName" )
												{
													ommConsumerActiveConfig.dictionaryConfig.rdmfieldDictionaryFileName = loggerEntry.getAscii();
												}
												else if ( loggerEntry.getName() == "EnumTypeDefFileName" )
												{
													ommConsumerActiveConfig.dictionaryConfig.enumtypeDefFileName = loggerEntry.getAscii();
												}
												break;

											case DataType::EnumEnum:

												if ( loggerEntry.getName() == "DictionaryType" )
												{
													UInt16 dictionaryType = loggerEntry.getEnum();

													if ( dictionaryType > 1 )
													{
														EmaString text( "Invalid DictionaryType [");
														text.append( dictionaryType );
														text.append("] in Programmatic Configuration. Use default DictionaryType [");
														text.append( DEFAULT_DICTIONARY_TYPE );
														text.append( "]" );
														EmaConfigError * mce( new EmaConfigError( text, OmmLoggerClient::ErrorEnum ) );
														emaConfigErrList.add( mce );
													}
													else
													{
														ommConsumerActiveConfig.dictionaryConfig.dictionaryType = (Dictionary::DictionaryType)dictionaryType;
													}
												}
												break;
											}
										}
									}
								}
							}
						}
					}
				}
			}
		}
	}
}

namespace thomsonreuters {

namespace ema {

namespace access {

template<>
void XMLConfigElement< thomsonreuters::ema::access::EmaString >::print()
{
	printf( "%s: %s (parent %p)", _name.c_str(), _value.c_str(), _parent );
}

template<>
void XMLConfigElement< bool >::print()
{
	if ( _value == true )
		printf( "%s: true (parent %p)", _name.c_str(), _parent );
	else
		printf( "%s: false (parent %p)", _name.c_str(), _parent );
}

}

}

}
