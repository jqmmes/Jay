package pt.up.fc.dcc.hyrax.odlib

private var odClient = ODLib()
fun main(args: Array<String>) {
    odClient.startGRPCServerService(50000)
    val localClient  = odClient.getClient()
    val remoteClient = odClient.newRemoteClient("localhost", 50051)
    remoteClient.sayHello()
    remoteClient.ping()
    //odClient.setTFModel("/home/joaquim/Downloads/faster_rcnn_nas_coco_2018_01_28/saved_model/")
    odClient.setTFModel("/home/joaquim/Downloads/ssd/saved_model/")
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
    odClient.clean()
}

fun callbackFun (resultList : List<ODUtils.ODDetection?>) {
    for (detection in resultList) println(String.format("%d\t%f", detection!!.class_, detection.score))
    println("=============")
}