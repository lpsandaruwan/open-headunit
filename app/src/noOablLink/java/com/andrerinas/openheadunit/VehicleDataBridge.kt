package com.andrerinas.openheadunit

import android.content.Context

/** Ordinary builds have no vehicle-data service or Dongfeng permission declarations. */
object VehicleDataBridge {
    fun isTelemetryProcess(context: Context): Boolean = false
    fun start(context: Context) = Unit
}
