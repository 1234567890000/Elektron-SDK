function  WWHBookData_AddTOCEntries(P)
{
var A=P.fN("Chapter 1 Guide Introduction","");
var B=A.fN("1.1 About this Manual","0");
B=A.fN("1.2 Audience","1");
B=A.fN("1.3 Programming Languages","2");
B=A.fN("1.4 Acronyms and Abbreviations","3");
B=A.fN("1.5 References","4");
B=A.fN("1.6 Document Conventions","5");
A=P.fN("Chapter 2 Product Overview","");
B=A.fN("2.1 EMA Product Description","6");
B=A.fN("2.2 Product Documentation and Learning EMA","7");
B=A.fN("2.3 Supported Features","8");
B=A.fN("2.4 Product Architecture","9");
A=P.fN("Chapter 3 OMM Containers and Messages","");
B=A.fN("3.1 Overview","10");
B=A.fN("3.2 Classes","11");
var C=B.fN("3.2.1 DataType Class","11#1019110");
C=B.fN("3.2.2 DataCode Class","12");
C=B.fN("3.2.3 Data Class","13");
C=B.fN("3.2.4 Msg Class","14");
C=B.fN("3.2.5 OmmError Class","15");
B=A.fN("3.3 Working with OMM Containers","16");
C=B.fN("3.3.1 Example: Populating a FieldList Class","17");
C=B.fN("3.3.2 Example: Extracting Information from a FieldList Class","18");
C=B.fN("3.3.3 Example: Extracting FieldList information using a Downcast operation","19");
B=A.fN("3.4 Working with OMM Messages","20");
C=B.fN("3.4.1 Example: Populating the GenericMsg with an ElementList Payload","21");
C=B.fN("3.4.2 Example: Extracting Information from the GenericMsg class","22");
A=P.fN("Chapter 4 Consumer Classes","");
B=A.fN("4.1 OmmConsumer Class","23");
C=B.fN("4.1.1 Connecting to a Server and Opening Items","24");
C=B.fN("4.1.2 Opening Items Immediately After OmmConsumer Object Instantiation","25");
C=B.fN("4.1.3 Destroying the OmmConsumer Object","26");
C=B.fN("4.1.4 Example: Working with the OmmConsumer Class","27");
C=B.fN("4.1.5 Working with Items","28");
C=B.fN("4.1.6 Example: Working with Items","29");
B=A.fN("4.2 OmmConsumerClient Class","30");
C=B.fN("4.2.1 OmmConsumerClient Description","30#1020898");
C=B.fN("4.2.2 Example: OmmConsumerClient","31");
B=A.fN("4.3 OmmConsumerConfig Class","32");
A=P.fN("Chapter 5 Troubleshooting and Debugging","");
B=A.fN("5.1 EMA Logger Usage","33");
B=A.fN("5.2 OmmConsumerErrorClient Class","34");
C=B.fN("5.2.1 OmmConsumerErrorClient Description","34#1019163");
C=B.fN("5.2.2 Example: OmmConsumerErrorClient","35");
B=A.fN("5.3 OmmException Class","36");
}
