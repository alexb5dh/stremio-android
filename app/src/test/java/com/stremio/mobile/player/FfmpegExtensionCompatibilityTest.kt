package com.stremio.mobile.player

import android.os.Handler
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.video.VideoRendererEventListener
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** The prebuilt extension targets an older media3; DefaultRenderersFactory creates its renderers by reflection (as of 1.10.1). */
class FfmpegExtensionCompatibilityTest {
    @Test
    fun `audio renderer matches media3`() {
        val renderer = load("androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer")
        renderer.getConstructor(Handler::class.java, AudioRendererEventListener::class.java, AudioSink::class.java)
        assertNoUnimplementedAbstractMethods(renderer)
    }

    /** Upstream stub: delete this test if the extension drops it. */
    @Test
    fun `video renderer matches media3`() {
        val renderer = load("androidx.media3.decoder.ffmpeg.ExperimentalFfmpegVideoRenderer")
        renderer.getConstructor(Long::class.java, Handler::class.java, VideoRendererEventListener::class.java, Int::class.java)
        assertNoUnimplementedAbstractMethods(renderer)
    }

    private fun load(name: String): Class<*> = Class.forName(name, false, javaClass.classLoader)

    private fun assertNoUnimplementedAbstractMethods(type: Class<*>) {
        val unimplemented = abstractMethods(type).filter { findImplementation(type, it) == null }
        assertTrue("${type.name} doesn't implement: ${unimplemented.joinToString()}", unimplemented.isEmpty())
    }

    private fun abstractMethods(type: Class<*>): List<Method> =
        (superclasses(type) + allInterfaces(type)).flatMap { it.declaredMethods.toList() }
            .filter { Modifier.isAbstract(it.modifiers) && !Modifier.isStatic(it.modifiers) }

    /** By name and erased parameters, so bridge methods and interface defaults count. */
    private fun findImplementation(type: Class<*>, abstract: Method): Method? {
        fun Class<*>.concreteMatch() = declaredMethods.firstOrNull {
            it.name == abstract.name && it.parameterTypes.contentEquals(abstract.parameterTypes) && !Modifier.isAbstract(it.modifiers)
        }
        return superclasses(type).firstNotNullOfOrNull { it.concreteMatch() }
            ?: allInterfaces(type).firstNotNullOfOrNull { it.concreteMatch()?.takeIf(Method::isDefault) }
    }

    private fun superclasses(type: Class<*>): List<Class<*>> = generateSequence(type) { it.superclass }.toList()

    private fun allInterfaces(type: Class<*>): Set<Class<*>> {
        val result = linkedSetOf<Class<*>>()
        val pending = ArrayDeque(superclasses(type).flatMap { it.interfaces.asList() })
        while (pending.isNotEmpty()) {
            val next = pending.removeFirst()
            if (result.add(next)) pending.addAll(next.interfaces)
        }
        return result
    }
}
