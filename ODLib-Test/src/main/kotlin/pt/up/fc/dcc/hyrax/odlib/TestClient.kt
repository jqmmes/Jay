package pt.up.fc.dcc.hyrax.odlib

import pt.up.fc.dcc.hyrax.odlib.discover.MulticastAdvertiser
import pt.up.fc.dcc.hyrax.odlib.discover.MulticastListener
import pt.up.fc.dcc.hyrax.odlib.interfaces.DiscoverInterface
import java.lang.Thread.sleep
class xpto : DiscoverInterface {
    override fun onNewClientFound(remoteClient: RemoteODClient) {
        println("xpto")
    }

}

private var odClient = ODLib()
fun main(args: Array<String>) {
    /*odClient.startGRPCServerService(odClient, 50000)
    val localClient  = odClient.getClient()
    //val remoteClient = odClient.newRemoteClient("192.168.1.27", 50001)
    val remoteClient = odClient.newRemoteClient("192.168.1.40", 50001)
    remoteClient.sayHello()
    remoteClient.ping()*/
    MulticastAdvertiser.advertise()
    //MulticastListener.listen(xpto())
    //odClient.listModels(false).first()
    //odClient.setTFModel("/home/joaquim/Downloads/faster_rcnn_nas_coco_2018_01_28/saved_model/")
    sleep(10000)
    return
    /*odClient.setTFModel(odClient.listModels(false).last())
    //odClient.setTFModel("/home/joaquim/Downloads/ssd/saved_model/")
    //odClient.setTFModel("/home/joaquim/Downloads/faster_rcnn_inception_resnet_v2_atrous_coco_2018_01_28/saved_model/")

    remoteClient.asyncDetectObjects("/home/joaquim/000001.jpg", ::callbackFun)
    localClient.asyncDetectObjects("/home/joaquim/000001.jpg", ::callbackFun)
    remoteClient.asyncDetectObjects("/home/joaquim/000001.jpg", ::callbackFun)
    remoteClient.asyncDetectObjects("/home/joaquim/000001.jpg", ::callbackFun)
    remoteClient.asyncDetectObjects("/home/joaquim/000001.jpg", ::callbackFun)
    remoteClient.asyncDetectObjects("/home/joaquim/000001.jpg", ::callbackFun)
    remoteClient.asyncDetectObjects("/home/joaquim/000001.jpg", ::callbackFun)
    remoteClient.asyncDetectObjects("/home/joaquim/000001.jpg", ::callbackFun)
    remoteClient.asyncDetectObjects("/home/joaquim/000001.jpg", ::callbackFun)
    remoteClient.asyncDetectObjects("/home/joaquim/000001.jpg", ::callbackFun)
    remoteClient.asyncDetectObjects("/home/joaquim/000001.jpg", ::callbackFun)
    remoteClient.asyncDetectObjects("/home/joaquim/000001.jpg", ::callbackFun)
    for (detection in remoteClient.detectObjects("/home/joaquim/000001.jpg")) println(String.format("%d\t%f", detection!!.class_, detection.score))
    localClient.asyncDetectObjects("/home/joaquim/000001.jpg", ::callbackFun)
    for (detection in localClient.detectObjects("/home/joaquim/000001.jpg")) println(String.format("%d\t%f", detection!!.class_, detection.score))

    localClient.detectObjects("/home/joaquim/000001.jpg")
    localClient.asyncDetectObjects("/home/joaquim/000001.jpg", ::callbackFun)
    localClient.asyncDetectObjects("/home/joaquim/000002.jpg", ::callbackFun)
    localClient.asyncDetectObjects("/home/joaquim/000003.jpg", ::callbackFun)
    localClient.asyncDetectObjects("/home/joaquim/000004.jpg", ::callbackFun)
    localClient.asyncDetectObjects("/home/joaquim/000006.jpg", ::callbackFun)
    localClient.asyncDetectObjects("/home/joaquim/000008.jpg", ::callbackFun)
    while (ODService.getJobsRunningCount() > 0) {
        Thread.sleep(10)
    }
    odClient.clean()*/
}

fun callbackFun (resultList : List<ODUtils.ODDetection?>) {
    for (detection in resultList) println(String.format("%d\t%f", detection!!.class_, detection.score))
    println("=============")
}