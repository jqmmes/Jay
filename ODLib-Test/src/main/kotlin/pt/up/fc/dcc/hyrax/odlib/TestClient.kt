package pt.up.fc.dcc.hyrax.odlib

private var odClient = ODLib()
fun main(args: Array<String>) {
    odClient.startODService()
    val localClient  = odClient.getClient()
    //val remoteClient = odClient.newRemoteClient("localhost", 50051)
    //remoteClient.ping()
    //odClient.setTFModel("/home/joaquim/Downloads/faster_rcnn_nas_coco_2018_01_28/saved_model/")
    odClient.setTFModel("/home/joaquim/Downloads/ssd/saved_model/")
    //odClient.setTFModel("/home/joaquim/Downloads/faster_rcnn_inception_resnet_v2_atrous_coco_2018_01_28/saved_model/")

    localClient.detectObjects("/home/joaquim/000001.jpg")
    println("Working...")
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