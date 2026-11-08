package com.knackered.obd.dtc

/**
 * Short SAE generic descriptions. Manufacturer-specific codes (often P1xxx)
 * stay undescribed rather than guessed. This is not a full J2012 dictionary.
 */
object GenericDtcDescriptions {
    private val descriptions: Map<String, String> = mapOf(
        "P0100" to "Mass or volume air flow circuit",
        "P0101" to "Mass or volume air flow circuit range/performance",
        "P0102" to "Mass or volume air flow circuit low",
        "P0103" to "Mass or volume air flow circuit high",
        "P0106" to "Manifold absolute pressure circuit range/performance",
        "P0107" to "Manifold absolute pressure circuit low",
        "P0108" to "Manifold absolute pressure circuit high",
        "P0110" to "Intake air temperature circuit",
        "P0112" to "Intake air temperature circuit low",
        "P0113" to "Intake air temperature circuit high",
        "P0115" to "Engine coolant temperature circuit",
        "P0116" to "Engine coolant temperature circuit range/performance",
        "P0117" to "Engine coolant temperature circuit low",
        "P0118" to "Engine coolant temperature circuit high",
        "P0120" to "Throttle/pedal position sensor A circuit",
        "P0121" to "Throttle/pedal position sensor A circuit range/performance",
        "P0122" to "Throttle/pedal position sensor A circuit low",
        "P0123" to "Throttle/pedal position sensor A circuit high",
        "P0130" to "O2 sensor circuit (bank 1 sensor 1)",
        "P0131" to "O2 sensor circuit low voltage (bank 1 sensor 1)",
        "P0132" to "O2 sensor circuit high voltage (bank 1 sensor 1)",
        "P0133" to "O2 sensor circuit slow response (bank 1 sensor 1)",
        "P0134" to "O2 sensor circuit no activity (bank 1 sensor 1)",
        "P0171" to "System too lean (bank 1)",
        "P0172" to "System too rich (bank 1)",
        "P0201" to "Injector circuit/open - cylinder 1",
        "P0202" to "Injector circuit/open - cylinder 2",
        "P0203" to "Injector circuit/open - cylinder 3",
        "P0204" to "Injector circuit/open - cylinder 4",
        "P0300" to "Random/multiple cylinder misfire detected",
        "P0301" to "Cylinder 1 misfire detected",
        "P0302" to "Cylinder 2 misfire detected",
        "P0303" to "Cylinder 3 misfire detected",
        "P0304" to "Cylinder 4 misfire detected",
        "P0325" to "Knock sensor 1 circuit",
        "P0335" to "Crankshaft position sensor A circuit",
        "P0340" to "Camshaft position sensor A circuit",
        "P0400" to "Exhaust gas recirculation flow",
        "P0401" to "Exhaust gas recirculation flow insufficient",
        "P0420" to "Catalyst system efficiency below threshold (bank 1)",
        "P0440" to "Evaporative emission system",
        "P0442" to "Evaporative emission system leak detected (small)",
        "P0455" to "Evaporative emission system leak detected (large)",
        "P0500" to "Vehicle speed sensor A",
        "P0560" to "System voltage",
        "P0562" to "System voltage low",
        "P0606" to "ECM/PCM processor",
        "P2135" to "Throttle/pedal position sensor A/B voltage correlation",
        "U0100" to "Lost communication with ECM/PCM",
        "U0101" to "Lost communication with TCM",
        "U0121" to "Lost communication with ABS control module",
    )

    fun lookup(code: String): String? = descriptions[code.uppercase()]
}
