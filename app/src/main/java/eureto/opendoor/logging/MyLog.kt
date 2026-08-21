package eureto.opendoor.logging;

import android.util.Log;
import android.content.Context
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date


class MyLog {
    companion object {
        fun addLogMessageIntoFile(context: Context, message: String)
        {
            val logFileName: String = "log.txt"
            val timestamp =
                SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(Date())
            val fullMessage = "[$timestamp] $message\n"

            Log.d("LogMessage", message)

            try {
                context.openFileOutput(logFileName, Context.MODE_APPEND).use { output ->
                    output.write(fullMessage.toByteArray())
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Błąd zapisu logów: ${e.message}")
            }
        }

        fun clearLogFile(context: Context){
            try {
                context.openFileOutput("log.txt", Context.MODE_PRIVATE).use { output ->
                    output.write("".toByteArray())}

                Toast.makeText(context, "Wyczyszczono historię logów", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e("MainActivity", "Błąd podczas czyszczenia logów: ${e.message}")
            }
        }
    }
}
