# Remarks:
To make the injection of XTrace and Baggage libs work:
* In several pom.xml:
        * The license check was disabled
        * The jar content check was disabled

* Dependencies are inherited from root pom.xml. So building modules independently
does currently not work. 


# List of system boundaries and baggage propagation:

* HMaster receives and processes request
RPCServer -> MasterProcedureUtil(emb bag) -> ProcedureExecuter(start bag)
