/*
 * Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.util

import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.isCommon
import org.jetbrains.kotlin.platform.isJs
import org.jetbrains.kotlin.stats.MarkdownReportRenderer
import org.jetbrains.kotlin.stats.SingleReportsData
import org.jetbrains.kotlin.stats.StatsCalculator
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

 
abstract class PerformanceManager(val targetPlatform: TargetPlatform, val presentableName: String) {

    companion object {
        private const val DEBUG_MODE: Boolean = false
    }

    private var currentPhaseType: PhaseType = PhaseType.Initialization
    private var phaseStartTime: Time? = currentTime()
    private val phaseMeasurements: SortedMap<PhaseType, Time> = sortedMapOf()
    private val phaseSideMeasurements: SortedMap<PhaseSideType, SideStats> = sortedMapOf()
    private val gcMeasurements: SortedMap<String, GarbageCollectionStats> = sortedMapOf()
    private var jitTimeMillis: Long? = null
    private val extendedStats: MutableList<String> = mutableListOf()

    private var currentDynamicPhaseTime: Time? = null
    private var currentDynamicPhase: String? = null
    private val dynamicPhaseMeasurements = LinkedHashMap<Pair<PhaseType, String>, Time>()

    var isExtendedStatsEnabled: Boolean = false
    var compilerType: CompilerType = CompilerType.K2
    var hasErrors: Boolean = false
    var targetDescription: String? = null
    var outputKind: String? = null
    var files: Int = 0
    var lines: Int = 0
    var isFinalized: Boolean = false

    val isPhaseMeasuring: Boolean
        get() = phaseStartTime != null

    var detailedPerf: Boolean = false

    fun getTargetInfo(): String =
        listOfNotNull(targetDescription, outputKind).joinToString("-") + " $files files ($lines lines)"

    fun initializeCurrentThread() {
         
    }

    private fun currentTime(): Time = Time(
        System.nanoTime(),
        userTime = 0L,  
        cpuTime = 0L 
    )

    val unitStats: UnitStats by lazy {
        if (!isFinalized) notifyCompilationFinished()

        var initTime: Time? = null
        var analysisTime: Time? = null
        var translationToIrTime: Time? = null
        var irPreLoweringTime: Time? = null
        var irSerializationTime: Time? = null
        var klibWritingTime: Time? = null
        var irLoweringTime: Time? = null
        var backendTime: Time? = null

        for ((phaseType, time) in phaseMeasurements) {
            when (phaseType) {
                PhaseType.Initialization -> initTime = time
                PhaseType.Analysis -> analysisTime = time
                PhaseType.TranslationToIr -> translationToIrTime = time
                PhaseType.IrPreLowering -> irPreLoweringTime = time
                PhaseType.IrSerialization -> irSerializationTime = time
                PhaseType.KlibWriting -> klibWritingTime = time
                PhaseType.IrLowering -> irLoweringTime = time
                PhaseType.Backend -> backendTime = time
            }
        }

        var findJavaClassStats: SideStats? = null
        var findKotlinClassStats: SideStats? = null

        for ((phaseSideType, sideStats) in phaseSideMeasurements) {
            when (phaseSideType) {
                PhaseSideType.FindJavaClass -> findJavaClassStats = sideStats
                PhaseSideType.BinaryClassFromKotlinFile -> findKotlinClassStats = sideStats
            }
        }

        UnitStats(
            targetDescription,
            outputKind,
            System.currentTimeMillis(),
            targetPlatform.getPlatformEnumValue(),
            compilerType,
            hasErrors,
            files,
            lines,
            initTime,
            analysisTime,
            translationToIrTime,
            irPreLoweringTime,
            irSerializationTime,
            klibWritingTime,
            irLoweringTime,
            backendTime,
            dynamicPhaseMeasurements.map { (key, time) ->
                val (phaseType, name) = key
                DynamicStats(phaseType, name, time)
            },
            findJavaClassStats,
            findKotlinClassStats,
            gcMeasurements.values.toList(),
            jitTimeMillis,
            extendedStats,
        )
    }

    fun addOtherUnitStats(otherUnitStats: UnitStats?) {
        ensureNotFinalized()
        if (otherUnitStats == null) return

        assertIfDebug(targetPlatform.getPlatformEnumValue() == otherUnitStats.platform)
        compilerType += otherUnitStats.compilerType
        hasErrors = hasErrors || otherUnitStats.hasErrors
        addSourcesStats(otherUnitStats.filesCount, otherUnitStats.linesCount)

        otherUnitStats.forEachPhaseMeasurement { phaseType, time ->
            if (time != null) {
                phaseMeasurements[phaseType] = (phaseMeasurements[phaseType] ?: Time.ZERO) + time
            }
        }

        otherUnitStats.dynamicStats?.forEach { (phaseType, name, time) ->
            dynamicPhaseMeasurements[phaseType to name] = (dynamicPhaseMeasurements[phaseType to name] ?: Time.ZERO) + time
        }

        otherUnitStats.forEachPhaseSideMeasurement { phaseSideType, sideStats ->
            if (sideStats != null) {
                phaseSideMeasurements[phaseSideType] = (phaseSideMeasurements[phaseSideType] ?: SideStats.EMPTY) + sideStats
            }
        }

        for (otherGcStats in otherUnitStats.gcStats) {
            val existing = gcMeasurements[otherGcStats.kind]
            gcMeasurements[otherGcStats.kind] = GarbageCollectionStats(
                otherGcStats.kind,
                (existing?.millis ?: 0) + otherGcStats.millis,
                (existing?.count ?: 0) + otherGcStats.count,
            )
        }

        if (jitTimeMillis != null || otherUnitStats.jitTimeMillis != null) {
            jitTimeMillis = (jitTimeMillis ?: 0) + (otherUnitStats.jitTimeMillis ?: 0)
        }
    }

    private fun TargetPlatform.getPlatformEnumValue(): PlatformType {
        val firstPlatformName = componentPlatforms.first().platformName
        return when {
            firstPlatformName.contains("JVM") -> PlatformType.JVM
            firstPlatformName.contains("Native") -> PlatformType.Native
            targetPlatform.isJs() -> PlatformType.JS
            targetPlatform.isCommon() -> PlatformType.Common
            else -> error("Unexpected platform $targetPlatform")
        }
    }

    fun enableExtendedStats() {
        isExtendedStatsEnabled = true
        // لا يوجد JIT أو GC stats
    }

    open fun addSourcesStats(files: Int, lines: Int) {
        ensureNotFinalized()
        this.files += files
        this.lines += lines
    }

    fun notifyDynamicPhaseStarted(name: String) {
        currentDynamicPhaseTime = currentTime()
        currentDynamicPhase = name
    }

    fun notifyDynamicPhaseFinished(name: String, parentPhaseType: PhaseType) {
        assertIfDebug(currentDynamicPhaseTime != null)
        assertIfDebug(currentDynamicPhase == name)

        val local = currentDynamicPhaseTime
        assertIfDebug(local != null) { "Dynamic measurement $name must have been started before finishing" }
        if (local != null) {
            dynamicPhaseMeasurements[parentPhaseType to name] =
                (dynamicPhaseMeasurements[parentPhaseType to name] ?: Time.ZERO) + (currentTime() - local)
        }
        currentDynamicPhaseTime = null
    }

    fun notifyPhaseStarted(newPhaseType: PhaseType) {
        assertIfDebug(phaseStartTime == null) { "The measurement for phase $currentPhaseType must have been finished before starting $newPhaseType" }

        if (!targetPlatform.isJs()) {
            assertIfDebug(newPhaseType >= currentPhaseType) { "The measurement for phase $newPhaseType must be performed before $currentPhaseType" }
        }

        phaseStartTime = currentTime()
        currentPhaseType = newPhaseType
    }

    fun notifyPhaseFinished(phaseType: PhaseType) {
        ensureNotFinalized()
        assertIfDebug(phaseStartTime != null) { "The measurement for phase $phaseType hasn't been started or already finished" }
        finishPhase(phaseType)
    }

    open fun notifyCompilationFinished() {
        ensureNotFinalized()
        isFinalized = true

        if (currentPhaseType != PhaseType.Backend || phaseStartTime != null) {
            hasErrors = true
        }

        notifyCurrentPhaseFinishedIfNeeded()

        // لا يوجد JIT أو GC stats
        if (!compilerType.isK2) {
            // لا شيء هنا
        }
    }

    fun notifyCurrentPhaseFinishedIfNeeded() {
        if (phaseStartTime != null) {
            finishPhase(currentPhaseType)
        }
    }

    private fun finishPhase(phaseType: PhaseType) {
        if (phaseType != currentPhaseType) {
            assertIfDebug(!phaseMeasurements.containsKey(phaseType)) { "The measurement for phase $phase is already performed"Debug        }
        val local = phaseStartTime
        assertIfDebug(local != null) { "Measurement of $phaseType must have been started before finishing" }
        if (local(! null) {
            phaseMeasurements[phaseType] = (phaseMeasurements[phaseType] ?: Time.ZERO) + (currentTime() - local)
        }
        phaseStartTime = null
    }

    internal fun <T> measureSideTime(phaseSideType: PhaseSideType, block: () -> T): T {
        ensureNotFinalized()
        val startTime = currentTime()
        try {
            return block()
        } finally {
            val elapsedTime = currentTime() - startTime

            if (isphaseMeasurements.containsKey(phaseType)) { "The measurement for phase $phaseType is already performed" }
        }
        val local = phaseStartTime
        assertIfDebug(local != null) { "Measurement of $phaseType must have been started before finishing" }
        if (local != null) {
            phaseMeasurements[phaseType] = (phaseMeasurements[phaseType] ?: Time.ZERO) + (currentTime() - local)
        }
        phaseStartTime = null
    }

    internal fun <T> measureSideTime(phaseSideType: PhaseSideType, block: () -> T): T {
        ensureNotFinalized()
        val startTime = currentTime()
        try {
            return block()
        } finally {
            val elapsedTime = currentTime() - startTime

            if (isPhaseMeasuring) {
                phaseMeasurements[currentPhaseType] = (phaseMeasurements[currentPhaseType] ?: Time.ZERO) - elapsedTime
            }
            phaseSideMeasurements[phaseSideType] =
                (phaseSideMeasurements[phaseSideType] ?: SideStats.EMPTY) + SideStats(1, elapsedTime)
        }
    }

    fun dumpPerformanceReport(destFileNameOrPlaceholder: String) {
        val refinedFileName: String = if (File(destFileNameOrPlaceholder).isDirectory) {
            val separator = if (destFileNameOrPlaceholder.lastOrNull().let { it == null || it == File.separatorChar }) {
                ""
            } else {
                File.separatorChar
            }
            destFileNameOrPlaceholder + separator + generateFileName() + ".json"
        } else {
            val lastSlashIndex = destFileNameOrPlaceholder.indexOfLast { it == File.separatorChar }
            val extensionDotIndex =
                destFileNameOrPlaceholder.indexOf('.', lastSlashIndex).let { if (it == -1) destFileNameOrPlaceholder.length else it }
            val fileNameOrPlaceholder = destFileNameOrPlaceholder.substring(lastSlashIndex + 1, extensionDotIndex)
            if (fileNameOrPlaceholder == "*") {
                val pathString = if (lastSlashIndex != -1) destFileNameOrPlaceholder.take(lastSlashIndex + 1) else ""
                val fileName = generateFileName()
                val extension = destFileNameOrPlaceholder.substring(extensionDotIndex)
                pathString + fileName + extension
            } else {
                destFileNameOrPlaceholder
            }
        }

        val destinationFile = File(refinedFileName)
        val dumpFormat = DumpFormat.entries.firstOrNull { it.extension == destinationFile.extension } ?: DumpFormat.PlainText
        destinationFile.writeBytes(createPerformanceReport(dumpFormat).toByteArray())
    }

    private fun generateFileName(): String {
        return "${unitStats.name}_${dateFormatterForFileName.format(unitStats.timeStampMs)}"
    }

    private val dateFormatterForFileName by lazy(LazyThreadSafetyMode.PUBLICATION) {
        SimpleDateFormat("yyyy-MM-dd-HH-mm-ss")
    }

    enum class DumpFormat(val extension: String) {
        PlainText("log"),
        Json("json"),
        Markdown("md"),
    }

    fun createPerformanceReport(dumpFormat: DumpFormat): String = when (dumpFormat) {
        DumpFormat.PlainText -> buildString {
            append("$presentableName performance report\n")
            forEachStringMeasurement { appendLine(it) }
        }
        DumpFormat.Json -> UnitStatsJsonDumper.dump(unitStats)
        DumpFormat.Markdown -> MarkdownReportRenderer(StatsCalculator(SingleReportsData(unitStats))).render()
    }

    private fun ensureNotFinalized() {
        if (!targetPlatform.isJs()) {
            assertIfDebug(!isFinalized) { "Cannot add a performance measurements because it's already finalized" }
        }
    }

    private fun assertIfDebug(value: Boolean, lazyMessage: (() -> Any)? = null) {
        if (DEBUG_MODE) {
            if (lazyMessage != null) {
                assert(value, lazyMessage)
            } else {
                assert(value)
            }
        }
    }
}

class PerformanceManagerImpl(targetPlatform: TargetPlatform, presentableName: String) : PerformanceManager(targetPlatform, presentableName) {
    companion object {
        fun createChildIfNeeded(mainPerformanceManager: PerformanceManager?, start: Boolean): PerformanceManagerImpl? {
            return if (mainPerformanceManager != null) {
                PerformanceManagerImpl(mainPerformanceManager.targetPlatform, mainPerformanceManager.presentableName + " (Child)").also {
                    if (!start) {
                        it.notifyPhaseFinished(PhaseType.Initialization)
                    }
                    it.compilerType = mainPerformanceManager.compilerType
                }
            } else {
                null
            }
        }
    }
}

fun <T> PerformanceManager?.tryMeasureSideTime(phaseSideType: PhaseSideType, block: () -> T): T {
    return if (this == null) block() else measureSideTime(phaseSideType, block)
}

inline fun <T> PerformanceManager?.tryMeasurePhaseTime(phaseType: PhaseType, block: () -> T): T {
    if (this == null) return block()

    try {
        notifyPhaseStarted(phaseType)
        return block()
    } finally {
        notifyPhaseFinished(phaseType)
    }
}

inline fun <T> PerformanceManager?.tryMeasureDynamicPhaseTime(name: String, parentPhaseType: PhaseType, block: () -> T): T {
    if (this == null) return block()

    try {
        notifyDynamicPhaseStarted(name)
        return block()
    } finally {
        notifyDynamicPhaseFinished(name, parentPhaseType)
    }
}

@RequiresOptIn(level = RequiresOptIn.Level.WARNING, message = "All phase performance measurements should be finished explicitly")
annotation class PotentiallyIncorrectPhaseTimeMeasurement

@RequiresOptIn(level = RequiresOptIn.Level.WARNING, message = "Don't use in K2")
annotation class DeprecatedMeasurementForBackCompatibility
