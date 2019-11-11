# Remarks:
To make the injection of XTrace and Baggage libs work:
* In several pom.xml:
        * The license check was disabled
        * The jar content check was disabled

* Dependencies are inherited from root pom.xml. So building modules independently
does currently not work. 

* Aspects for XTrace and Baggage are only applied on hbase-server and hbase-procedure right now.
Applying aspects on all modules is currently not possible because of an incompatibiliy/bug of aspectj and/or maven-shade-plugin in certain cases.
Code produced by aspectj for the class AsyncBufferedMutatorImpl in hbase-client cannot be processed by maven-shade-plugin. Throws an exception while building.

# List of system boundaries and baggage propagation:

* Client sends request
AbstractRpcClient -> NettyRPCConnection -> NettyRPCDuplexHandler -> (network) -> CallRunner

* Server receives request and answers
CallRunner -> RPCServer -> (some actions) -> return to CallRunner -> ServerCall.setResponse()

* HMaster receives and processes request
CallRunner -> RPCServer -> MasterProcedureUtil -> ProcedureExecuter
