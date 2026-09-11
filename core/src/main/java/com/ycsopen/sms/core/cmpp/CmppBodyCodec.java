package com.ycsopen.sms.core.cmpp;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

final class CmppBodyCodec {
    private CmppBodyCodec() {
    }

    static byte[] connect(String sourceAddress, byte[] authenticator, byte version, int timestamp) {
        Writer writer = new Writer();
        writer.string(sourceAddress);
        writer.bytes(authenticator);
        writer.byteValue(version);
        writer.intValue(timestamp);
        return writer.toByteArray();
    }

    static Connect connect(byte[] body) {
        Reader reader = new Reader(body);
        return new Connect(reader.string(), reader.bytes(), reader.byteValue(), reader.intValue());
    }

    static byte[] connectResp(int status, byte version) {
        return new Writer().intValue(status).byteValue(version).toByteArray();
    }

    static ConnectResp connectResp(byte[] body) {
        Reader reader = new Reader(body);
        return new ConnectResp(reader.intValue(), reader.byteValue());
    }

    static byte[] submit(String idempotencyKey, String messageId, String destination,
                         int segmentReference, int segmentTotal, int segmentIndex,
                         boolean registeredDelivery, byte[] payload) {
        Writer writer = new Writer();
        writer.string(idempotencyKey);
        writer.string(messageId);
        writer.string(destination);
        writer.intValue(segmentReference);
        writer.intValue(segmentTotal);
        writer.intValue(segmentIndex);
        writer.byteValue((byte) (registeredDelivery ? 1 : 0));
        writer.bytes(payload);
        return writer.toByteArray();
    }

    static Submit submit(byte[] body) {
        Reader reader = new Reader(body);
        return new Submit(reader.string(), reader.string(), reader.string(), reader.intValue(),
                reader.intValue(), reader.intValue(), reader.byteValue() == 1, reader.bytes());
    }

    static byte[] submitResp(String providerMessageId, int result) {
        return new Writer().string(providerMessageId).intValue(result).toByteArray();
    }

    static SubmitResp submitResp(byte[] body) {
        Reader reader = new Reader(body);
        return new SubmitResp(reader.string(), reader.intValue());
    }

    static byte[] deliverReport(String providerMessageId, String messageId, String status) {
        return new Writer().string(providerMessageId).string(status).byteValue((byte) 1).toByteArray();
    }

    static byte[] uplink(String sourceAddress, String content) {
        return new Writer().string(sourceAddress).string(content).byteValue((byte) 0).toByteArray();
    }

    static Deliver decodeDeliver(byte[] body) {
        Reader reader = new Reader(body);
        String first = reader.string();
        String second = reader.string();
        boolean report = reader.byteValue() == 1;
        return new Deliver(first, second, report);
    }

    static byte[] deliverResp(String providerMessageId, int result) {
        return new Writer().string(providerMessageId).intValue(result).toByteArray();
    }

    record Connect(String sourceAddress, byte[] authenticator, byte version, int timestamp) {
        Connect {
            authenticator = authenticator.clone();
        }

        @Override
        public byte[] authenticator() {
            return authenticator.clone();
        }
    }
    record ConnectResp(int status, byte version) { }
    record Submit(String idempotencyKey, String messageId, String destination,
                  int segmentReference, int segmentTotal, int segmentIndex,
                  boolean registeredDelivery, byte[] payload) {
        Submit {
            payload = payload.clone();
        }

        @Override
        public byte[] payload() {
            return payload.clone();
        }
    }
    record SubmitResp(String providerMessageId, int result) { }
    record Deliver(String first, String second, boolean report) { }

    private static final class Writer {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();

        Writer string(String value) {
            byte[] bytes = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
            intValue(bytes.length);
            out.writeBytes(bytes);
            return this;
        }

        Writer bytes(byte[] value) {
            byte[] bytes = value == null ? new byte[0] : value;
            intValue(bytes.length);
            out.writeBytes(bytes);
            return this;
        }

        Writer intValue(int value) {
            out.writeBytes(ByteBuffer.allocate(4).putInt(value).array());
            return this;
        }

        Writer byteValue(byte value) {
            out.write(value);
            return this;
        }

        byte[] toByteArray() {
            return out.toByteArray();
        }
    }

    private static final class Reader {
        private final ByteBuffer buffer;

        Reader(byte[] body) {
            buffer = ByteBuffer.wrap(body == null ? new byte[0] : body);
        }

        String string() {
            byte[] bytes = bytes();
            return new String(bytes, StandardCharsets.UTF_8);
        }

        byte[] bytes() {
            int length = intValue();
            if (length < 0 || buffer.remaining() < length) {
                throw new IllegalArgumentException("invalid CMPP body length");
            }
            byte[] bytes = new byte[length];
            buffer.get(bytes);
            return bytes;
        }

        int intValue() {
            if (buffer.remaining() < 4) {
                throw new IllegalArgumentException("incomplete CMPP body");
            }
            return buffer.getInt();
        }

        byte byteValue() {
            if (!buffer.hasRemaining()) {
                throw new IllegalArgumentException("incomplete CMPP body");
            }
            return buffer.get();
        }
    }
}
