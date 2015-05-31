Summary
=======

The 371__MarketPrice__BatchViewReissue application is provided as
an example of OMM Consumer application written to the EMA library.

This application demonstrates basic usage of the EMA library while 
opening multiple items via a single request with a specific view and
parsing of OMM MarketPrice data from Reuters Data Feed Direct (RDF-D),
directly from an OMM Provider application, or from Thomson Reuters
Advanced Distribution Server.

The 371__MarketPrice__BatchViewReissue showcases usage of combined
batch and view request feature supported by Omm Consumer and Omm Provider.

Detailed Description
====================

The 371__MarketPrice__BatchViewReissue implements the following high level steps:

+ Implements OmmConsumerClient class in AppClient
  - overrides desired methods
  - provides own methods as needed, e.g. decode( const FieldList& )
    - each of the method provided in this example use the ease of use
	  data extraction methods that are data type specific
+ Instantiates AppClient object that receives and processes item messages
+ Instantiates and modifies OmmConsumerConfig object
  - sets user name to "user"
  - sets host name on the preconfigured connection to "localhost"
  - sets port on the preconfigured connection to "14002"
+ Instantiates an OmmConsumer object which initializes the connection 
  and logs into the specified server.
+ Opens a batch of streaming item interests
  - MarketPrice Domain batch request from DIRECT_FEED service
    - the batch (a list of item names) and view definition is added to
	  the request using the payload() method
+ Processes data received from API for 60 seconds
  - all received messages are processed on API thread of control
  - modifies or reissues view for each item
+ Exits

Note: if needed, these and other details may be modified to fit local
      environment using EmaConfig.xml file.
	  
Note: please refer to the EMA library ReadMe.txt file for details on
      standard configuration.


OmmConsumerClient and Callbacks
===============================

The 371__MarketPrice__BatchViewReissue demonstrates how to receive and process
individual item response messages for MarketPrice domains. Additionally this
application shows a native / RWF decoding of FieldList container.