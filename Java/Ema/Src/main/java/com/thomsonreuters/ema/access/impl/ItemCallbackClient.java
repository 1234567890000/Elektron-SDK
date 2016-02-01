///*|-----------------------------------------------------------------------------
// *|            This source code is provided under the Apache 2.0 license      --
// *|  and is provided AS IS with no warranty or guarantee of fit for purpose.  --
// *|                See the project's LICENSE.md for details.                  --
// *|           Copyright Thomson Reuters 2015. All rights reserved.            --
///*|-----------------------------------------------------------------------------

package com.thomsonreuters.ema.access.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.thomsonreuters.ema.access.GenericMsg;
import com.thomsonreuters.ema.access.OmmConsumerClient;
import com.thomsonreuters.ema.access.PostMsg;
import com.thomsonreuters.ema.access.ReqMsg;
import com.thomsonreuters.ema.access.TunnelStreamRequest;
import com.thomsonreuters.ema.access.impl.OmmLoggerClient.Severity;
import com.thomsonreuters.upa.codec.AckMsgFlags;
import com.thomsonreuters.upa.codec.Buffer;
import com.thomsonreuters.upa.codec.CloseMsg;
import com.thomsonreuters.upa.codec.Codec;
import com.thomsonreuters.upa.codec.CodecFactory;
import com.thomsonreuters.upa.codec.DataStates;
import com.thomsonreuters.upa.codec.DataTypes;
import com.thomsonreuters.upa.codec.Msg;
import com.thomsonreuters.upa.codec.MsgClasses;
import com.thomsonreuters.upa.codec.MsgKey;
import com.thomsonreuters.upa.codec.MsgKeyFlags;
import com.thomsonreuters.upa.codec.QosRates;
import com.thomsonreuters.upa.codec.QosTimeliness;
import com.thomsonreuters.upa.codec.RefreshMsgFlags;
import com.thomsonreuters.upa.codec.RequestMsg;
import com.thomsonreuters.upa.codec.RequestMsgFlags;
import com.thomsonreuters.upa.codec.StateCodes;
import com.thomsonreuters.upa.codec.StatusMsg;
import com.thomsonreuters.upa.codec.StatusMsgFlags;
import com.thomsonreuters.upa.codec.StreamStates;
import com.thomsonreuters.upa.rdm.DomainTypes;
import com.thomsonreuters.upa.rdm.InstrumentNameTypes;
import com.thomsonreuters.upa.valueadd.common.VaNode;
import com.thomsonreuters.upa.valueadd.reactor.DefaultMsgCallback;
import com.thomsonreuters.upa.valueadd.reactor.ReactorCallbackReturnCodes;
import com.thomsonreuters.upa.valueadd.reactor.ReactorChannel;
import com.thomsonreuters.upa.valueadd.reactor.ReactorErrorInfo;
import com.thomsonreuters.upa.valueadd.reactor.ReactorMsgEvent;
import com.thomsonreuters.upa.valueadd.reactor.ReactorReturnCodes;
import com.thomsonreuters.upa.valueadd.reactor.ReactorSubmitOptions;

class ConsumerCallbackClient
{
	protected RefreshMsgImpl			_refreshMsg;
	protected UpdateMsgImpl				_updateMsg;
	protected StatusMsgImpl				_statusMsg;
	protected GenericMsgImpl			_genericMsg;
	protected AckMsgImpl				_ackMsg;
	protected OmmConsumerEventImpl		_event;
	protected OmmConsumerImpl			_consumer;
	protected OmmConsumerClientImpl 	_consumerClient;

	ConsumerCallbackClient(OmmConsumerImpl consumer, String clientName)
	{
		_consumer = consumer;
		_event = new OmmConsumerEventImpl();
		_refreshMsg = new RefreshMsgImpl();
		_consumerClient = new OmmConsumerClientImpl();
		
		if (_consumer.loggerClient().isTraceEnabled())
		{
			String temp = "Created " + clientName;
			_consumer.loggerClient().trace(_consumer.formatLogMessage(clientName,
											temp, Severity.TRACE).toString());
		}
	}
	
	StatusMsg rsslStatusMsg() {return null;}
}

class ItemCallbackClient extends ConsumerCallbackClient implements DefaultMsgCallback
{
	private static final String CLIENT_NAME = "ItemCallbackClient";
	
	private HashMap<Long, Item>						_itemMap;
	private StatusMsg								_rsslStatusMsg;
	private com.thomsonreuters.upa.codec.CloseMsg	_rsslCloseMsg;

	ItemCallbackClient(OmmConsumerImpl consumer)
	{
		super(consumer, CLIENT_NAME);
		
		int initialHashSize =  (int)((_consumer.activeConfig().itemCountHint/ 0.75) + 1);
		_itemMap = new HashMap<Long, Item>(initialHashSize);
		
		_updateMsg = new UpdateMsgImpl();
	}

	void initialize() {}

	public int defaultMsgCallback(ReactorMsgEvent event)
	{
		Msg msg = event.msg();
		ChannelInfo channelInfo = (ChannelInfo)event.reactorChannel().userSpecObj();
		
        if (msg == null)
        {
        	com.thomsonreuters.upa.transport.Error error = event.errorInfo().error();
        	
        	if (_consumer.loggerClient().isErrorEnabled())
        	{
	        	StringBuilder temp = _consumer.consumerStrBuilder();
	        	temp.append("Received an item event without RsslMsg message")
	        		.append(OmmLoggerClient.CR)
	    			.append("Consumer Name ").append(_consumer.consumerName())
	    			.append(OmmLoggerClient.CR)
	    			.append("RsslReactor ").append(Integer.toHexString(channelInfo.rsslReactor().hashCode()))
	    			.append(OmmLoggerClient.CR)
	    			.append("RsslChannel ").append(Integer.toHexString(error.channel().hashCode())) 
	    			.append(OmmLoggerClient.CR)
	    			.append("Error Id ").append(error.errorId()).append(OmmLoggerClient.CR)
	    			.append("Internal sysError ").append(error.sysError()).append(OmmLoggerClient.CR)
	    			.append("Error Location ").append(event.errorInfo().location()).append(OmmLoggerClient.CR)
	    			.append("Error Text ").append(error.text());
	        	
        		_consumer.loggerClient().error(_consumer.formatLogMessage(ItemCallbackClient.CLIENT_NAME, temp.toString(), Severity.ERROR));
        	}
        	
    		return ReactorCallbackReturnCodes.SUCCESS;
        }
        
        if (msg.streamId() != 1 && (event.streamInfo() == null || event.streamInfo().userSpecObject() == null))
        {
        	if (_consumer.loggerClient().isErrorEnabled())
        	{
	        	StringBuilder temp = _consumer.consumerStrBuilder();
	        	temp.append("Received an item event without user specified pointer or stream info")
	        		.append(OmmLoggerClient.CR)
	        		.append("Consumer Name ").append(_consumer.consumerName())
	        		.append(OmmLoggerClient.CR)
	        		.append("RsslReactor ").append(Integer.toHexString(channelInfo.rsslReactor().hashCode()))
	        		.append(OmmLoggerClient.CR)
        			.append("RsslReactorChannel ").append(Integer.toHexString(event.reactorChannel().hashCode()))
        			.append(OmmLoggerClient.CR)
        			.append("RsslSelectableChannel ").append(Integer.toHexString(event.reactorChannel().selectableChannel().hashCode()));
	        	
	        	_consumer.loggerClient().error(_consumer.formatLogMessage(ItemCallbackClient.CLIENT_NAME, temp.toString(), Severity.ERROR));
        	}

        	return ReactorCallbackReturnCodes.SUCCESS;
        }

    	switch (msg.msgClass())
    	{
	    	case MsgClasses.ACK :
	    		if (msg.streamId() == 1)
	    			return _consumer.loginCallbackClient().processAckMsg(msg, event.reactorChannel(), null);
	    		else
	    			return _consumer.itemCallbackClient().processAckMsg(msg, event.reactorChannel(), event);
	    	case MsgClasses.GENERIC :
	    		return _consumer.itemCallbackClient().processGenericMsg(msg, event.reactorChannel(), event);
	    	case MsgClasses.REFRESH :
	    		return _consumer.itemCallbackClient().processRefreshMsg(msg, event.reactorChannel(), event);
	    	case MsgClasses.STATUS :
	    		return _consumer.itemCallbackClient().processStatusMsg(msg, event.reactorChannel(), event);
	    	case MsgClasses.UPDATE :
	    		return _consumer.itemCallbackClient().processUpdateMsg(msg, event.reactorChannel(), event);
	    	default :
	    		if (_consumer.loggerClient().isErrorEnabled())
	        	{
		        	StringBuilder temp = _consumer.consumerStrBuilder();
		        	temp.append("Received an item event with message containing unhandled message class")
		        		.append(OmmLoggerClient.CR)
		        		.append("Consumer Name ").append(_consumer.consumerName())
		        		.append(OmmLoggerClient.CR)
		        		.append("RsslReactor ").append(Integer.toHexString(channelInfo.rsslReactor().hashCode()))
		        		.append(OmmLoggerClient.CR)
	        			.append("RsslReactorChannel ").append(Integer.toHexString(event.reactorChannel().hashCode()))
	        			.append(OmmLoggerClient.CR)
	        			.append("RsslSelectableChannel ").append(Integer.toHexString(event.reactorChannel().selectableChannel().hashCode()));
		        	
		        	_consumer.loggerClient().error(_consumer.formatLogMessage(ItemCallbackClient.CLIENT_NAME, temp.toString(), Severity.ERROR));
	        	}
	    		break;
    	}
        
		return ReactorCallbackReturnCodes.SUCCESS;
	}

	int processRefreshMsg(Msg rsslMsg, ReactorChannel rsslReactorChannel, ReactorMsgEvent rsslEvent)
	{
		_refreshMsg.decode(rsslMsg, rsslReactorChannel.majorVersion(), rsslReactorChannel.minorVersion(), 
				((ChannelInfo)rsslReactorChannel.channel().userSpecObject()).rsslDictionary());
	
		_event._item = (Item)rsslEvent.streamInfo().userSpecObject();

		if (_event._item.type() == Item.ItemType.BATCH_ITEM)
			_event._item = ((BatchItem)_event._item).singleItem(rsslMsg.streamId());
		
		_refreshMsg.serviceName(_event._item.directory().service().info().serviceName());

		_event._item.client().onAllMsg(_refreshMsg, _event);
		_event._item.client().onRefreshMsg(_refreshMsg, _event);
		
		int rsslStreamState = ((com.thomsonreuters.upa.codec.RefreshMsg)rsslMsg).state().streamState();
		if (rsslStreamState == StreamStates.NON_STREAMING)
		{
			if (((com.thomsonreuters.upa.codec.RefreshMsg)rsslMsg).checkRefreshComplete())
				_event._item.remove();
		}
		else if (rsslStreamState != StreamStates.OPEN)
		{
			_event._item.remove();
		}

		return ReactorCallbackReturnCodes.SUCCESS;
	}

	int processUpdateMsg(Msg rsslMsg, ReactorChannel rsslReactorChannel, ReactorMsgEvent rsslEvent)
	{
		_updateMsg.decode(rsslMsg, rsslReactorChannel.majorVersion(), rsslReactorChannel.minorVersion(), 
				((ChannelInfo)rsslReactorChannel.channel().userSpecObject()).rsslDictionary());
		
		_event._item = (Item)rsslEvent.streamInfo().userSpecObject();

		if (_event._item.type() == Item.ItemType.BATCH_ITEM)
			_event._item = ((BatchItem)_event._item).singleItem(rsslMsg.streamId());
	
		_updateMsg.serviceName(_event._item.directory().service().info().serviceName());

		_event._item.client().onAllMsg(_updateMsg, _event);
		_event._item.client().onUpdateMsg(_updateMsg, _event);

		return ReactorCallbackReturnCodes.SUCCESS;
	}

	int processStatusMsg(Msg rsslMsg, ReactorChannel rsslReactorChannel, ReactorMsgEvent rsslEvent)
	{
		if (_statusMsg == null)
			_statusMsg = new StatusMsgImpl();
		
		_statusMsg.decode(rsslMsg, rsslReactorChannel.majorVersion(), rsslReactorChannel.minorVersion(), 
				((ChannelInfo)rsslReactorChannel.channel().userSpecObject()).rsslDictionary());
		
		_event._item = (Item)rsslEvent.streamInfo().userSpecObject();
		
		if (_event._item.type() == Item.ItemType.BATCH_ITEM)
			_event._item = ((BatchItem)_event._item).singleItem(rsslMsg.streamId());
		
		_statusMsg.serviceName(_event._item.directory().service().info().serviceName());

		_event._item.client().onAllMsg(_statusMsg, _event);
		_event._item.client().onStatusMsg(_statusMsg, _event);

		if (((com.thomsonreuters.upa.codec.StatusMsg)rsslMsg).checkHasState() &&  
				((com.thomsonreuters.upa.codec.StatusMsg)rsslMsg).state().streamState() != StreamStates.OPEN) 
			_event._item.remove();

		return ReactorCallbackReturnCodes.SUCCESS;
	}

	int processGenericMsg(Msg rsslMsg, ReactorChannel rsslReactorChannel, ReactorMsgEvent rsslEvent)
	{
		_genericMsg.decode(rsslMsg, rsslReactorChannel.majorVersion(), rsslReactorChannel.minorVersion(),
				((ChannelInfo)rsslReactorChannel.channel().userSpecObject()).rsslDictionary());
		
		_event._item = (Item)rsslEvent.streamInfo().userSpecObject();
		
		if (_event._item.type() == Item.ItemType.BATCH_ITEM)
			_event._item = ((BatchItem)_event._item).singleItem(rsslMsg.streamId());
		
		_event._item.client().onAllMsg(_genericMsg, _event);
		_event._item.client().onGenericMsg(_genericMsg, _event);

		return ReactorCallbackReturnCodes.SUCCESS;
	}

	int processAckMsg(Msg rsslMsg, ReactorChannel rsslReactorChannel, ReactorMsgEvent rsslEvent)
	{
		_ackMsg.decode(rsslMsg, rsslReactorChannel.majorVersion(), rsslReactorChannel.minorVersion(),
				((ChannelInfo)rsslReactorChannel.channel().userSpecObject()).rsslDictionary());

		_event._item = (Item)rsslEvent.streamInfo().userSpecObject();

		if (_event._item.type() == Item.ItemType.BATCH_ITEM)
			_event._item = ((BatchItem)_event._item).singleItem(rsslMsg.streamId());
				
		_ackMsg.serviceName(_event._item.directory().service().info().serviceName());

		_event._item.client().onAllMsg(_ackMsg, _event);
		_event._item.client().onAckMsg(_ackMsg, _event);

		return ReactorCallbackReturnCodes.SUCCESS;
	}
	
	com.thomsonreuters.upa.codec.StatusMsg rsslStatusMsg()
	{
		if (_rsslStatusMsg == null)
			_rsslStatusMsg = (StatusMsg)CodecFactory.createMsg();
		else
			_rsslStatusMsg.clear();
		
		return _rsslStatusMsg;
	}

	long registerClient(ReqMsg reqMsg, OmmConsumerClient consumerClient, Object closure , long parentHandle)
	{
		if (consumerClient == null)
			consumerClient = _consumerClient;
		
		if (parentHandle == 0)
		{
			RequestMsg requestMsg = ((ReqMsgImpl)reqMsg).rsslMsg();

			switch (requestMsg.domainType())
			{
				case DomainTypes.LOGIN :
				{
					SingleItem item = _consumer.loginCallbackClient().loginItem(reqMsg, consumerClient, closure);

					item.itemId(LongIdGenerator.nextLongId());
					addToMap(item);

					return item.itemId();
				}
				case DomainTypes.DICTIONARY :
				{
					int nameType = requestMsg.msgKey().nameType();
					if ((nameType != InstrumentNameTypes.UNSPECIFIED) && (nameType != InstrumentNameTypes.RIC))
					{
						StringBuilder temp = _consumer.consumerStrBuilder();
						
			        	temp.append("Invalid ReqMsg's name type : ")
			        		.append(nameType)
			        		.append(". OmmConsumer name='").append(_consumer .consumerName()).append("'.");
		
			        	if (_consumer.loggerClient().isErrorEnabled())
			        	{
			        		_consumer.loggerClient().error(_consumer.formatLogMessage(ItemCallbackClient.CLIENT_NAME, temp.toString(), Severity.ERROR));
			        	}

						if (_consumer.hasConsumerErrorClient())
							_consumer.consumerErrorClient().onInvalidUsage(temp.toString());
						else
							throw (_consumer.ommIUExcept().message(temp.toString()));

						return 0;
					}

					if (requestMsg.msgKey().checkHasName())
					{
						String name = requestMsg.msgKey().name().toString();

						if (!(name.equals("RWFFld")) && !(name.equals("RWFEnum")))
						{
							StringBuilder temp = _consumer.consumerStrBuilder();
							
				        	temp.append("Invalid ReqMsg's name : ")
				        		.append(name)
				        		.append("\nReqMsg's name must be \"RWFFld\" or \"RWFEnum\" for MMT_DICTIONARY domain type. ")
								.append("OmmConsumer name='").append(_consumer .consumerName()).append("'.");

				        	if (_consumer.loggerClient().isErrorEnabled())
				        	{
				        		_consumer.loggerClient().error(_consumer.formatLogMessage(ItemCallbackClient.CLIENT_NAME, temp.toString(), Severity.ERROR));
				        	}

							if (_consumer.hasConsumerErrorClient())
								_consumer.consumerErrorClient().onInvalidUsage(temp.toString());
							else
								throw (_consumer.ommIUExcept().message(temp.toString()));

							return 0;
						}
					}
					else
					{
						StringBuilder temp = _consumer.consumerStrBuilder();
						
			        	temp.append("ReqMsg's name is not defined. ")
							.append("OmmConsumer name='").append(_consumer .consumerName()).append("'.");

			        	if (_consumer.loggerClient().isErrorEnabled())
			        	{
			        		_consumer.loggerClient().error(_consumer.formatLogMessage(ItemCallbackClient.CLIENT_NAME, temp.toString(), Severity.ERROR));
			        	}

						if (_consumer.hasConsumerErrorClient())
							_consumer.consumerErrorClient().onInvalidUsage(temp.toString());
						else
							throw (_consumer.ommIUExcept().message(temp.toString()));

						return 0;
					}

					DictionaryItem item;
					if ((item = (DictionaryItem)GlobalPool._dictionaryItemPool.poll()) == null)
					{
						item = new DictionaryItem(_consumer, consumerClient, closure);
						GlobalPool._dictionaryItemPool.updatePool(item);
					}
					else
						item.reset(_consumer, consumerClient, closure, null);
					
					if (!item.open(reqMsg))
					{
						item.returnToPool();
						return 0;
					}
					else
					{
						item.itemId(LongIdGenerator.nextLongId());
						addToMap(item);
						
						return item.itemId();
					}
				}
				case DomainTypes.SOURCE :
				{
					List<ChannelInfo> channels = _consumer.channelCallbackClient().channelList();
					for(ChannelInfo eachChannel : channels)
					{
						DirectoryItem item;
						if ((item = (DirectoryItem)GlobalPool._directoryItemPool.poll()) == null)
						{
							item = new DirectoryItem(_consumer, consumerClient, closure);
							GlobalPool._directoryItemPool.updatePool(item);
						}
						else
							item.reset(_consumer, consumerClient, closure, null);
						item.channelInfo(eachChannel);
						
						if (!item.open(reqMsg))
						{
							item.returnToPool();
							return 0;
						}
						else
						{
							item.itemId(LongIdGenerator.nextLongId());
							addToMap(item);
							
							return item.itemId();
						}
					}
	
					return 0;
				}
				default :
				{
					if (requestMsg.checkHasBatch())
					{
						//TODO BatchItem
					}
					else
					{
						SingleItem item;
						if ((item = (SingleItem)GlobalPool._singleItemPool.poll()) == null)
						{
							item = new SingleItem(_consumer, consumerClient, closure, null);
							GlobalPool._singleItemPool.updatePool(item);
						}
						else
							item.reset(_consumer, consumerClient, closure, null);
						
						if (!item.open(reqMsg))
						{
							item.returnToPool();
							return 0;
						}
						else
						{
							item.itemId(LongIdGenerator.nextLongId());
							addToMap(item);
							
							return item.itemId();
						}
					}
				}
			}
		}
		else 
		{
			//TODO ParentHandle
		}
		
		return 0;
	}
	
	long registerClient(TunnelStreamRequest tunnelStreamReq, OmmConsumerClient consumerClient, Object closure)
	{
		return 0;
	}
	
	void reissue(ReqMsg reqMsg, long handle)
	{
		
	}

	void unregister(long handle)
	{
		Item item = _itemMap.get(handle);
		if (item != null)
			item.close();
	}

	void submit(PostMsg postMsg, long handle)
	{
		
	}

	void submit(GenericMsg genericMsg, long handle)
	{
		
	}

	//TODO TunnelStream
	//	int processCallback(TunnelStream , TunnelStreamStatusEvent)
	//	int processCallback(TunnelStream , TunnelStreamMsgEvent)
	//	int processCallback(TunnelStream , TunnelStreamQueueMsgEvent)

	void addToMap(Item item)
	{
		_itemMap.put(item.itemId(), item);
	}
	
	void removeFromMap(Item item)
	{
		_itemMap.remove(item.itemId());
	}
	
	com.thomsonreuters.upa.codec.CloseMsg rsslCloseMsg()
	{
		if (_rsslCloseMsg == null)
			_rsslCloseMsg = (CloseMsg)CodecFactory.createMsg();
		else
			_rsslCloseMsg.clear();
		
		return _rsslCloseMsg;
	}
}

abstract class Item extends VaNode
{
	static final class ItemType
	{
		final static int SINGLE_ITEM = 0;
		final static int BATCH_ITEM  = 1;
	}
	
	int						_domainType;
	int						_streamId;
	Object					_closure;
	Item					_parent;
	OmmConsumerClient		_consumerClient;
	OmmConsumerImpl			_consumer;
	long 					_itemId;

	Item() {}
	
	Item(OmmConsumerImpl consumer, OmmConsumerClient consumerClient, Object closure, Item parent)
	{
		_domainType = 0;
		_streamId = 0;
		_closure = closure;
		_parent = parent;
		_consumerClient = consumerClient;
		_consumer = consumer;
	}

	OmmConsumerClient client()
	{
		return _consumerClient;
	}
	
	Object closure()
	{
		return _closure;
	}
	
	Item parent()
	{
		return _parent;
	}
	
	OmmConsumerImpl consumer()
	{
		return _consumer;
	}
	
	void itemId(long itemId)
	{
		_itemId = itemId;
	}
	
	long itemId()
	{
		return _itemId;
	}
	
	void reset(OmmConsumerImpl consumer, OmmConsumerClient consumerClient, Object closure, Item parent)
	{
		_domainType = 0;
		_streamId = 0;
		_closure = closure;
		_parent = parent;
		_consumerClient = consumerClient;
		_consumer = consumer;
	}
	
	int streamId()
	{
		return _streamId;
	}
	
	
	
	abstract boolean open(ReqMsg reqMsg);
	abstract boolean modify(ReqMsg reqMsg);
	abstract boolean submit(PostMsg postMsg);
	abstract boolean submit(GenericMsg genericMsg);
	abstract boolean close();
	abstract void remove();
	abstract int type();
	abstract Directory directory();
}

class SingleItem extends Item
{
	private static final String 	CLIENT_NAME = "SingleItem";
	
	private Directory				_directory;
	protected ClosedStatusClient	_closedStatusClient;
	

	SingleItem() {}
	
	SingleItem(OmmConsumerImpl consumer, OmmConsumerClient consumerClient, Object closure , Item batchItem)
	{
		super(consumer, consumerClient, closure, batchItem);
	}
	
	@Override
	void reset(OmmConsumerImpl consumer, OmmConsumerClient consumerClient, Object closure , Item batchItem)
	{
		super.reset(consumer, consumerClient, closure, batchItem);
		
		_directory = null;
		
	}
	
	@Override
	Directory directory()
	{
		return _directory;
	}
	
	@Override
	int type()
	{
		return ItemType.SINGLE_ITEM;
	
	}

	@Override
	boolean open(ReqMsg reqMsg)
	{
		Directory directory = null;

		if (reqMsg.hasServiceName())
		{
			directory = _consumer.directoryCallbackClient().directory(reqMsg.serviceName());
			if (directory == null)
			{
				StringBuilder temp = _consumer.consumerStrBuilder();
	        	temp.append("Service name of '")
	        		.append(reqMsg.serviceName())
	        		.append("' is not found.");

	        	scheduleItemClosedStatus(_consumer.itemCallbackClient(),
											this, ((ReqMsgImpl)reqMsg).rsslMsg(), 
											temp.toString(), reqMsg.serviceName());
	        	
	        	return false;
			}
		}
		else
		{
			if (reqMsg.hasServiceId())
				directory = _consumer.directoryCallbackClient().directory(reqMsg.serviceId());
			else
			{
				scheduleItemClosedStatus(_consumer.itemCallbackClient(),
						this, ((ReqMsgImpl)reqMsg).rsslMsg(), 
						"Passed in request message does not identify any service.",
						null);
	        	
				return false;
			}

			if (directory == null)
			{
				StringBuilder temp = _consumer.consumerStrBuilder();
				
	        	temp.append("Service id of '")
	        		.append(reqMsg.serviceName())
	        		.append("' is not found.");
	        	
	        	scheduleItemClosedStatus(_consumer.itemCallbackClient(),
						this, ((ReqMsgImpl)reqMsg).rsslMsg(), 
						temp.toString(), null);
	        	
	        	return false;
			}
		}

		_directory = directory;

		return submit(((ReqMsgImpl)reqMsg).rsslMsg());
	}
	
	@Override
	boolean modify(ReqMsg reqMsg)
	{
		return true;
	}
		
	@Override
	boolean submit(PostMsg postMsg)
	{
		return true;
	}
	
	@Override
	boolean submit(GenericMsg genericMsg)
	{
		return true;
	}
	
	@Override
	boolean close()
	{
		CloseMsg rsslCloseMsg = _consumer.itemCallbackClient().rsslCloseMsg();
		rsslCloseMsg.containerType(DataTypes.NO_DATA);
		rsslCloseMsg.domainType(_domainType);

		boolean retCode = submit(rsslCloseMsg);

		remove();
		return retCode;
	}
	
	@Override
	void remove()
	{
		if (type() != ItemType.BATCH_ITEM)
		{
			if (_parent != null)
			{
				if (_parent.type() == ItemType.BATCH_ITEM)
					((BatchItem)_parent).decreaseItemCount();
			}
			
			_consumer.itemCallbackClient().removeFromMap(this);
			this.returnToPool();
		}
	}

	boolean submit(RequestMsg rsslRequestMsg)
	{
		ReactorSubmitOptions rsslSubmitOptions = _consumer.rsslSubmitOptions();
		rsslSubmitOptions.clear();
		
		int rsslFlags = rsslRequestMsg.msgKey().flags();
		rsslRequestMsg.msgKey().flags(rsslFlags & ~MsgKeyFlags.HAS_SERVICE_ID);

		if (!rsslRequestMsg.checkHasQos())
		{
			rsslRequestMsg.applyHasQos();
			rsslRequestMsg.applyHasWorstQos();
			rsslRequestMsg.qos().dynamic(false);
			rsslRequestMsg.qos().timeliness(QosTimeliness.REALTIME);
			rsslRequestMsg.qos().rate(QosRates.TICK_BY_TICK);
			rsslRequestMsg.worstQos().rate(QosRates.TIME_CONFLATED);
			rsslRequestMsg.worstQos().timeliness(QosTimeliness.DELAYED_UNKNOWN);
			rsslRequestMsg.worstQos().rateInfo(65535);
		}	
		
		if (_consumer.activeConfig().channelConfig.msgKeyInUpdates)
			rsslRequestMsg.applyMsgKeyInUpdates();
		
		if (_directory != null)
			rsslSubmitOptions.serviceName(_directory.serviceName());
		
		rsslSubmitOptions.requestMsgOptions().userSpecObj(this);
		
		if (_streamId == 0)
		{
			if (rsslRequestMsg.checkHasBatch())
			{
				//TODO batch
			}
			else
			{
				if (_streamId == 0)
					rsslRequestMsg.streamId(_directory.channelInfo().nextStreamId());
				
				_streamId = rsslRequestMsg.streamId();
			}
		}
		else
			rsslRequestMsg.streamId(_streamId);

		if (_domainType == 0)
			_domainType = rsslRequestMsg.domainType();
		else
			rsslRequestMsg.domainType(_domainType);
		
	    ReactorErrorInfo rsslErrorInfo = _consumer.rsslErrorInfo();
		rsslErrorInfo.clear();
		ReactorChannel rsslChannel = _directory.channelInfo().rsslReactorChannel();
		int ret;
		if (ReactorReturnCodes.SUCCESS > (ret = rsslChannel.submit(rsslRequestMsg, rsslSubmitOptions, rsslErrorInfo)))
	    {
			StringBuilder temp = _consumer.consumerStrBuilder();
			if (_consumer.loggerClient().isErrorEnabled())
        	{
				com.thomsonreuters.upa.transport.Error error = rsslErrorInfo.error();
				
	        	temp.append("Internal error: rsslChannel.submit() failed in SingleItem.submit(RequestMsg rsslRequestMsg)")
	        		.append("RsslChannel ").append(Integer.toHexString(error.channel().hashCode())) 
	    			.append(OmmLoggerClient.CR)
	    			.append("Error Id ").append(error.errorId()).append(OmmLoggerClient.CR)
	    			.append("Internal sysError ").append(error.sysError()).append(OmmLoggerClient.CR)
	    			.append("Error Location ").append(rsslErrorInfo.location()).append(OmmLoggerClient.CR)
	    			.append("Error Text ").append(error.text());
	        	
	        	_consumer.loggerClient().error(_consumer.formatLogMessage(SingleItem.CLIENT_NAME, temp.toString(), Severity.ERROR));
	        	
	        	temp.setLength(0);
        	}
			
			temp.append("Failed to open or modify item request. Reason: ")
				.append(ReactorReturnCodes.toString(ret))
				.append(". Error text: ")
				.append(rsslErrorInfo.error().text());
				
			if (_consumer.hasConsumerErrorClient())
				_consumer.consumerErrorClient().onInvalidUsage(temp.toString());
			else
				throw (_consumer.ommIUExcept().message(temp.toString()));

			return false;
	    }
        
		return true;
	}

	boolean submit(CloseMsg rsslCloseMsg)
	{	
		ReactorSubmitOptions rsslSubmitOptions = _consumer.rsslSubmitOptions();
		rsslSubmitOptions.clear();

		rsslSubmitOptions.requestMsgOptions().userSpecObj(this);
	
		if (_streamId == 0)
		{
			if (_consumer.loggerClient().isErrorEnabled())
	        	_consumer.loggerClient().error(_consumer.formatLogMessage(SingleItem.CLIENT_NAME,
	        									"Invalid streamId for this item in in SingleItem.submit(CloseMsg rsslCloseMsg)",
	        									Severity.ERROR));
		}
		else
			rsslCloseMsg.streamId(_streamId);
	
		ReactorErrorInfo rsslErrorInfo = _consumer.rsslErrorInfo();
		rsslErrorInfo.clear();
		ReactorChannel rsslChannel = _directory.channelInfo().rsslReactorChannel();
		int ret;
		if (ReactorReturnCodes.SUCCESS > (ret = rsslChannel.submit(rsslCloseMsg, rsslSubmitOptions, rsslErrorInfo)))
	    {
			StringBuilder temp = _consumer.consumerStrBuilder();
			
			if (_consumer.loggerClient().isErrorEnabled())
	    	{
				com.thomsonreuters.upa.transport.Error error = rsslErrorInfo.error();
				
	        	temp.append("Internal error: ReactorChannel.submit() failed in SingleItem.submit(CloseMsg rsslCloseMsg)")
	        	.append("RsslChannel ").append(Integer.toHexString(error.channel().hashCode())) 
	    			.append(OmmLoggerClient.CR)
	    			.append("Error Id ").append(error.errorId()).append(OmmLoggerClient.CR)
	    			.append("Internal sysError ").append(error.sysError()).append(OmmLoggerClient.CR)
	    			.append("Error Location ").append(rsslErrorInfo.location()).append(OmmLoggerClient.CR)
	    			.append("Error Text ").append(error.text());
	        	
	        	_consumer.loggerClient().error(_consumer.formatLogMessage(SingleItem.CLIENT_NAME, temp.toString(), Severity.ERROR));
	        	
	        	temp.setLength(0);
	    	}
			
			temp.append("Failed to close item request. Reason: ")
				.append(ReactorReturnCodes.toString(ret))
				.append(". Error text: ")
				.append(rsslErrorInfo.error().text());
				
			if (_consumer.hasConsumerErrorClient())
				_consumer.consumerErrorClient().onInvalidUsage(temp.toString());
			else
				throw (_consumer.ommIUExcept().message(temp.toString()));
	
			return false;
	    }
	
		return true;
	}

	ClosedStatusClient closedStatusClient(ConsumerCallbackClient client, Item item, Msg rsslMsg, String statusText, String serviceName)
	{
		if (_closedStatusClient == null)
			_closedStatusClient = new ClosedStatusClient(client, item, rsslMsg, statusText, serviceName);
		else
			_closedStatusClient.reset(client, item, rsslMsg, statusText, serviceName);
		
		return _closedStatusClient;
	}
	
	void scheduleItemClosedStatus(ConsumerCallbackClient client, Item item, Msg rsslMsg, String statusText, String serviceName)
	{
		if (_closedStatusClient != null) return;
    	
		_closedStatusClient = new ClosedStatusClient(client, item, rsslMsg, statusText, serviceName);
    	_consumer.addTimeoutEvent(1000, _closedStatusClient);
	}
}

//TODO
class BatchItem extends SingleItem
{
	private static final String 	CLIENT_NAME = "BatchItem";
	
	private List<SingleItem>		_singleItemList = new ArrayList<SingleItem>(10);
	private  int					_itemCount;
	
	BatchItem() {}
			
	BatchItem(OmmConsumerImpl consumer, OmmConsumerClient consumerClient, Object closure)
	{
		super(consumer, consumerClient, closure, null);
		
		_singleItemList = new ArrayList<SingleItem>(10);
		_itemCount = 1;
	}
	
	@Override
	void reset(OmmConsumerImpl consumer, OmmConsumerClient consumerClient, Object closure, Item item)
	{
		super.reset(consumer, consumerClient, closure, item);
		
		_singleItemList.clear();
		_itemCount = 1;
	}

	@Override
	boolean open(ReqMsg reqMsg)
	{
		return true;
	}
	
	@Override
	boolean modify(ReqMsg reqMsg)
	{
		return true;
	}
	
	@Override
	boolean submit(PostMsg postMsg)
	{
		return true;
	}
	
	@Override
	boolean submit(GenericMsg genericMsg)
	{
		return true;
	}
	
	@Override
	boolean close()
	{
		StringBuilder temp = _consumer.consumerStrBuilder();
		temp.append("Invalid attempt to close batch stream. ")
    		.append("OmmConsumer name='").append(_consumer .consumerName()).append("'.");
		
		if (_consumer.loggerClient().isErrorEnabled())
        	_consumer.loggerClient().error(_consumer.formatLogMessage(BatchItem.CLIENT_NAME, temp.toString(), Severity.ERROR));
			
		if (_consumer.hasConsumerErrorClient())
			_consumer.consumerErrorClient().onInvalidUsage(temp.toString());
		else
			throw (_consumer.ommIUExcept().message(temp.toString()));

		return false;
	}

	@Override
	int type()
	{
		return ItemType.BATCH_ITEM;
	
	}
	
	SingleItem createSingleItem()
	{
		SingleItem item;
		if ((item = (SingleItem)GlobalPool._singleItemPool.poll()) == null)
		{
			item = new SingleItem(_consumer, _consumerClient, 0, this);
			GlobalPool._singleItemPool.updatePool(item);
		}
		else
			item.reset(_consumer, _consumerClient, 0, this);
		
		return item;
	}
	
	boolean addBatchItems(List<String> itemList)
	{
		int size = itemList.size();
		while (size-- > 0)
			_singleItemList.add(createSingleItem());
		
		_itemCount = _singleItemList.size(); 

		return true;
	}
	
	List<SingleItem> singleItemList()
	{
		return _singleItemList;
	}

	SingleItem singleItem(int index)
	{
		return null;
	}

	void decreaseItemCount()
	{
		
	}
	
	Item item(int index)
	{
		return null;
	}
}

//TODO TunnelItem
//TODO SubItem

class ClosedStatusClient implements TimeoutClient
{
	private MsgKey 		_rsslMsgKey = CodecFactory.createMsgKey();
	private Buffer 		_statusText =  CodecFactory.createBuffer();
	private Buffer 		_serviceName = CodecFactory.createBuffer();
	private int 		_domainType;
	private int 		_streamId;
	private Item 		_item;
	private boolean 	_isPrivateStream; 
	private ConsumerCallbackClient _client;
	
	ClosedStatusClient(ConsumerCallbackClient client, Item item, Msg rsslMsg, String statusText, String serviceName)
	{
		reset(client, item, rsslMsg, statusText, serviceName);
	}
	
	void reset(ConsumerCallbackClient client, Item item, Msg rsslMsg, String statusText, String serviceName)
	{
		_client = client;
		_item = item;
		_statusText.data(statusText);
		_domainType = rsslMsg.domainType();
		_rsslMsgKey.clear();
		_serviceName.data(serviceName);
		
		if (rsslMsg.msgKey() != null)
			rsslMsg.msgKey().copy(_rsslMsgKey);
		
		switch (rsslMsg.msgClass())
	    {
	     	case MsgClasses.REFRESH :
	           	_isPrivateStream = (rsslMsg.flags() & RefreshMsgFlags.PRIVATE_STREAM) > 0 ? true : false;
	           	break;
	        case MsgClasses.STATUS :
	        	_isPrivateStream = (rsslMsg.flags() & StatusMsgFlags.PRIVATE_STREAM) > 0 ? true : false;
	        	break;
	        case MsgClasses.REQUEST :
	           	_isPrivateStream = (rsslMsg.flags() & RequestMsgFlags.PRIVATE_STREAM) > 0 ? true : false;
	        	break;
	        case MsgClasses.ACK :
	           	_isPrivateStream = (rsslMsg.flags() & AckMsgFlags.PRIVATE_STREAM) > 0 ? true : false;
	        	break;
	        default :
	           	_isPrivateStream = false;
	        	break;
	    }
	}
	
	@Override
	public void handleTimeoutEvent()
	{
		StatusMsg rsslStatusMsg = _client.rsslStatusMsg();

		rsslStatusMsg.msgClass(MsgClasses.STATUS);
		rsslStatusMsg.streamId(_streamId);
		rsslStatusMsg.domainType(_domainType);
		rsslStatusMsg.containerType(DataTypes.NO_DATA);
	
		rsslStatusMsg.applyHasState();
		rsslStatusMsg.state().streamState(StreamStates.CLOSED);
		rsslStatusMsg.state().dataState(DataStates.SUSPECT);
		rsslStatusMsg.state().code(StateCodes.NONE);
		rsslStatusMsg.state().text(_statusText);
		    
		rsslStatusMsg.applyHasMsgKey();
		_rsslMsgKey.copy(rsslStatusMsg.msgKey()); 
		
		if (_isPrivateStream)
			rsslStatusMsg.applyPrivateStream();

		if (_client._statusMsg == null)
			_client._statusMsg = new StatusMsgImpl();
		
		_client._statusMsg.decode(rsslStatusMsg, Codec.majorVersion(), Codec.majorVersion(), null);

		_client._statusMsg.serviceName(_serviceName);

		_client._event._item = _item;

		_client._event._item.client().onAllMsg(_client._statusMsg, _client._event);
		_client._event._item.client().onStatusMsg(_client._statusMsg, _client._event);

		_client._event._item.remove();
	}
}