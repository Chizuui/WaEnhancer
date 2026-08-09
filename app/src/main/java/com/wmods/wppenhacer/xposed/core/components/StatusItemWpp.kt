package com.wmods.wppenhacer.xposed.core.components

import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import java.io.File
import java.lang.reflect.Field
import java.util.concurrent.ConcurrentHashMap

class StatusItemWpp private constructor(
    val fStatus: FStatusWpp?,
    private val directFMessage: FMessageWpp?
) {
    val fMessage: FMessageWpp?
        get() = directFMessage ?: fStatus?.fMessage

    val isFromMe: Boolean
        get() = directFMessage?.key?.isFromMe ?: fStatus?.fStatusKey?.isFromMe ?: false

    val messageID: String
        get() = directFMessage?.key?.messageID ?: fStatus?.fStatusKey?.messageID ?: ""

    val senderJid: FMessageWpp.UserJid?
        get() = directFMessage?.userJid ?: fStatus?.fStatusKey?.senderJid

    val isMediaFile: Boolean
        get() = directFMessage?.isMediaFile ?: fStatus?.isMediaFile ?: false

    fun getMediaFile(): File? = directFMessage?.mediaFile ?: fStatus?.getMediaFile()

    companion object {
        // Thread-safe field cache: from() is called on multiple threads
        private val fStatusFieldCache = ConcurrentHashMap<Class<*>, Field?>()

        @JvmStatic
        fun from(obj: Any?): StatusItemWpp? {
            if (obj == null) return null
            val fMsgField = ReflectionUtils.findFieldUsingFilterIfExists(obj.javaClass) { f ->
                FMessageWpp.TYPE.isAssignableFrom(f.type)
            }
            fMsgField?.get(obj)?.let { return StatusItemWpp(null, FMessageWpp(it)) }
            val fStatusField = fStatusFieldCache.computeIfAbsent(obj.javaClass) {
                ReflectionUtils.findFieldUsingFilterIfExists(it) { f ->
                    FStatusWpp.TYPE.isAssignableFrom(f.type)
                }
            }
            fStatusField?.get(obj)?.let { return StatusItemWpp(FStatusWpp(it), null) }
            return null
        }
    }
}