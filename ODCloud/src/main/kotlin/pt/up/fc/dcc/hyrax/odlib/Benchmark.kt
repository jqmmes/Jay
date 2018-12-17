package pt.up.fc.dcc.hyrax.odlib

import pt.up.fc.dcc.hyrax.odlib.clients.ClientManager
import pt.up.fc.dcc.hyrax.odlib.scheduler.LocalScheduler
import pt.up.fc.dcc.hyrax.odlib.scheduler.Scheduler
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import javax.imageio.ImageIO

object Benchmark {

    fun run(dataSize: String = "small", mModel: String = "all") {
        val client = ClientManager.getLocalODClient()
        Scheduler.startService(LocalScheduler())
        var countDownLatch = CountDownLatch(1)
        for (model in client.getModels(false, true).reversed()) {
            if (model.modelName == mModel || mModel == "all") {
                println("Model:\t${model.modelName}")
                client.selectModel(model)
                Thread.sleep(1000)
                while (!client.modelLoaded(model)) Thread.sleep(1000)
                for (f in File("./benchmark/$dataSize").listFiles()) {
                    if (f.name.startsWith(".")) continue
                    val img = ImageIO.read(f)
                    if (img.type != BufferedImage.TYPE_3BYTE_BGR) {
                        throw IOException(
                                String.format(
                                        "Expected 3-byte BGR encoding in BufferedImage, found %d (file: %s). This code could be made more robust",
                                        img.type, f.name))
                    }
                    val output = ByteArrayOutputStream()
                    ImageIO.write(img, "jpg", output)
                    val start = System.currentTimeMillis()
                    val job = Scheduler.createJob(output.toByteArray())
                    client.asyncDetectObjects(job) {
                        println("${job.getId()}\t${f.name}\t${System.currentTimeMillis() - start}ms")
                        countDownLatch.countDown()
                    }
                    countDownLatch.await()
                    countDownLatch = CountDownLatch(1)
                }
                println("---------------------------------------")
            }
        }
    }
}