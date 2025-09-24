package eureto.opendoor

import android.app.Application
import eureto.opendoor.network.EwelinkApiClient // Zmień nazwę pakietu
import eureto.opendoor.network.EwelinkDevices

class EwelinkApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Inicjalizacja EwelinkApiClient w klasie Application
        EwelinkApiClient.initialize(this)
        EwelinkDevices.initialize(this, EwelinkApiClient)
    }
}