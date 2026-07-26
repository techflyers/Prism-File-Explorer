package com.raival.compose.file.explorer.screen.terminal

import android.app.ActivityManager
import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.*
import java.io.File
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

/**
 * Writes synthetic /proc/stat and /proc/vmstat files that the Ubuntu container can read.
 * This makes tools like `htop` work correctly inside the proot sandbox.
 */
object StatUpdater {
    private var updateJob: Job? = null
    private val numCores = Runtime.getRuntime().availableProcessors()
    private val coreTicks = Array(numCores) { LongArray(8) { Random.nextLong(1000, 50000) } }
    private val globalTicks = LongArray(8)

    fun start(context: Context) {
        if (updateJob?.isActive == true) return

        updateJob = CoroutineScope(Dispatchers.IO).launch {
            val statFile = localDir(context).child("stat")
            val vmstatFile = localDir(context).child("vmstat")
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memoryInfo = ActivityManager.MemoryInfo()

            var lastSystemClock = SystemClock.elapsedRealtime()
            val initialTicks = getAppCpuTicks()
            var lastAppUserTicks = initialTicks.first
            var lastAppSystemTicks = initialTicks.second

            while (isActive) {
                try {
                    val elapsedRealtime = SystemClock.elapsedRealtime()
                    val dtMs = (elapsedRealtime - lastSystemClock).coerceAtLeast(1L)
                    lastSystemClock = elapsedRealtime

                    val currentTicks = getAppCpuTicks()
                    var userDelta = (currentTicks.first - lastAppUserTicks).coerceAtLeast(0L)
                    var systemDelta = (currentTicks.second - lastAppSystemTicks).coerceAtLeast(0L)
                    lastAppUserTicks = currentTicks.first
                    lastAppSystemTicks = currentTicks.second

                    val expectedTicksPerCore = (dtMs / 10L).coerceAtLeast(1L)
                    val systemBackgroundLoad = 0.005 + Random.nextDouble() * 0.015
                    val bgTicks = (expectedTicksPerCore * numCores * systemBackgroundLoad).toLong()
                    userDelta += (bgTicks * 0.7).toLong()
                    systemDelta += bgTicks - (bgTicks * 0.7).toLong()

                    val totalExpectedTicks = expectedTicksPerCore * numCores
                    var activeUser = userDelta
                    var activeSystem = systemDelta
                    if (activeUser + activeSystem > totalExpectedTicks) {
                        val scale = totalExpectedTicks.toDouble() / (activeUser + activeSystem)
                        activeUser = (activeUser * scale).toLong()
                        activeSystem = (activeSystem * scale).toLong()
                    }

                    var remainingUser = activeUser
                    var remainingSystem = activeSystem

                    for (core in 0 until numCores) {
                        val totalRemaining = remainingUser + remainingSystem
                        val coreActive = totalRemaining.coerceAtMost(expectedTicksPerCore)
                        val coreUser = if (totalRemaining > 0) (coreActive * remainingUser) / totalRemaining else 0L
                        val coreSystem = coreActive - coreUser
                        val coreIdle = expectedTicksPerCore - coreActive
                        coreTicks[core][0] += coreUser
                        coreTicks[core][2] += coreSystem
                        coreTicks[core][3] += coreIdle
                        remainingUser -= coreUser
                        remainingSystem -= coreSystem
                    }
                    if (remainingUser > 0) coreTicks[0][0] += remainingUser
                    if (remainingSystem > 0) coreTicks[0][2] += remainingSystem

                    for (i in 0..7) globalTicks[i] = 0
                    for (core in 0 until numCores) for (i in 0..7) globalTicks[i] += coreTicks[core][i]

                    val statBuilder = StringBuilder()
                    statBuilder.append("cpu")
                    for (i in 0..7) statBuilder.append(" ").append(globalTicks[i])
                    statBuilder.append(" 0 0\n")
                    for (core in 0 until numCores) {
                        statBuilder.append("cpu$core")
                        for (i in 0..7) statBuilder.append(" ").append(coreTicks[core][i])
                        statBuilder.append(" 0 0\n")
                    }
                    statBuilder.append("intr 127541 38 290 0 0 0 0 4 0 1 0 0\n")
                    statBuilder.append("ctxt ${System.currentTimeMillis() / 1000}\n")
                    statBuilder.append("btime ${System.currentTimeMillis() / 1000 - 3600}\n")
                    statBuilder.append("processes ${100 + Random.nextInt(50)}\n")
                    statBuilder.append("procs_running ${1 + Random.nextInt(4)}\n")
                    statBuilder.append("procs_blocked 0\n")
                    statBuilder.append("softirq 75663 0 5903 6 25375 10774 0 243 11685 0 21677\n")
                    if (!statFile.exists()) statFile.createNewFile()
                    statFile.writeText(statBuilder.toString())

                    activityManager.getMemoryInfo(memoryInfo)
                    val freePages = memoryInfo.availMem / 4096
                    val totalPages = memoryInfo.totalMem / 4096
                    val usedPages = (totalPages - freePages).coerceAtLeast(0)

                    val vmstatBuilder = StringBuilder()
                    vmstatBuilder.append("nr_free_pages $freePages\n")
                    vmstatBuilder.append("nr_zone_inactive_anon ${(usedPages * 0.25).toLong()}\n")
                    vmstatBuilder.append("nr_zone_active_anon ${(usedPages * 0.15).toLong()}\n")
                    vmstatBuilder.append("nr_zone_inactive_file ${(usedPages * 0.20).toLong()}\n")
                    vmstatBuilder.append("nr_zone_active_file ${(usedPages * 0.35).toLong()}\n")
                    vmstatBuilder.append("nr_unevictable ${(usedPages * 0.05).toLong()}\n")
                    vmstatBuilder.append("nr_zone_write_pending 0\nnr_mlock 0\nnr_bounce 0\nnr_zspages 0\nnr_free_cma 0\n")
                    vmstatBuilder.append("numa_hit 1259626\nnuma_miss 0\nnuma_foreign 0\nnuma_interleave 720\nnuma_local 1259626\nnuma_other 0\n")
                    vmstatBuilder.append("nr_inactive_anon ${(usedPages * 0.25).toLong()}\n")
                    vmstatBuilder.append("nr_active_anon ${(usedPages * 0.15).toLong()}\n")
                    vmstatBuilder.append("nr_inactive_file ${(usedPages * 0.20).toLong()}\n")
                    vmstatBuilder.append("nr_active_file ${(usedPages * 0.35).toLong()}\n")
                    vmstatBuilder.append("nr_slab_reclaimable ${8000 + Random.nextInt(500)}\nnr_slab_unreclaimable ${7000 + Random.nextInt(500)}\n")
                    vmstatBuilder.append("pgpgin 890508\npgpgout 0\npswpin 0\npswpout 0\npgfault 176973\npgmajfault 488\n")
                    if (!vmstatFile.exists()) vmstatFile.createNewFile()
                    vmstatFile.writeText(vmstatBuilder.toString())
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                delay(1000.milliseconds)
            }
        }
    }

    fun stop() {
        updateJob?.cancel()
        updateJob = null
    }

    private fun getAppCpuTicks(): Pair<Long, Long> {
        var userTicks = 0L; var systemTicks = 0L
        try {
            File("/proc").listFiles()?.forEach { file ->
                if (file.isDirectory && file.name.all { it.isDigit() }) {
                    runCatching {
                        val statFile = File(file, "stat")
                        if (statFile.exists()) {
                            val line = statFile.readText().trim()
                            val rest = line.substring(line.lastIndexOf(')') + 2)
                            val tokens = rest.split(' ')
                            if (tokens.size >= 13) {
                                userTicks += tokens[11].toLongOrNull() ?: 0L
                                systemTicks += tokens[12].toLongOrNull() ?: 0L
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return Pair(userTicks, systemTicks)
    }
}
