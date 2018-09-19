package pt.up.fc.dcc.hyrax.odlib

private var odClient = ODLib()
fun main(args: Array<String>) {
    odClient.startODService()
    var localClient  = odClient.getClient()
    var remoteClient = odClient.newRemoteClient("localhost", 50051)
    remoteClient.ping()
    odClient.setTFModel("/home/joaquim/Downloads/ssd/saved_model/")

    localClient.detectObjects("/home/joaquim/000002.jpg")
    //print(localClient)
}