Summary
=======

The 332__Dictionary__Streaming application is provided as an example of
OMM Consumer application written to the EMA library.

This application demonstrates basic usage of the EMA library for accessing
and parsing of OMM Dictionary and MarketPrice data from Reuters Data Feed
Direct (RDF-D), directly from an OMM Provider application, or from Thomson
Reuters Advanced Distribution Server.

The 332__Dictionary__Streaming showcases usage of directory stream in Omm
Consumer. It demonstrates opening of dicitionary stream as well as its processing.


Detailed Description
====================

The 332__Dictionary__Streaming implements the following high level steps:

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
+ Opens three streaming item interests
  - Dictionary Domain stream for RWFFld
  - Dictionary Domain stream for RWFEnum
  - Market Price Domain IBM.N item from DIRECT_FEED service
+ Processes data received from API for 60 seconds
  - all received messages are processed on API thread of control
+ Exits

Note: if needed, these and other details may be modified to fit local
      environment using EmaConfig.xml file.
	  
Note: please refer to the EMA library ReadMe.txt file for details on
      standard configuration.


OmmConsumerClient and Callbacks
===============================

The 332__Dictionary__Streaming demonstrates how to receive and process individual item
response messages for Dictionary and MarketPrice domains. Additionally this application
shows a native / RWF decoding of Series and ElementList containers.