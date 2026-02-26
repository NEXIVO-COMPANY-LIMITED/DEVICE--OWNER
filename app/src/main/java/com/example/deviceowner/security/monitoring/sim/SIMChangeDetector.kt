package com.microspace.payo.security.monitoring.sim

import android.content.Context
import android.telephony.TelephonyManager
import com.microspace.payo.data.local.database.DeviceOwnerDatabase
import com.microspace.payo.data.local.database.entities.sim.SimChangeHistoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SIMChangeDetector(private val context: Context) {

    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    private val simChangeHistoryDao = DeviceOwnerDatabase.getDatabase(context).simChangeHistoryDao()

    suspend fun checkForSIMChange() {
        withContext(Dispatchers.IO) {
            val currentOperator = telephonyManager.simOperatorName ?: "Unknown"
            val currentSerial = try { telephonyManager.simSerialNumber ?: "Unknown" } catch (e: SecurityException) { "Permission Denied" }
            val currentNumber = try { telephonyManager.line1Number ?: "Unknown" } catch (e: SecurityException) { "Permission Denied" }

            val lastRecord = simChangeHistoryDao.getRecent(1).firstOrNull()

            if (lastRecord != null) {
                if (lastRecord.newOperator != currentOperator || lastRecord.newSerial != currentSerial) {
                    val simChange = SimChangeHistoryEntity(
                        originalPhoneNumber = lastRecord.newPhoneNumber,
                        newPhoneNumber = currentNumber,
                        originalOperator = lastRecord.newOperator,
                        newOperator = currentOperator,
                        originalSerial = lastRecord.newSerial,
                        newSerial = currentSerial,
                        changedAt = System.currentTimeMillis()
                    )
                    simChangeHistoryDao.insert(simChange)
                }
            }
        }
    }

    suspend fun initialize() {
        withContext(Dispatchers.IO) {
            val count = simChangeHistoryDao.getChangeCount()
            if (count == 0) {
                val currentOperator = telephonyManager.simOperatorName ?: "Unknown"
                val currentSerial = try { telephonyManager.simSerialNumber ?: "Unknown" } catch (e: SecurityException) { "Unknown" }
                val currentNumber = try { telephonyManager.line1Number ?: "Unknown" } catch (e: SecurityException) { "Unknown" }

                val initialRecord = SimChangeHistoryEntity(
                    originalPhoneNumber = "INITIAL",
                    newPhoneNumber = currentNumber,
                    originalOperator = "INITIAL",
                    newOperator = currentOperator,
                    originalSerial = "INITIAL",
                    newSerial = currentSerial,
                    changedAt = System.currentTimeMillis()
                )
                simChangeHistoryDao.insert(initialRecord)
            }
        }
    }
}