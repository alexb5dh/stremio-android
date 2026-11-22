package com.stremio.mobile.player

import android.os.Handler
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.video.VideoRendererEventListener
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * The FFmpeg extension is a prebuilt library compiled against an older media3 and is only linked at runtime: DefaultRenderersFactory
 * instantiates its renderers by reflection, and a failure there breaks ExoPlayer creation for every stream. The constructor
 * signatures below are the ones DefaultRenderersFactory looks up as of media3 1.10.1; if a newer media3 looks up different ones,
 * update them here.
 */
class FfmpegExtensionCompatibilityTest {
    @Test
    fun `audio renderer matches media3`() {
        val renderer = load("androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer")
        assertPublicConstructor(renderer, Handler::class.java, AudioRendererEventListener::class.java, AudioSink::class.java)
        assertNoUnimplementedAbstractMethods(renderer)
    }

    @Test
    fun `video renderer matches media3`() {
        val renderer = load("androidx.media3.decoder.ffmpeg.ExperimentalFfmpegVideoRenderer")
        assertPublicConstructor(
            renderer, Long::class.java, Handler::class.java, VideoRendererEventListener::class.java, Int::class.java
        )
        assertNoUnimplementedAbstractMethods(renderer)
    }

    private fun load(name: String): Class<*> = Class.forName(name, false, javaClass.classLoader)

    private fun assertPublicConstructor(type: Class<*>, vararg parameterTypes: Class<*>) {
        val constructor = type.getConstructor(*parameterTypes)
        assertTrue("$constructor is not public", Modifier.isPublic(constructor.modifiers))
    }

    private fun assertNoUnimplementedAbstractMethods(type: Class<*>) {
        val unimplemented = abstractMethods(type).filter { findImplementation(type, it) == null }
        assertTrue("${type.name} doesn't implement: ${unimplemented.joinToString()}", unimplemented.isEmpty())
    }

    private fun abstractMethods(type: Class<*>): List<Method> =
        (superclasses(type) + allInterfaces(type)).flatMap { it.declaredMethods.toList() }
            .filter { Modifier.isAbstract(it.modifiers) && !Modifier.isStatic(it.modifiers) }

    /** Matches by name and erased parameter types, so compiler bridge methods and interface defaults count. */
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
