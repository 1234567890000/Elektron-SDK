///*|-----------------------------------------------------------------------------
// *|            This source code is provided under the Apache 2.0 license      --
// *|  and is provided AS IS with no warranty or guarantee of fit for purpose.  --
// *|                See the project's LICENSE.md for details.                  --
// *|           Copyright Thomson Reuters 2015. All rights reserved.            --
///*|-----------------------------------------------------------------------------

package com.thomsonreuters.ema.examples.training.series200.example220__MarketByPrice__PrivateStream;

import com.thomsonreuters.ema.access.Msg;
import com.thomsonreuters.ema.access.AckMsg;
import com.thomsonreuters.ema.access.GenericMsg;
import com.thomsonreuters.ema.access.RefreshMsg;
import com.thomsonreuters.ema.access.ReqMsg;
import com.thomsonreuters.ema.access.StatusMsg;
import com.thomsonreuters.ema.access.UpdateMsg;
import com.thomsonreuters.ema.access.Data;
import com.thomsonreuters.ema.access.DataType;
import com.thomsonreuters.ema.access.DataType.DataTypes;
import com.thomsonreuters.ema.access.EmaFactory;
import com.thomsonreuters.ema.access.FieldEntry;
import com.thomsonreuters.ema.access.FieldList;
import com.thomsonreuters.ema.access.Map;
import com.thomsonreuters.ema.access.MapEntry;
import com.thomsonreuters.ema.access.OmmConsumer;
import com.thomsonreuters.ema.access.OmmConsumerClient;
import com.thomsonreuters.ema.access.OmmConsumerConfig;
import com.thomsonreuters.ema.access.OmmConsumerConfig.OperationModel;
import com.thomsonreuters.ema.access.OmmConsumerEvent;
import com.thomsonreuters.ema.access.OmmException;


import com.thomsonreuters.ema.rdm.EmaRdm;

class AppClient implements OmmConsumerClient
{
	public void onRefreshMsg( RefreshMsg refreshMsg, OmmConsumerEvent event )
	{
		System.out.println( "Item Name: " + ( refreshMsg.hasName() ? refreshMsg.name() : "<not set>" ) );
		System.out.println( "Service Name: " + ( refreshMsg.hasServiceName() ? refreshMsg.serviceName() : "<not set>" ) );
		
		System.out.println( "Item State: " + refreshMsg.state() );
		
		if ( DataType.DataTypes.MAP == refreshMsg.payload().dataType() )
			decode( refreshMsg.payload().map() );
		
		System.out.println();
	}
	
	public void onUpdateMsg( UpdateMsg updateMsg, OmmConsumerEvent event ) 
	{
		System.out.println( "Item Name: " + ( updateMsg.hasName() ? updateMsg.name() : "<not set>" ) );
		System.out.println( "Service Name: " + ( updateMsg.hasServiceName() ? updateMsg.serviceName() : "<not set>" ) );
		
		if ( DataType.DataTypes.MAP == updateMsg.payload().dataType() )
			decode( updateMsg.payload().map() );
		
		System.out.println();
	}

	public void onStatusMsg( StatusMsg statusMsg, OmmConsumerEvent event ) 
	{
		System.out.println( "Item Name: " + ( statusMsg.hasName() ? statusMsg.name() : "<not set>" ) );
		System.out.println( "Service Name: " + ( statusMsg.hasServiceName() ? statusMsg.serviceName() : "<not set>" ) );

		if ( statusMsg.hasState() )
			System.out.println( "Item State: " +statusMsg.state() );
		
		System.out.println();
	}
	
	public void onGenericMsg( GenericMsg genericMsg, OmmConsumerEvent consumerEvent ){}
	public void onAckMsg( AckMsg ackMsg, OmmConsumerEvent consumerEvent ){}
	public void onAllMsg( Msg msg, OmmConsumerEvent consumerEvent ){}

	void decode( Map map )
	{
		if ( DataTypes.FIELD_LIST == map.summaryData().dataType() )
		{
			System.out.println( "Map Summary data:" );
			decode( map.summaryData().fieldList() );
			System.out.println();
		}

		while ( map.forth() )
		{
			MapEntry me = map.entry();

			if ( DataTypes.BUFFER == me.key().dataType() )
				System.out.println( "Action: " + me.mapActionToString() + " key value: " + me.key().buffer() );

			if ( DataTypes.FIELD_LIST == me.loadType() )
			{
				System.out.println( "Entry data:" );
				decode( me.fieldList() );
				System.out.println();
			}
		}
	}
	
	void decode( FieldList fl )
	{
		while ( fl.forth() )
		{
			FieldEntry fe = fl.entry();

			System.out.print( "Fid: " + fe.fieldId() + " Name = " + fe.name() + " DataType: " + DataType.asString( fe.load().dataType() ) + " Value: " );

			if ( Data.DataCode.BLANK == fe.code() )
				System.out.println( " blank" );
			else
				switch ( fe.loadType() )
				{
				case DataTypes.REAL :
					System.out.println( fe.real().asDouble() );
					break;
				case DataTypes.DATE :
					System.out.println( fe.date().day() + " / " + fe.date().month() + " / " + fe.date().year() );
					break;
				case DataTypes.TIME :
					System.out.println( fe.time().hour() + " / " + fe.time().minute() + " / " + fe.time().second() + fe.time().millisecond());
					break;
				case DataTypes.INT :
					System.out.println( fe.intValue());
					break;
				case DataTypes.UINT :
					System.out.println( fe.uintValue());
					break;
				case DataTypes.ASCII :
					System.out.println( fe.ascii() );
					break;
				case DataTypes.ENUM :
					System.out.println( fe.enumValue() );
					break;
				case DataTypes.ERROR :
					System.out.println( "( " + fe.error().errorCodeAsString() + " )" );
					break;
				default :
					System.out.println();
					break;
				}
		}
	}
}

public class Consumer 
{
	public static void main( String[] args )
	{
		try
		{
			AppClient appClient = new AppClient();
			
			OmmConsumerConfig config = EmaFactory.createOmmConsumerConfig();
			
			OmmConsumer consumer  = EmaFactory.createOmmConsumer( config.operationModel( OperationModel.USER_DISPATCH ).host( "localhost:14002"  ).username( "user" ) );
			
			ReqMsg reqMsg = EmaFactory.createReqMsg();
			
			consumer.registerClient( reqMsg.domainType( EmaRdm.MMT_MARKET_BY_PRICE ).serviceName( "DIRECT_FEED" ).name( "AAO.V" ).privateStream( true ), appClient );
			
			long startTime = System.currentTimeMillis();
			while ( startTime + 60000 > System.currentTimeMillis() )
				consumer.dispatch( 10 );		// calls to onRefreshMsg(), onUpdateMsg(), or onStatusMsg() execute on this thread
		}
		catch ( OmmException excp )
		{
			System.out.println( excp.getMessage() );
		}
	}
}


