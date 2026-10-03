@file:Suppress("FunctionNaming")

package com.gurbakir.mobile.nativecompat

import android.graphics.Path
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.system.Os
import android.system.OsConstants
import androidx.datastore.core.MultiProcessDataStoreFactory
import androidx.datastore.core.Serializer
import androidx.graphics.path.PathIterator
import androidx.graphics.path.PathSegment
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in native compatibility evidence; normal API 23/30 lanes deliberately skip this probe. */
@RunWith(AndroidJUnit4::class)
class Native16KiBCompatibilityTest {
    @Test
    fun publicConicConversionUsesThePackagedGraphicsNativeLibraryOn16KiB() {
        requireNative16KiBRuntime()
        recordPackagedLibrary("androidx.graphics.path", "libandroidx.graphics.path.so")
        System.loadLibrary("androidx.graphics.path")

        // A weight other than one preserves an actual conic. API 34+ converts it through
        // the bundled ConicConverter JNI when the public AsQuadratics mode is requested.
        val path = Path().apply {
            moveTo(0f, 0f)
            if (Build.VERSION.SDK_INT >= 34) {
                conicTo(8f, 16f, 24f, 0f, 0.70710677f)
            } else {
                throw AssertionError("The conic fixture requires API 34")
            }
            close()
        }
        val conicSegments = PathIterator(path, PathIterator.ConicEvaluation.AsConic)
            .asSequence().toList()
        assertTrue(conicSegments.any { it.type == PathSegment.Type.Conic })

        val quadraticSegments = PathIterator(path, PathIterator.ConicEvaluation.AsQuadratics)
            .asSequence().toList()
        assertTrue(quadraticSegments.any { it.type == PathSegment.Type.Quadratic })
        assertFalse(quadraticSegments.any { it.type == PathSegment.Type.Conic })
        assertTrue(quadraticSegments.flatMap { it.points.toList() }.all { it.x.isFinite() && it.y.isFinite() })
        recordCompletedOperation("androidx.graphics.path", "public_conic_to_quadratics")
    }

    @Test
    fun multiProcessDataStoreReadsAndUpdatesThroughThePackagedNativeCounterOn16KiB() {
        requireNative16KiBRuntime()
        recordPackagedLibrary("datastore_shared_counter", "libdatastore_shared_counter.so")
        System.loadLibrary("datastore_shared_counter")

        val context = InstrumentationRegistry.getInstrumentation().context
        val directory = File(context.filesDir, "ui-v3-native-probe")
        assertTrue(directory.isDirectory || directory.mkdirs())
        val fixture = File(directory, "counter-" + UUID.randomUUID() + ".bin")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = MultiProcessDataStoreFactory.create(
                serializer = ProbeIntegerSerializer,
                scope = scope,
                produceFile = { fixture }
            )
            runBlocking {
                withTimeout(10_000) {
                    assertEquals(0, store.data.first())
                    assertEquals(37, store.updateData { it + 37 })
                    assertEquals(37, store.data.first())
                    assertEquals(38, store.updateData { it + 1 })
                    assertEquals(38, store.data.first())
                }
            }
            assertTrue(fixture.isFile)
            recordCompletedOperation("datastore_shared_counter", "multiprocess_read_update_read")
        } finally {
            scope.cancel()
        }
    }

    private fun requireNative16KiBRuntime() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(
            "Select require16k=true only on an explicitly selected 16KiB runtime",
            InstrumentationRegistry.getArguments().getString("require16k") == "true"
        )
        assertEquals(
            "This neutral probe must target its own test APK rather than an application store",
            instrumentation.context.packageName,
            instrumentation.targetContext.packageName
        )
        assertEquals("Actual kernel page size", 16_384L, Os.sysconf(OsConstants._SC_PAGESIZE))
        assertTrue("A 64-bit process is required", Process.is64Bit())
        if (Build.VERSION.SDK_INT < 34) {
            throw AssertionError("The native conic probe requires the public API 34 Path contract")
        }
        instrumentation.sendStatus(
            0,
            Bundle().apply {
                putString("nativeProbePhase", "runtime_verified")
                putString("kernelPageSize", "16384")
                putString("process64Bit", "true")
                putString("runtimeApi", Build.VERSION.SDK_INT.toString())
                putString("runtimeAbis", Build.SUPPORTED_ABIS.joinToString(","))
                putString("probePackage", instrumentation.context.packageName)
            }
        )
    }

    private fun recordPackagedLibrary(libraryName: String, archiveName: String) {
        val context = InstrumentationRegistry.getInstrumentation().context
        val abi = Build.SUPPORTED_ABIS.first { it == "arm64-v8a" || it == "x86_64" }
        val entryName = "lib/" + abi + "/" + archiveName
        val digest = MessageDigest.getInstance("SHA-256")
        ZipFile(context.packageCodePath).use { archive ->
            val entry = requireNotNull(archive.getEntry(entryName)) { "Missing packaged native library: " + entryName }
            archive.getInputStream(entry).use { input ->
                val buffer = ByteArray(8_192)
                var count = input.read(buffer)
                while (count != -1) {
                    digest.update(buffer, 0, count)
                    count = input.read(buffer)
                }
            }
        }
        val sha256 = digest.digest().joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
            .uppercase(Locale.ROOT)
        InstrumentationRegistry.getInstrumentation().sendStatus(
            0,
            Bundle().apply {
                putString("nativeProbePhase", "packaged_bytes_recorded")
                putString("nativeLibrary", libraryName)
                putString("packagedNativeEntry", entryName)
                putString("packagedNativeSha256", sha256)
            }
        )
    }

    private fun recordCompletedOperation(libraryName: String, operation: String) {
        InstrumentationRegistry.getInstrumentation().sendStatus(
            0,
            Bundle().apply {
                putString("nativeProbePhase", "native_operation_completed")
                putString("loadedNativeLibrary", libraryName)
                putString("nativeOperation", operation)
            }
        )
    }

    private object ProbeIntegerSerializer : Serializer<Int> {
        override val defaultValue = 0

        override suspend fun readFrom(input: InputStream): Int = DataInputStream(input).readInt()

        override suspend fun writeTo(t: Int, output: OutputStream) {
            DataOutputStream(output).writeInt(t)
        }
    }
}
