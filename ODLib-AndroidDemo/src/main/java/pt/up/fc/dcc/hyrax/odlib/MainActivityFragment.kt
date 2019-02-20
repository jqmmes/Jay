package pt.up.fc.dcc.hyrax.odlib

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.app.AppCompatActivity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import pt.up.fc.dcc.hyrax.odlib.services.scheduler.schedulers.SchedulerBase
import pt.up.fc.dcc.hyrax.odlib.utils.ImageUtils
import pt.up.fc.dcc.hyrax.odlib.utils.ODJob
import pt.up.fc.dcc.hyrax.odlib.utils.ODLogger

/**
 * A placeholder fragment containing a simple view.
 */
class MainActivityFragment : Fragment() {

    companion object {
        private const val REQUEST_IMAGE_CAPTURE = 1
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_main, container, false)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == AppCompatActivity.RESULT_OK) {
            val imageBitmap = data.extras!!.get("data") as Bitmap
            val newJob = ODJob(ImageUtils.getByteArrayFromBitmap(imageBitmap))
            //SchedulerBase.addJob(newJob)
        }
    }

    /*private fun takePhoto() {
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
            takePictureIntent.resolveActivity(activity!!.packageManager)?.also {
                startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE)
            }
        }
    }*/

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ODLogger.logInfo("Fragment onCreateView")
        //view.findViewById<Button>(R.id.takePhoto).setOnClickListener { _ -> takePhoto()}
    }
}
