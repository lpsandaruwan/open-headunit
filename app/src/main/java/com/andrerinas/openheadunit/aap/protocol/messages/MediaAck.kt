package com.andrerinas.openheadunit.aap.protocol.messages

import com.andrerinas.openheadunit.aap.AapMessage
import com.andrerinas.openheadunit.aap.protocol.proto.Media
import com.google.protobuf.Message

/**
 * Return one completed DATA message's credit in this channel's media session. ack is a count,
 * not a packet id or a claim that audio/video reached the device. Fragment count, CSD messages
 * and repeated media timestamps must not increase it. Outbound media ACKs use the phone's
 * MediaStart session; inbound microphone ACKs instead match our MicrophoneResponse session.
 */
class MediaAck(channel: Int, sessionId: Int, credits: Int = 1)
    : AapMessage(channel, Media.MsgType.MEDIA_MESSAGE_ACK_VALUE, makeProto(sessionId, credits)) {

    companion object {
        private fun makeProto(sessionId: Int, credits: Int): Message {
            require(credits > 0) { "media ACK credits must be positive" }
            return Media.Ack.newBuilder().apply {
                this.sessionId = sessionId
                this.ack = credits
            }.build()
        }
    }
}
