#Installation Guide
TODO

#Architecture Overview
TODO
To understand the basic control flow in HBase


# List of system boundaries and baggage propagation:
(OUT OF DATE)

* Client sends request
AbstractRpcClient -> NettyRPCConnection -> NettyRPCDuplexHandler -> (network) -> CallRunner

* Server receives request and answers
CallRunner -> RPCServer -> (some actions) -> return to CallRunner -> ServerCall.setResponse()

* HMaster receives and processes request
CallRunner -> RPCServer -> MasterProcedureUtil -> ProcedureExecuter

# Remarks:
To make the injection of XTrace and Baggage libs work:
* In several pom.xml licence and content checks were disabled

* Dependencies are inherited from root pom.xml. So building modules independently
does currently not work. 
