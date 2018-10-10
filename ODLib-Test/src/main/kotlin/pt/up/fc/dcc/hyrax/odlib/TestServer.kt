package pt.up.fc.dcc.hyrax.odlib

import pt.up.fc.dcc.hyrax.odlib.services.ODComputingService

private var odClient = ODLib()
    fun main(args: Array<String>) {
        odClient.startODService()
        //odClient.setTFModel("/home/joaquim/Downloads/faster_rcnn_inception_resnet_v2_atrous_coco_2018_01_28/saved_model/")
        odClient.listModels(false).first()
        odClient.startGRPCServerService(odClient,50051)
        Thread.sleep(20000)
        while (ODComputingService.getJobsRunningCount() > 0) {
            Thread.sleep(10)
        }
        odClient.stopODService() // ODComputingService bloqueia até concluido ou fazer um clean()
    }
