package com.ycsopen.sms.core.cmpp;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

final class CmppDownstreamBodyCodec {
    private CmppDownstreamBodyCodec() {
    }

    static byte[] submit(String submitId, String destination, String serviceId, String templateId,
                         String productCode, boolean registeredDelivery, String content) {
        Writer writer = new Writer();
        writer.string(submitId);
        writer.string(destination);
        writer.string(serviceId);
        writer.string(templateId);
        writer.string(productCode);
        writer.byteValue((byte) (registeredDelivery ? 1 : 0));
        writer.string(content);
        return writer.toByteArray();
    }

    static Submit submit(byte[] body) {
        Reader reader = new Reader(body);
        return new Submit(reader.string(), reader.string(), reader.string(), reader.string(),
                reader.string(), reader.byteValue() == 1, reader.string()).checked();
    }

    record Submit(String submitId, String destination, String serviceId, String templateId,
                  String productCode, boolean registeredDelivery, String content) {
        Submit checked() {
            if (blank(submitId) || blank(destination) || blank(serviceId)
                    || blank(templateId) || blank(productCode) || blank(content)) {
                throw new IllegalArgumentException("missing downstream CMPP submit binding");
            }
            if (!destination.matches("1[3-9][0-9]{9}")) {
                throw new IllegalArgumentException("invalid downstream CMPP destination");
            }
            return new Submit(submitId.trim(), destination.trim(), serviceId.trim(), templateId.trim(),
                    productCode.trim(), registeredDelivery, content.trim());
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static final class Writer {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();

        Writer string(String value) {
            byte[] bytes = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
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
            int length = intValue();
            if (length < 0 || buffer.remaining() < length) {
                throw new IllegalArgumentException("invalid downstream CMPP body length");
            }
            byte[] bytes = new byte[length];
            buffer.get(bytes);
            return new String(bytes, StandardCharsets.UTF_8);
        }

        int intValue() {
            if (buffer.remaining() < 4) {
                throw new IllegalArgumentException("incomplete downstream CMPP body");
            }
            return buffer.getInt();
        }

        byte byteValue() {
            if (!buffer.hasRemaining()) {
                throw new IllegalArgumentException("incomplete downstream CMPP body");
            }
            return buffer.get();
        }
    }
}
