package pt.up.fc.dcc.hyrax.odlib

import android.app.Activity
import android.widget.TextView
import pt.up.fc.dcc.hyrax.odlib.enums.LogLevel
import pt.up.fc.dcc.hyrax.odlib.interfaces.ODLog
import java.util.Calendar

class Logger(private val activity : Activity, private val loggingConsole : TextView) : ODLog {
    override fun log(message : String, logLevel: LogLevel) {
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
                    loggingConsole.text
                )
        }
    }
}