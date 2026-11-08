package com.knackered.obd

import android.app.Application
import com.knackered.obd.session.ObdClient
import com.knackered.obd.transport.FakeObdTransport
import com.knackered.obd.transport.InMemoryObdLog
import com.knackered.obd.transport.ObdTransport
import com.knackered.obd.vehicle.FileVehicleProfileStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/**
 * Temporary host until the Connect screen owns the transport.
 * The terminal reviews against [FakeObdTransport]; it is not a Bluetooth connection.
 */
class KnackeredApp : Application() {
    val standIn: StandInSession by lazy { StandInSession().also { it.start() } }
    val profiles: FileVehicleProfileStore by lazy {
        FileVehicleProfileStore(File(filesDir, "vehicle-profiles.txt"))
    }
}

class StandInSession {
    val log = InMemoryObdLog()
    val transport: ObdTransport = FakeObdTransport()
    val client = ObdClient(transport, log)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ready = CompletableDeferred<Unit>()

    fun start() {
        scope.launch {
            transport.connect()
            ready.complete(Unit)
        }
    }

    suspend fun awaitConnected() {
        ready.await()
    }
}
