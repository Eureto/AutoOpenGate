package eureto.opendoor

import android.app.Application
import eureto.opendoor.network.EwelinkApiClient // Zmień nazwę pakietu

class EwelinkApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Inicjalizacja EwelinkApiClient w klasie Application
        EwelinkApiClient.initialize(this)
    }
}