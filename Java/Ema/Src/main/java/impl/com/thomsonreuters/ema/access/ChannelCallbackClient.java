///*|-----------------------------------------------------------------------------
// *|            This source code is provided under the Apache 2.0 license      --
// *|  and is provided AS IS with no warranty or guarantee of fit for purpose.  --
// *|                See the project's LICENSE.md for details.                  --
// *|           Copyright Thomson Reuters 2015. All rights reserved.            --
///*|-----------------------------------------------------------------------------

package com.thomsonreuters.ema.access;

import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.util.ArrayList;
import java.util.List;

import com.thomsonreuters.ema.access.OmmConsumerImpl.OmmConsumerState;
import com.thomsonreuters.ema.access.OmmLoggerClient.Severity;
import com.thomsonreuters.upa.codec.DataDictionary;
import com.thomsonreuters.upa.valueadd.domainrep.rdm.directory.DirectoryRequest;
import com.thomsonreuters.upa.valueadd.domainrep.rdm.login.LoginRequest;
import com.thomsonreuters.upa.valueadd.reactor.ConsumerRole;
import com.thomsonreuters.upa.valueadd.reactor.ConsumerWatchlistOptions;
import com.thomsonreuters.upa.valueadd.reactor.DictionaryDownloadModes;
import com.thomsonreuters.upa.valueadd.reactor.Reactor;
import com.thomsonreuters.upa.valueadd.reactor.ReactorCallbackReturnCodes;
import com.thomsonreuters.upa.valueadd.reactor.ReactorChannel;
import com.thomsonreuters.upa.valueadd.reactor.ReactorChannelEvent;
import com.thomsonreuters.upa.valueadd.reactor.ReactorChannelEventCallback;
import com.thomsonreuters.upa.valueadd.reactor.ReactorChannelEventTypes;
import com.thomsonreuters.upa.valueadd.reactor.ReactorConnectOptions;
import com.thomsonreuters.upa.valueadd.reactor.ReactorErrorInfo;
import com.thomsonreuters.upa.valueadd.reactor.ReactorFactory;
import com.thomsonreuters.upa.valueadd.reactor.ReactorReturnCodes;
import com.thomsonreuters.upa.valueadd.reactor.ReactorRole;

class ChannelInfo
{
	private String				_name;
	private StringBuilder		_toString;
	private boolean				_toStringSet;
	private int					_nextStreamId;
	private Reactor				_rsslReactor;
	private ReactorChannel		_rsslReactorChannel;
	private List<Integer>	_reusedStreamIds;
	protected int _majorVersion;
	protected int _minorVersion;
	protected DataDictionary		_rsslDictionary;
	
	ChannelInfo(String name, Reactor rsslReactor)
	{
		_nextStreamId = 4;
		_name = name;
		_rsslReactor = rsslReactor;
		_reusedStreamIds = new ArrayList<Integer>();
	}

	ChannelInfo reset(String name, Reactor rsslReactor)
	{
		_nextStreamId = 4;
		_name = name;
		_rsslReactor = rsslReactor;
		_toStringSet = false;
		
		return this;
	}
	
	String name()
	{
		return _name;
	}

	Reactor rsslReactor()
	{
		return _rsslReactor;
	}
	
	ReactorChannel rsslReactorChannel()
	{
		return _rsslReactorChannel;
	}
	
	void rsslReactorChannel(ReactorChannel rsslReactorChannel)
	{
		_rsslReactorChannel = rsslReactorChannel;
		
		_majorVersion = rsslReactorChannel.majorVersion();
		_minorVersion = rsslReactorChannel.minorVersion();
	}
		
	DataDictionary rsslDictionary()
	{
		return _rsslDictionary;
	}
	
	ChannelInfo rsslDictionary(DataDictionary rsslDictionary)
	{
		_rsslDictionary = rsslDictionary;
		return this;
	}

	int nextStreamId(int numOfItem)
	{
		if ( numOfItem > 0 )
		{
			int retVal = ++_nextStreamId;
			_nextStreamId += numOfItem;
			return retVal;
		}

		if ( _reusedStreamIds.size() == 0 ) 
			return ++_nextStreamId;
		else
		{
			Integer steamId = _reusedStreamIds.remove(0);
			return steamId.intValue();
		}	
	}
	
	void returnStreamId(int streamId)
	{
		_reusedStreamIds.add((Integer)(streamId));
	}

	@Override
	public String toString()
	{
		if (!_toStringSet)
		{
			_toStringSet = true;
			if (_toString == null)
				_toString = new StringBuilder();
			else
				_toString.setLength(0);
			
			_toString.append("\tRsslReactorChannel name ")
					 .append(_name).append(OmmLoggerClient.CR)
					 .append("\tRsslReactor ")
					 .append(Integer.toHexString(_rsslReactor.hashCode())).append(OmmLoggerClient.CR)
					 .append("\tRsslReactorChannel ")
					 .append(Integer.toHexString(_rsslReactorChannel != null ?  _rsslReactorChannel.hashCode() : 0)).append(OmmLoggerClient.CR);
		}
		
		return _toString.toString();
	}
}

class ChannelCallbackClient implements ReactorChannelEventCallback
{
	private static final String CLIENT_NAME = "ChannelCallbackClient";
	
	private List<ChannelInfo>			_channelPool = new ArrayList<ChannelInfo>();
	private List<ChannelInfo>			_channelList = new ArrayList<ChannelInfo>();
	private OmmConsumerImpl				_consumer;
	private Reactor						_rsslReactor;
	private ReactorConnectOptions 		_rsslReactorConnOptions = ReactorFactory.createReactorConnectOptions();
	private ConsumerRole 				_rsslConsumerRole = ReactorFactory.createConsumerRole();

	
	ChannelCallbackClient(OmmConsumerImpl consumer, Reactor rsslReactor)
	{
		_consumer = consumer;
		_rsslReactor = rsslReactor;
		_rsslReactorConnOptions.connectionList().add(ReactorFactory.createReactorConnectInfo());
		
		if (_consumer.loggerClient().isTraceEnabled())
		{
			_consumer.loggerClient().trace(_consumer.formatLogMessage(CLIENT_NAME,
																		"Created ChannelCallbackClient",
																		Severity.TRACE).toString());
		}
	}

	@Override
	public int reactorChannelEventCallback(ReactorChannelEvent event)
	{
		ChannelInfo chnlInfo = (ChannelInfo)event.reactorChannel().userSpecObj();
		ReactorChannel rsslReactorChannel  = event.reactorChannel();
		
		switch(event.eventType())
		{
			case ReactorChannelEventTypes.CHANNEL_OPENED :
			{
				if (_consumer.loggerClient().isTraceEnabled())
				{
					StringBuilder temp = _consumer.consumerStrBuilder();
    	        	temp.append("Received ChannelOpened on channel ");
					temp.append(chnlInfo.name()).append(OmmLoggerClient.CR)
						.append("Consumer Name ").append(_consumer.consumerName());
					_consumer.loggerClient().trace(_consumer.formatLogMessage(ChannelCallbackClient.CLIENT_NAME, temp.toString(), Severity.TRACE));
				}
				return ReactorCallbackReturnCodes.SUCCESS;
			}
    		case ReactorChannelEventTypes.CHANNEL_UP:
    		{
    			ReactorErrorInfo rsslReactorErrorInfo = _consumer.rsslErrorInfo();
    			
    	        try
    	        {
    				event.reactorChannel().selectableChannel().register(_consumer.selector(),
    																	SelectionKey.OP_READ,
    																	event.reactorChannel());
    			}
    	        catch (ClosedChannelException e)
    	        {
    	        	if (_consumer.loggerClient().isErrorEnabled())
    	        	{
	    	        	StringBuilder temp = _consumer.consumerStrBuilder();
	    	        	temp.append("Selector failed to register channel ")
							.append(chnlInfo.name()).append(OmmLoggerClient.CR)
							.append("Consumer Name ").append(_consumer.consumerName()).append(OmmLoggerClient.CR);
	    	        		if (rsslReactorChannel != null && rsslReactorChannel.channel() != null )
								temp.append("RsslReactor ").append("@").append(Integer.toHexString(rsslReactorChannel.reactor().hashCode() )).append(OmmLoggerClient.CR)
								.append("RsslChannel ").append("@").append(Integer.toHexString(rsslReactorChannel.channel().hashCode())).append(OmmLoggerClient.CR);
							else
								temp.append("RsslReactor Channel is null").append(OmmLoggerClient.CR);
		    	        	
	    	        	_consumer.loggerClient().error(_consumer.formatLogMessage(ChannelCallbackClient.CLIENT_NAME, temp.toString(), Severity.ERROR));
    	        	}
    	        	return ReactorCallbackReturnCodes.FAILURE;
    			}
    	        
    	        chnlInfo.rsslReactorChannel(event.reactorChannel());
    	        
    	        int sendBufSize = 65535;
    	        if (rsslReactorChannel.ioctl(com.thomsonreuters.upa.transport.IoctlCodes.SYSTEM_WRITE_BUFFERS, sendBufSize, rsslReactorErrorInfo) != ReactorReturnCodes.SUCCESS)
                {
    	        	if (_consumer.loggerClient().isErrorEnabled())
    	        	{
	    	        	StringBuilder temp = _consumer.consumerStrBuilder();
	    	        	temp.append("Failed to set send buffer size on channel ")
							.append(chnlInfo.name()).append(OmmLoggerClient.CR)
							.append("Consumer Name ").append(_consumer.consumerName()).append(OmmLoggerClient.CR);
	    	        	    if (rsslReactorChannel != null && rsslReactorChannel.channel() != null )
								temp.append("RsslReactor ").append("@").append(Integer.toHexString(rsslReactorChannel.reactor().hashCode() )).append(OmmLoggerClient.CR)
								.append("RsslChannel ").append("@").append(Integer.toHexString(rsslReactorChannel.channel().hashCode())).append(OmmLoggerClient.CR);
							else
								temp.append("RsslReactor Channel is null").append(OmmLoggerClient.CR);
		    	        	
							temp.append("Error Id ").append(rsslReactorErrorInfo.error().errorId()).append(OmmLoggerClient.CR)
							.append("Internal sysError ").append(rsslReactorErrorInfo.error().sysError()).append(OmmLoggerClient.CR)
							.append("Error Location ").append(rsslReactorErrorInfo.location()).append(OmmLoggerClient.CR)
							.append("Error text ").append(rsslReactorErrorInfo.error().text());

	    	        	_consumer.loggerClient().error(_consumer.formatLogMessage(ChannelCallbackClient.CLIENT_NAME, temp.toString(), Severity.ERROR));
    	        	}
    	        	
    	        	_consumer.closeRsslChannel(rsslReactorChannel);
    	        	
                    return ReactorCallbackReturnCodes.SUCCESS;
                }

    	    	int rcvBufSize = 65535;
                if (rsslReactorChannel.ioctl(com.thomsonreuters.upa.transport.IoctlCodes.SYSTEM_READ_BUFFERS, rcvBufSize, rsslReactorErrorInfo) != ReactorReturnCodes.SUCCESS)
                {
                	if (_consumer.loggerClient().isErrorEnabled())
    	        	{
	    	        	StringBuilder temp = _consumer.consumerStrBuilder();
	    	        	temp.append("Failed to set recv buffer size on channel ").append(chnlInfo.name()).append(OmmLoggerClient.CR)
							.append("Consumer Name ").append(_consumer.consumerName()).append(OmmLoggerClient.CR);
	    	        	if (rsslReactorChannel != null && rsslReactorChannel.channel() != null)
							temp.append("RsslReactor ").append("@").append(Integer.toHexString(rsslReactorChannel.reactor().hashCode() )).append(OmmLoggerClient.CR)
							.append("RsslChannel ").append("@").append(Integer.toHexString(rsslReactorChannel.channel().hashCode())).append(OmmLoggerClient.CR);
						else
							temp.append("RsslReactor Channel is null").append(OmmLoggerClient.CR);
	    	        	
						temp.append("Error Id ").append(rsslReactorErrorInfo.error().errorId()).append(OmmLoggerClient.CR)
							.append("Internal sysError ").append(rsslReactorErrorInfo.error().sysError()).append(OmmLoggerClient.CR)
							.append("Error Location ").append(rsslReactorErrorInfo.location()).append(OmmLoggerClient.CR)
							.append("Error text ").append(rsslReactorErrorInfo.error().text());

	    	        	_consumer.loggerClient().error(_consumer.formatLogMessage(ChannelCallbackClient.CLIENT_NAME, temp.toString(), Severity.ERROR));
    	        	}
                	
                	_consumer.closeRsslChannel(rsslReactorChannel);
                	
                    return ReactorCallbackReturnCodes.SUCCESS;
                }
                
                ChannelConfig channelConfig = _consumer.activeConfig().channelConfig;
                
                if (rsslReactorChannel.ioctl(com.thomsonreuters.upa.transport.IoctlCodes.COMPRESSION_THRESHOLD, channelConfig.compressionThreshold, rsslReactorErrorInfo) != ReactorReturnCodes.SUCCESS)
                {
                	if (_consumer.loggerClient().isErrorEnabled())
    	        	{
	    	        	StringBuilder temp = _consumer.consumerStrBuilder();
						
	    	        	temp.append("Failed to set compression threshold on channel ")
							.append(chnlInfo.name()).append(OmmLoggerClient.CR)
							.append("Consumer Name ").append(_consumer.consumerName()).append(OmmLoggerClient.CR);
		    	        	if (rsslReactorChannel != null && rsslReactorChannel.channel() != null)
								temp.append("RsslReactor ").append("@").append(Integer.toHexString(rsslReactorChannel.reactor().hashCode() )).append(OmmLoggerClient.CR)
								.append("RsslChannel ").append("@").append(Integer.toHexString(rsslReactorChannel.channel().hashCode())).append(OmmLoggerClient.CR);
							else
								temp.append("RsslReactor Channel is null").append(OmmLoggerClient.CR);
		    	        	
							temp.append("Error Id ").append(rsslReactorErrorInfo.error().errorId()).append(OmmLoggerClient.CR)
							.append("Internal sysError ").append(rsslReactorErrorInfo.error().sysError()).append(OmmLoggerClient.CR)
							.append("Error Location ").append(rsslReactorErrorInfo.location()).append(OmmLoggerClient.CR)
							.append("Error text ").append(rsslReactorErrorInfo.error().text());

	    	        	_consumer.loggerClient().error(_consumer.formatLogMessage(ChannelCallbackClient.CLIENT_NAME, temp.toString(), Severity.ERROR));
    	        	}
                	
             	_consumer.closeRsslChannel(rsslReactorChannel);
                	
                    return ReactorCallbackReturnCodes.SUCCESS;
                }
                
				if (_consumer.loggerClient().isInfoEnabled())
				{
					StringBuilder temp = _consumer.consumerStrBuilder();
    	        	temp.append("Received ChannelUp event on channel ");
					temp.append(chnlInfo.name()).append(OmmLoggerClient.CR)
						.append("Consumer Name ").append(_consumer.consumerName());
					_consumer.loggerClient().info(_consumer.formatLogMessage(ChannelCallbackClient.CLIENT_NAME, temp.toString(), Severity.INFO));
				}
	
				_consumer.ommConsumerState(OmmConsumerState.RSSLCHANNEL_UP);
				
				return ReactorCallbackReturnCodes.SUCCESS;
    		}
    		case ReactorChannelEventTypes.FD_CHANGE:
    		{
    	        try
    	        {
    	            SelectionKey key = event.reactorChannel().oldSelectableChannel().keyFor(_consumer.selector());
    	            if (key != null)
                       	key.cancel();
    	        }
    	        catch (Exception e) {}
    
    	        try
    	        {
    	        	event.reactorChannel().selectableChannel().register(_consumer.selector(),
    	        													SelectionKey.OP_READ,
    	        													event.reactorChannel());
    	        }
    	        catch (Exception e)
    	        {
    	        	if (_consumer.loggerClient().isErrorEnabled())
    	        	{
	    	        	StringBuilder temp = _consumer.consumerStrBuilder();
	    	        	temp.append("Selector failed to register channel ")
							.append(chnlInfo.name()).append(OmmLoggerClient.CR);
		    	        	if (rsslReactorChannel != null && rsslReactorChannel.channel() != null)
								temp.append("RsslReactor ").append("@").append(Integer.toHexString(rsslReactorChannel.reactor().hashCode() )).append(OmmLoggerClient.CR)
								.append("RsslChannel ").append("@").append(Integer.toHexString(rsslReactorChannel.channel().hashCode())).append(OmmLoggerClient.CR);
							else
								temp.append("RsslReactor Channel is null").append(OmmLoggerClient.CR);
		    	        	
	    	        	_consumer.loggerClient().error(_consumer.formatLogMessage(ChannelCallbackClient.CLIENT_NAME, temp.toString(), Severity.ERROR));
    	        	}
    	        	return ReactorCallbackReturnCodes.FAILURE;
    	        }
    	        
    	        if (_consumer.loggerClient().isTraceEnabled())
    			{
    	        	StringBuilder temp = _consumer.consumerStrBuilder();
    	        	temp.append("Received FD Change event on channel ")
						.append(chnlInfo.name()).append(OmmLoggerClient.CR)
						.append("Consumer Name ").append(_consumer.consumerName());
    	        	_consumer.loggerClient().trace(_consumer.formatLogMessage(ChannelCallbackClient.CLIENT_NAME, temp.toString(), Severity.TRACE));
    			}

    			chnlInfo.rsslReactorChannel(event.reactorChannel());

    	        return ReactorCallbackReturnCodes.SUCCESS;
    		}
    		case ReactorChannelEventTypes.CHANNEL_READY:
    		{
    			if (_consumer.loggerClient().isTraceEnabled())
				{
					StringBuilder temp = _consumer.consumerStrBuilder();
    	        	temp.append("Received ChannelReady event on channel ");
					temp.append(chnlInfo.name()).append(OmmLoggerClient.CR)
						.append("Consumer Name ").append(_consumer.consumerName());
					_consumer.loggerClient().trace(_consumer.formatLogMessage(ChannelCallbackClient.CLIENT_NAME, temp.toString(), Severity.TRACE));
				}
    			
    			return ReactorCallbackReturnCodes.SUCCESS;
    		}
    		case ReactorChannelEventTypes.CHANNEL_DOWN_RECONNECTING:
    		{
                try
                {
                    SelectionKey key = event.reactorChannel().selectableChannel().keyFor(_consumer.selector());
                    if (key != null)
                    	key.cancel();
                }
                catch (Exception e) { }
    			
                if (_consumer.loggerClient().isWarnEnabled())
          	   	{
            		ReactorErrorInfo errorInfo = event.errorInfo();
            		 
  					StringBuilder temp = _consumer.consumerStrBuilder();
  		        	temp.append("Received Channel warning event on channel ")
  						.append(chnlInfo.name()).append(OmmLoggerClient.CR);
	    	        	if (rsslReactorChannel != null && rsslReactorChannel.channel() != null)
							temp.append("RsslReactor ").append("@").append(Integer.toHexString(rsslReactorChannel.reactor().hashCode() )).append(OmmLoggerClient.CR)
							.append("RsslChannel ").append("@").append(Integer.toHexString(rsslReactorChannel.channel().hashCode())).append(OmmLoggerClient.CR);
						else
							temp.append("RsslReactor Channel is null").append(OmmLoggerClient.CR);
	    	        	
						temp.append("Error Id ").append(event.errorInfo().error().errorId()).append(OmmLoggerClient.CR)
  						.append("Internal sysError ").append(errorInfo.error().sysError()).append(OmmLoggerClient.CR)
  						.append("Error Location ").append(errorInfo.location()).append(OmmLoggerClient.CR)
  						.append("Error text ").append(errorInfo.error().text());
  	
  		        	_consumer.loggerClient().warn(_consumer.formatLogMessage(ChannelCallbackClient.CLIENT_NAME, temp.toString(), Severity.WARNING));
          	   	}
            	
            	return ReactorCallbackReturnCodes.SUCCESS;
    		}
    		case ReactorChannelEventTypes.CHANNEL_DOWN:
            {
        	   try
               {
                   SelectionKey key = rsslReactorChannel.selectableChannel().keyFor(_consumer.selector());
                   if (key != null)
                      	key.cancel();
               }
               catch (Exception e) {}

        	   if (_consumer.loggerClient().isErrorEnabled())
        	   {
        		    ReactorErrorInfo errorInfo = event.errorInfo();
					StringBuilder temp = _consumer.consumerStrBuilder();
		        	temp.append("Received ChannelDown event on channel ")
						.append(chnlInfo.name()).append(OmmLoggerClient.CR)
						.append("Consumer Name ").append(_consumer.consumerName()).append(OmmLoggerClient.CR);
	    	        	if (rsslReactorChannel != null && rsslReactorChannel.channel() != null)
							temp.append("RsslReactor ").append("@").append(Integer.toHexString(rsslReactorChannel.reactor().hashCode() )).append(OmmLoggerClient.CR)
							.append("RsslChannel ").append("@").append(Integer.toHexString(rsslReactorChannel.channel().hashCode())).append(OmmLoggerClient.CR);
						else
							temp.append("RsslReactor Channel is null").append(OmmLoggerClient.CR);
	    	        	
						temp.append("Error Id ").append(event.errorInfo().error().errorId()).append(OmmLoggerClient.CR)
						.append("Internal sysError ").append(errorInfo.error().sysError()).append(OmmLoggerClient.CR)
						.append("Error Location ").append(errorInfo.location()).append(OmmLoggerClient.CR)
						.append("Error text ").append(errorInfo.error().text());
	
		        	_consumer.loggerClient().error(_consumer.formatLogMessage(ChannelCallbackClient.CLIENT_NAME, temp.toString(), Severity.ERROR));
        	   }

        	   _consumer.ommConsumerState(OmmConsumerState.RSSLCHANNEL_DOWN);

        	   _consumer.closeRsslChannel(event.reactorChannel());
			
        	   return ReactorCallbackReturnCodes.SUCCESS;
            }
            case ReactorChannelEventTypes.WARNING:
            {
            	if (_consumer.loggerClient().isWarnEnabled())
          	   	{
            		ReactorErrorInfo errorInfo = event.errorInfo();
            		 
  					StringBuilder temp = _consumer.consumerStrBuilder();
  		        	temp.append("Received Channel warning event on channel ")
  						.append(chnlInfo.name()).append(OmmLoggerClient.CR)
  						.append("Consumer Name ").append(_consumer.consumerName()).append(OmmLoggerClient.CR);
	    	        	if (rsslReactorChannel != null && rsslReactorChannel.channel() != null)
							temp.append("RsslReactor ").append("@").append(Integer.toHexString(rsslReactorChannel.reactor().hashCode() )).append(OmmLoggerClient.CR)
							.append("RsslChannel ").append("@").append(Integer.toHexString(rsslReactorChannel.channel().hashCode())).append(OmmLoggerClient.CR);
						else
							temp.append("RsslReactor Channel is null").append(OmmLoggerClient.CR);
	    	        	
						temp.append("Error Id ").append(event.errorInfo().error().errorId()).append(OmmLoggerClient.CR)
  						.append("Internal sysError ").append(errorInfo.error().sysError()).append(OmmLoggerClient.CR)
  						.append("Error Location ").append(errorInfo.location()).append(OmmLoggerClient.CR)
  						.append("Error text ").append(errorInfo.error().text());
  	
  		        	_consumer.loggerClient().warn(_consumer.formatLogMessage(ChannelCallbackClient.CLIENT_NAME, temp.toString(), Severity.WARNING));
          	   	}
            	return ReactorCallbackReturnCodes.SUCCESS;
            }
            default:
            {
            	if (_consumer.loggerClient().isErrorEnabled())
         	   	{
         		    ReactorErrorInfo errorInfo = event.errorInfo();
 					StringBuilder temp = _consumer.consumerStrBuilder();
 		        	temp.append("Received unknown channel event type ")
 						.append(chnlInfo.name()).append(OmmLoggerClient.CR)
 						.append("Consumer Name ").append(_consumer.consumerName()).append(OmmLoggerClient.CR);
	    	        	if (rsslReactorChannel != null && rsslReactorChannel.channel() != null )
							temp.append("RsslReactor ").append("@").append(Integer.toHexString(rsslReactorChannel.reactor().hashCode() )).append(OmmLoggerClient.CR)
							.append("RsslChannel ").append("@").append(Integer.toHexString(rsslReactorChannel.channel().hashCode())).append(OmmLoggerClient.CR);
						else
							temp.append("RsslReactor Channel is null").append(OmmLoggerClient.CR);
	    	        	
						temp.append("Error Id ").append(event.errorInfo().error().errorId()).append(OmmLoggerClient.CR)
 						.append("Internal sysError ").append(errorInfo.error().sysError()).append(OmmLoggerClient.CR)
 						.append("Error Location ").append(errorInfo.location()).append(OmmLoggerClient.CR)
 						.append("Error text ").append(errorInfo.error().text());
 	
 		        	_consumer.loggerClient().error(_consumer.formatLogMessage(ChannelCallbackClient.CLIENT_NAME, temp.toString(), Severity.ERROR));
         	   	}
            	return ReactorCallbackReturnCodes.FAILURE;
            }
		}
	}
	
	void initialize(LoginRequest loginReq, DirectoryRequest dirReq)
	{
		OmmConsumerActiveConfig activeConfig = _consumer.activeConfig();
		_rsslConsumerRole.rdmLoginRequest(loginReq);
		_rsslConsumerRole.rdmDirectoryRequest(dirReq);
		_rsslConsumerRole.dictionaryDownloadMode(DictionaryDownloadModes.NONE);
		_rsslConsumerRole.loginMsgCallback(_consumer.loginCallbackClient());
		_rsslConsumerRole.dictionaryMsgCallback(_consumer.dictionaryCallbackClient());
		_rsslConsumerRole.directoryMsgCallback(_consumer.directoryCallbackClient());
		_rsslConsumerRole.channelEventCallback(_consumer.channelCallbackClient());
		_rsslConsumerRole.defaultMsgCallback(_consumer.itemCallbackClient());
		
		ConsumerWatchlistOptions watchlistOptions = _rsslConsumerRole.watchlistOptions();
		watchlistOptions.channelOpenCallback(_consumer.channelCallbackClient());
		watchlistOptions.enableWatchlist(true);
		watchlistOptions.itemCountHint(activeConfig.itemCountHint);
		watchlistOptions.obeyOpenWindow(activeConfig.obeyOpenWindow > 0 ? true : false);
		watchlistOptions.postAckTimeout(activeConfig.postAckTimeout);
		watchlistOptions.requestTimeout(activeConfig.requestTimeout);
		watchlistOptions.maxOutstandingPosts(activeConfig.maxOutstandingPosts);

		int connectionType = activeConfig.channelConfig.rsslConnectionType;
		
		if (connectionType == com.thomsonreuters.upa.transport.ConnectionTypes.SOCKET  ||
			connectionType == com.thomsonreuters.upa.transport.ConnectionTypes.HTTP ||
			connectionType == com.thomsonreuters.upa.transport.ConnectionTypes.ENCRYPTED)
		{
			ChannelInfo channelInfo = channelInfo(activeConfig.channelConfig.name, _rsslReactor);

			com.thomsonreuters.upa.transport.ConnectOptions connectOptions = _rsslReactorConnOptions.connectionList().get(0).connectOptions();
			
			connectOptions.userSpecObject(channelInfo);

			connectOptions.majorVersion(com.thomsonreuters.upa.codec.Codec.majorVersion());
			connectOptions.minorVersion(com.thomsonreuters.upa.codec.Codec.minorVersion());
			connectOptions.protocolType(com.thomsonreuters.upa.codec.Codec.protocolType());

			_rsslReactorConnOptions.reconnectAttemptLimit(activeConfig.channelConfig.reconnectAttemptLimit);
			_rsslReactorConnOptions.reconnectMinDelay(activeConfig.channelConfig.reconnectMinDelay);
			_rsslReactorConnOptions.reconnectMaxDelay(activeConfig.channelConfig.reconnectMaxDelay);

			connectOptions.compressionType(activeConfig.channelConfig.compressionType);
			connectOptions.connectionType(connectionType);
			connectOptions.pingTimeout(activeConfig.channelConfig.connectionPingTimeout/1000);
			connectOptions.guaranteedOutputBuffers(activeConfig.channelConfig.guaranteedOutputBuffers);
			connectOptions.sysRecvBufSize(activeConfig.channelConfig.sysRecvBufSize);
			connectOptions.sysSendBufSize(activeConfig.channelConfig.sysSendBufSize);
			connectOptions.numInputBuffers(activeConfig.channelConfig.numInputBuffers);

			switch (connectOptions.connectionType())
			{
			case com.thomsonreuters.upa.transport.ConnectionTypes.SOCKET:
				{
					connectOptions.unifiedNetworkInfo().address(((SocketChannelConfig)activeConfig.channelConfig).hostName);
					try
					{
					connectOptions.unifiedNetworkInfo().serviceName(((SocketChannelConfig)activeConfig.channelConfig).serviceName);
					}
					catch(Exception e) 
					{
		        	   if (_consumer.loggerClient().isErrorEnabled())
		        	   {
		        		   StringBuilder temp = _consumer.consumerStrBuilder();
							temp.append("Failed to set service name on channel options, received exception: '")
		        				     .append(e.getLocalizedMessage())
		        				     .append( "'. ");
				        	_consumer.loggerClient().error(_consumer.formatLogMessage(ChannelCallbackClient.CLIENT_NAME, temp.toString(), Severity.ERROR));
		        	   }
					}
					connectOptions.tcpOpts().tcpNoDelay(((SocketChannelConfig)activeConfig.channelConfig).tcpNodelay);
					connectOptions.unifiedNetworkInfo().interfaceName(((SocketChannelConfig)activeConfig.channelConfig).interfaceName);
					connectOptions.unifiedNetworkInfo().unicastServiceName("");
				break;
				}
			case com.thomsonreuters.upa.transport.ConnectionTypes.ENCRYPTED:
				{
					connectOptions.unifiedNetworkInfo().address(((EncryptedChannelConfig)activeConfig.channelConfig).hostName);
					connectOptions.unifiedNetworkInfo().serviceName(((EncryptedChannelConfig)activeConfig.channelConfig).serviceName);
					connectOptions.tcpOpts().tcpNoDelay(((EncryptedChannelConfig)activeConfig.channelConfig).tcpNodelay);
					connectOptions.tunnelingInfo().objectName(((EncryptedChannelConfig)activeConfig.channelConfig).objectName);
					connectOptions.tunnelingInfo().tunnelingType("encrypted"); 
					connectOptions.unifiedNetworkInfo().interfaceName(((SocketChannelConfig)activeConfig.channelConfig).interfaceName);
					connectOptions.unifiedNetworkInfo().unicastServiceName("");
					encryptedConfiguration(connectOptions);
				break;
				}
			case com.thomsonreuters.upa.transport.ConnectionTypes.HTTP:
				{
					connectOptions.unifiedNetworkInfo().address(((HttpChannelConfig)activeConfig.channelConfig).hostName);
					connectOptions.unifiedNetworkInfo().serviceName(((HttpChannelConfig)activeConfig.channelConfig).serviceName);
					connectOptions.tcpOpts().tcpNoDelay(((HttpChannelConfig)activeConfig.channelConfig).tcpNodelay);
					connectOptions.tunnelingInfo().objectName(((HttpChannelConfig)activeConfig.channelConfig).objectName);
					connectOptions.tunnelingInfo().tunnelingType("http"); 
					connectOptions.unifiedNetworkInfo().interfaceName(((SocketChannelConfig)activeConfig.channelConfig).interfaceName);
					connectOptions.unifiedNetworkInfo().unicastServiceName("");
					httpConfiguration(connectOptions);
				break;
				}
			default :
				break;
			}

			connectOptions.unifiedNetworkInfo().interfaceName(activeConfig.channelConfig.interfaceName);
			connectOptions.unifiedNetworkInfo().unicastServiceName("");

			if (_consumer.loggerClient().isTraceEnabled())
			{
					StringBuilder temp = _consumer.consumerStrBuilder();
					temp.append("Attempt to connect using ")
						.append(com.thomsonreuters.upa.transport.ConnectionTypes.toString(connectionType))
						.append(" connection type")
						.append(OmmLoggerClient.CR)
						.append("Channel name ").append(channelInfo.name()).append(OmmLoggerClient.CR)
						.append("Consumer Name ").append(_consumer.consumerName()).append(OmmLoggerClient.CR)
						.append("RsslReactor ").append("@").append(Integer.toHexString(_rsslReactor.hashCode())).append(OmmLoggerClient.CR)
						.append("interfaceName ").append(connectOptions.unifiedNetworkInfo().interfaceName()).append(OmmLoggerClient.CR)
						.append("hostName ").append(connectOptions.unifiedNetworkInfo().address()).append(OmmLoggerClient.CR)
						.append("port ").append(connectOptions.unifiedNetworkInfo().serviceName()).append(OmmLoggerClient.CR)
						.append("reconnectAttemptLimit ").append(_rsslReactorConnOptions.reconnectAttemptLimit()).append(OmmLoggerClient.CR)
						.append("reconnectMinDelay ").append(_rsslReactorConnOptions.reconnectMinDelay()).append(" msec").append(OmmLoggerClient.CR)
						.append("reconnectMaxDelay ").append(_rsslReactorConnOptions.reconnectMaxDelay()).append(" msec").append(OmmLoggerClient.CR)
						.append("CompressionType ").append(com.thomsonreuters.upa.transport.CompressionTypes.toString(connectOptions.compressionType())).append(OmmLoggerClient.CR)
						.append("connectionPingTimeout ").append(connectOptions.pingTimeout()).append(" sec").append(OmmLoggerClient.CR)
						.append("tcpNodelay ").append((connectOptions.tcpOpts().tcpNoDelay() ? "true" : "false"));
					
						if(connectionType == com.thomsonreuters.upa.transport.ConnectionTypes.ENCRYPTED || connectionType == com.thomsonreuters.upa.transport.ConnectionTypes.HTTP)
							temp.append(OmmLoggerClient.CR).append("ObjectName ").append(connectOptions.tunnelingInfo().objectName());
					
					_consumer.loggerClient().trace(_consumer.formatLogMessage(CLIENT_NAME, temp.toString(), Severity.TRACE));
			}

			ReactorErrorInfo rsslErrorInfo = _consumer.rsslErrorInfo();
			if (ReactorReturnCodes.SUCCESS > _rsslReactor.connect(_rsslReactorConnOptions, (ReactorRole)_rsslConsumerRole, rsslErrorInfo))
			{
				com.thomsonreuters.upa.transport.Error error = rsslErrorInfo.error();
				StringBuilder temp = _consumer.consumerStrBuilder();
				temp.append("Failed to add RsslChannel to RsslReactor. Channel name ")
				    .append(channelInfo.name()).append(OmmLoggerClient.CR)
					.append("Consumer Name ").append(_consumer.consumerName()).append(OmmLoggerClient.CR)
					.append("RsslReactor ").append("@").append(Integer.toHexString(_rsslReactor.hashCode())).append(OmmLoggerClient.CR)
					.append("RsslChannel ").append(error.channel()).append(OmmLoggerClient.CR)
					.append("Error Id ").append(error.errorId()).append(OmmLoggerClient.CR)
					.append("Internal sysError ").append(error.sysError()).append(OmmLoggerClient.CR)
					.append("Error Location ").append(rsslErrorInfo.location()).append(OmmLoggerClient.CR)
					.append("Error Text ").append(error.text());

				if (_consumer.loggerClient().isErrorEnabled())
					_consumer.loggerClient().error(_consumer.formatLogMessage(CLIENT_NAME, temp.toString(), Severity.ERROR));
				
				throw _consumer.ommIUExcept().message(temp.toString());
			}

			_consumer.ommConsumerState(OmmConsumerState.RSSLCHANNEL_DOWN);

			if (_consumer.loggerClient().isTraceEnabled())
			{
				StringBuilder temp = _consumer.consumerStrBuilder();
				temp.append("Successfully created a Reactor Channel")
	            	.append(OmmLoggerClient.CR)
				    .append(" Channel name ").append(channelInfo.name()).append(OmmLoggerClient.CR)
					.append("Consumer Name ").append(_consumer.consumerName());
				_consumer.loggerClient().trace(_consumer.formatLogMessage(CLIENT_NAME, temp.toString(), Severity.TRACE));
			}

			_channelList.add(channelInfo);
		}
		else
		{
			StringBuilder temp = _consumer.consumerStrBuilder();
			temp.append("Unknown connection type. Passed in type is ")
				.append(connectionType);
			throw _consumer.ommIUExcept().message(temp.toString());
		}
	}

	private void httpConfiguration(com.thomsonreuters.upa.transport.ConnectOptions rsslOptions)
    {    	
	}    
	 
	private void encryptedConfiguration(com.thomsonreuters.upa.transport.ConnectOptions rsslOptions)
	{
	}

	private ChannelInfo channelInfo(String name, Reactor rsslReactor)
	{
		if (_channelPool.isEmpty())
			return new ChannelInfo(name, rsslReactor);
		else 
			return (_channelPool.get(0).reset(name, rsslReactor));
	}
	
	void removeChannel(ReactorChannel rsslReactorChannel)
	{
		_channelList.remove((ChannelInfo)rsslReactorChannel.userSpecObj());
		_channelPool.add((ChannelInfo)rsslReactorChannel.userSpecObj());
	}

	void closeChannels()
	{
		for (int index = _channelList.size() -1; index >= 0; index--)
			_consumer.closeRsslChannel(_channelList.get(index).rsslReactorChannel());
	}

	List<ChannelInfo>  channelList()
	{
		return _channelList;
	}
}