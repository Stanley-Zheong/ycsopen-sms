package com.ycsopen.sms.core.cmpp;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Splits long CMPP messages into UDH-labelled UTF-16BE segments. */
public final class CmppSubmitSegmenter {
    private static final int SINGLE_SEGMENT_CHARS = 70;
    private static final int CONCAT_SEGMENT_CHARS = 67;
    private final AtomicInteger reference = new AtomicInteger(1);

    public List<Segment> segment(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("message content is required");
        }
        String text = content.trim();
        if (text.length() <= SINGLE_SEGMENT_CHARS) {
            return List.of(new Segment(0, 1, 1, false, text.getBytes(StandardCharsets.UTF_16BE)));
        }
        int total = (int) Math.ceil((double) text.length() / CONCAT_SEGMENT_CHARS);
        if (total > 255) {
            throw new IllegalArgumentException("CMPP long message has too many segments");
        }
        int ref = reference.getAndUpdate(value -> value == 255 ? 1 : value + 1);
        List<Segment> segments = new ArrayList<>();
        for (int index = 0; index < total; index++) {
            int start = index * CONCAT_SEGMENT_CHARS;
            int end = Math.min(text.length(), start + CONCAT_SEGMENT_CHARS);
            segments.add(new Segment(ref, total, index + 1, true,
                    text.substring(start, end).getBytes(StandardCharsets.UTF_16BE)));
        }
        return segments;
    }

    public record Segment(int reference, int total, int index, boolean concatenated, byte[] payload) {
        public Segment {
            payload = payload == null ? new byte[0] : payload.clone();
        }

        @Override
        public byte[] payload() {
            return payload.clone();
        }
    }
}
