package com.ycsopen.sms.core.cmpp;

import java.nio.ByteBuffer;
import java.util.Arrays;

/** CMPP-style binary frame: total_length + command_id + sequence_id + body. */
public record CmppPdu(int commandId, int sequenceId, byte[] body) {
    public static final int HEADER_BYTES = 12;

    public CmppPdu {
        if (commandId == 0 || sequenceId <= 0) {
            throw new IllegalArgumentException("invalid CMPP header");
        }
        body = body == null ? new byte[0] : body.clone();
    }

    @Override
    public byte[] body() {
        return body.clone();
    }

    public byte[] encode() {
        ByteBuffer buffer = ByteBuffer.allocate(HEADER_BYTES + body.length);
        buffer.putInt(HEADER_BYTES + body.length);
        buffer.putInt(commandId);
        buffer.putInt(sequenceId);
        buffer.put(body);
        return buffer.array();
    }

    public static CmppPdu decode(byte[] frame) {
        if (frame == null || frame.length < HEADER_BYTES) {
            throw new IllegalArgumentException("incomplete CMPP frame");
        }
        ByteBuffer buffer = ByteBuffer.wrap(frame);
        int length = buffer.getInt();
        if (length != frame.length || length < HEADER_BYTES) {
            throw new IllegalArgumentException("invalid CMPP frame length");
        }
        int commandId = buffer.getInt();
        int sequenceId = buffer.getInt();
        byte[] body = Arrays.copyOfRange(frame, HEADER_BYTES, frame.length);
        return new CmppPdu(commandId, sequenceId, body);
    }
}
