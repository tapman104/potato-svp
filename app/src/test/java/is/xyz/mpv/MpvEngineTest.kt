package `is`.xyz.mpv

import android.content.ContextWrapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class MpvEngineTest {

    private fun createTestContext(): ContextWrapper {
        return object : ContextWrapper(null) {
            override fun getApplicationContext(): android.content.Context = this
        }
    }

    @Before
    fun setUp() {
        MPVLib.clearObservers()
    }

    @Test
    fun testMpvEngineImplementsEventObserver() {
        val eventHandler = EngineEventHandler()
        val context = createTestContext()
        val engine = MpvEngine(context, eventHandler)

        @Suppress("USELESS_IS_CHECK")
        assertTrue("MpvEngine must implement MPVLib.EventObserver", engine is MPVLib.EventObserver)
    }

    @Test
    fun testEventPropertyLongUpdatesGeometry() {
        val eventHandler = EngineEventHandler()
        val context = createTestContext()
        val engine = MpvEngine(context, eventHandler)

        var reportedGeometry: VideoGeometry? = null
        engine.onVideoGeometryChanged = { geometry ->
            reportedGeometry = geometry
        }

        val observer: MPVLib.EventObserver = engine
        observer.eventProperty("width", 1920L)
        assertEquals(1920, reportedGeometry?.width)

        observer.eventProperty("height", 1080L)
        assertEquals(1080, reportedGeometry?.height)

        observer.eventProperty("video-rotate", 90L)
        assertEquals(90, reportedGeometry?.rotationDegrees)
    }

    @Test
    fun testEventPropertyDoubleUpdatesPlaybackProgress() {
        val eventHandler = EngineEventHandler()
        val context = createTestContext()
        val engine = MpvEngine(context, eventHandler)

        var reportedPos = 0.0
        var reportedDur = 0.0
        engine.onPlaybackProgressChanged = { pos, dur ->
            reportedPos = pos
            reportedDur = dur
        }

        val observer: MPVLib.EventObserver = engine
        observer.eventProperty("time-pos", 45.5)
        assertEquals(45.5, reportedPos, 0.001)

        observer.eventProperty("duration", 120.0)
        assertEquals(120.0, reportedDur, 0.001)
    }

    @Test
    fun testInitializationRegistersBothObserversOnce() {
        val eventHandler = EngineEventHandler()
        val context = createTestContext()
        val engine = MpvEngine(context, eventHandler)

        assertEquals(0, MPVLib.registeredObservers.size)

        engine.initialize(configDir = "/tmp/config", cacheDir = "/tmp/cache")

        assertEquals(EngineStatus.Ready, engine.status.value)
        val observers = MPVLib.registeredObservers
        assertEquals(2, observers.size)
        assertTrue(observers.contains(engine))
        assertTrue(observers.contains(eventHandler))
    }

    @Test
    fun testInitFailureRemovesBothObservers() {
        val eventHandler = EngineEventHandler()
        val context = createTestContext()
        val engine = MpvEngine(context, eventHandler)

        MPVLib.simulateInitFailure = true

        try {
            engine.initialize(configDir = "/tmp/config", cacheDir = "/tmp/cache")
            fail("Expected initialize to throw on simulated failure")
        } catch (_: Throwable) {
            // Expected
        }

        assertTrue(engine.status.value is EngineStatus.Failed)
        assertEquals(0, MPVLib.registeredObservers.size)
    }

    @Test
    fun testPostCreateFailureCallsDestroy() {
        val eventHandler = EngineEventHandler()
        val context = createTestContext()
        val engine = MpvEngine(context, eventHandler)

        MPVLib.simulatePostCreateFailure = true

        try {
            engine.initialize(configDir = "/tmp/config", cacheDir = "/tmp/cache")
            fail("Expected initialize to throw on post-create failure")
        } catch (_: Throwable) {
            // Expected
        }

        assertTrue(engine.status.value is EngineStatus.Failed)
        assertEquals(0, MPVLib.registeredObservers.size)
        assertTrue("MPVLib.destroy() must be called when post-create step fails", MPVLib.destroyCallCount > 0)
    }

    @Test
    fun testDestroyRemovesBothAndResetsHandler() {
        val eventHandler = EngineEventHandler()
        val context = createTestContext()
        val engine = MpvEngine(context, eventHandler)

        engine.initialize(configDir = "/tmp/config", cacheDir = "/tmp/cache")
        assertEquals(2, MPVLib.registeredObservers.size)

        // Mutate event handler state
        eventHandler.eventProperty("pause", true)
        eventHandler.eventProperty("media-title", "Test Title")
        assertEquals(true, eventHandler.trackState.value.isPaused)
        assertEquals("Test Title", eventHandler.trackState.value.mediaTitle)

        engine.destroy()

        assertEquals(EngineStatus.NotStarted, engine.status.value)
        assertEquals(0, MPVLib.registeredObservers.size)
        assertNull(eventHandler.trackState.value.isPaused)
        assertNull(eventHandler.trackState.value.mediaTitle)
    }

    @Test
    fun testReinitializationDoesNotDuplicateObservers() {
        val eventHandler = EngineEventHandler()
        val context = createTestContext()
        val engine = MpvEngine(context, eventHandler)

        // 1. First initialization
        engine.initialize(configDir = "/tmp/config", cacheDir = "/tmp/cache")
        assertEquals(2, MPVLib.registeredObservers.size)

        // 2. Destroy
        engine.destroy()
        assertEquals(0, MPVLib.registeredObservers.size)

        // 3. Re-initialize on the same engine instance
        engine.initialize(configDir = "/tmp/config", cacheDir = "/tmp/cache")
        val observers = MPVLib.registeredObservers
        assertEquals(2, observers.size)
        assertEquals(1, observers.count { it === engine })
        assertEquals(1, observers.count { it === eventHandler })
    }

    @Test
    fun testViewModelHasNoDirectObserverRegistration() {
        // PlayerViewModel must not implement MPVLib.EventObserver
        assertFalse(
            "PlayerViewModel must not implement MPVLib.EventObserver",
            MPVLib.EventObserver::class.java.isAssignableFrom(PlayerViewModel::class.java)
        )

        // PlayerViewModel must not declare any eventProperty or event overrides
        val declaredMethodNames = PlayerViewModel::class.java.declaredMethods.map { it.name }
        assertFalse(
            "PlayerViewModel must not declare eventProperty overrides",
            declaredMethodNames.contains("eventProperty")
        )
        assertFalse(
            "PlayerViewModel must not declare event overrides",
            declaredMethodNames.contains("event")
        )
    }

    @Test
    fun testPlayFileRearmsPauseObserver() {
        val eventHandler = EngineEventHandler()
        val context = createTestContext()
        val engine = MpvEngine(context, eventHandler)

        engine.initialize(configDir = "/tmp/config", cacheDir = "/tmp/cache")
        MPVLib.observedProperties.clear()

        // Attach surface so engine is ready to load file immediately
        val surface = android.view.Surface(android.graphics.SurfaceTexture(0))
        engine.onSurfaceCreated(surface)

        engine.playFile("/path/to/video.mp4")

        assertTrue(
            "observeProperty(\"pause\", MPV_FORMAT_FLAG) must be re-armed when loading a file",
            MPVLib.observedProperties.any { it.first == "pause" && it.second == MPVLib.MpvFormat.MPV_FORMAT_FLAG }
        )
    }

    // FIX CHECK-A: Test process-wide ownership guard prevents concurrent initialization
    @Test
    fun testProcessWideOwnershipGuardThrowsOnConcurrentCreate() {
        val eventHandler1 = EngineEventHandler()
        val context1 = createTestContext()
        val engine1 = MpvEngine(context1, eventHandler1)
        engine1.initialize(configDir = "/tmp/config", cacheDir = "/tmp/cache")

        val eventHandler2 = EngineEventHandler()
        val context2 = createTestContext()
        val engine2 = MpvEngine(context2, eventHandler2)

        try {
            engine2.initialize(configDir = "/tmp/config", cacheDir = "/tmp/cache")
            fail("Expected second MpvEngine.initialize to throw IllegalStateException due to MpvLifecycleGuard")
        } catch (e: IllegalStateException) {
            assertEquals("MPVLib is already owned", e.message)
        } finally {
            engine1.destroy()
        }

        // After engine1 destroys, engine2 should be able to initialize cleanly
        engine2.initialize(configDir = "/tmp/config", cacheDir = "/tmp/cache")
        assertEquals(EngineStatus.Ready, engine2.status.value)
        engine2.destroy()
    }
}
