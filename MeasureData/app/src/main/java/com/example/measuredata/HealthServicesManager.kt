/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.measuredata

import android.util.Log
import androidx.concurrent.futures.await
import com.google.android.libraries.wear.whs.client.MeasureCallback
import com.google.android.libraries.wear.whs.client.WearHealthServicesClient
import com.google.android.libraries.wear.whs.data.Availability
import com.google.android.libraries.wear.whs.data.DataPoint
import com.google.android.libraries.wear.whs.data.DataType
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.sendBlocking
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject

/**
 * Entry point for [WearHealthServicesClient] APIs, wrapping them in coroutine-friendly APIs.
 */
class HealthServicesManager @Inject constructor(
    private val whsClient: WearHealthServicesClient
) {
    suspend fun hasHeartRateCapability(): Boolean {
        val capabilities = whsClient.capabilities.await()
        return (DataType.HEART_RATE_BPM in capabilities.supportedDataTypesMeasure())
    }

    /**
     * Returns a cold flow. When activated, the flow will register a callback for heart rate data
     * and start to emit messages. When the consuming coroutine is cancelled, the measure callback
     * is unregistered.
     *
     * [callbackFlow] is used to bridge between a callback-based API and Kotlin flows.
     */
    fun heartRateMeasureFlow() = callbackFlow<MeasureMessage> {
        val callback = object : MeasureCallback {
            override fun onAvailabilityChanged(type: DataType, availability: Availability) {
                sendBlocking(MeasureMessage.MeasureAvailabilty(availability))
            }

            override fun onData(dataPoints: List<DataPoint>) {
                sendBlocking(MeasureMessage.MeasureData(dataPoints))
            }
        }

        Log.d(TAG, "Registering for data")
        whsClient.measureClient.registerCallback(DataType.HEART_RATE_BPM, callback)

        awaitClose {
            Log.d(TAG, "Unregistering for data")
            whsClient.measureClient.unregisterCallback(DataType.HEART_RATE_BPM, callback)
        }
    }
}

sealed class MeasureMessage {
    class MeasureAvailabilty(val availability: Availability) : MeasureMessage()
    class MeasureData(val data: List<DataPoint>): MeasureMessage()
}