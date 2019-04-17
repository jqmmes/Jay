package pt.up.fc.dcc.hyrax.odlib

import android.app.Activity
import android.content.Context
import android.widget.TextView
import pt.up.fc.dcc.hyrax.odlib.logger.LogLevel
import pt.up.fc.dcc.hyrax.odlib.interfaces.LogInterface
import java.io.File
import java.io.FileOutputStream
import java.util.*

class LogInterface(private val activity : Activity, private val loggingConsole : TextView) : LogInterface {
    override fun close() {
        outputFileOS.flush()
        outputFileOS.close()
    }

    private lateinit var outputFileOS: FileOutputStream

    fun benchmark(context: Context) {
        val cal = Calendar.getInstance()
        outputFileOS = FileOutputStream(File(context.getExternalFilesDir(null),
                "benchmark_${cal.get(Calendar.HOUR_OF_DAY)}_${cal.get(Calendar.MINUTE)}_${cal.get(Calendar.SECOND)}," +
                        ".txt"), false)
    }

    override fun log(message : String, logLevel: LogLevel) {
        outputFileOS.write("B:\t${System.currentTimeMillis()}\t$message\n".toByteArray())
        outputFileOS.flush()
        activity.runOnUiThread {
            val cal = Calendar.getInstance()
            loggingConsole.text = String.format(
                    "%s: %02d:%02d:%02d.%03d\t%s\n%s",
                    when (logLevel) {
                        LogLevel.Info -> "I"
                        LogLevel.Error -> "E"
                        LogLevel.Warn -> "W"
                        LogLevel.Disabled -> ""
                    },
                    cal.get(Calendar.HOUR_OF_DAY),
                    cal.get(Calendar.MINUTE),
                    cal.get(Calendar.SECOND),
                    cal.get(Calendar.MILLISECOND),
                    message,
                    loggingConsole.text.substring(0, Math.min(loggingConsole.text.length,2500))
                )
        }
    }
}