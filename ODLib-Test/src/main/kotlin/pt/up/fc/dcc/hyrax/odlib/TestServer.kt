package pt.up.fc.dcc.hyrax.odlib

    private var odClient = ODLib()
    fun main(args: Array<String>) {
        odClient.startODService()
        odClient.startGRPCServer(50051)
        var localClient  = odClient.getClient()
        //var remoteClient = odClient.newRemoteClient("localhost", 50051)
        print(localClient)
        //print(remoteClient)
    }
