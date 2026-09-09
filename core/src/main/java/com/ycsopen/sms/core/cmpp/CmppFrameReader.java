package com.ycsopen.sms.core.cmpp;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Reassembles fragmented TCP bytes into complete CMPP frames. */
public final class CmppFrameReader {
    private final ByteArrayOutputStream pending = new ByteArrayOutputStream();

    public List<CmppPdu> append(byte[] chunk) {
        if (chunk == null || chunk.length == 0) {
            return List.of();
        }
        pending.writeBytes(chunk);
        byte[] bytes = pending.toByteArray();
        List<CmppPdu> frames = new ArrayList<>();
        int offset = 0;
        while (bytes.length - offset >= CmppPdu.HEADER_BYTES) {
            int length = ByteBuffer.wrap(bytes, offset, 4).getInt();
            if (length < CmppPdu.HEADER_BYTES) {
                throw new IllegalArgumentException("invalid CMPP frame length");
            }
            if (bytes.length - offset < length) {
                break;
            }
            frames.add(CmppPdu.decode(Arrays.copyOfRange(bytes, offset, offset + length)));
            offset += length;
        }
        pending.reset();
        if (offset < bytes.length) {
            pending.writeBytes(Arrays.copyOfRange(bytes, offset, bytes.length));
        }
        return frames;
    }
}
