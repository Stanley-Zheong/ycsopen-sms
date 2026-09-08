package com.ycsopen.sms.core.service.tenant;

import java.util.Arrays;

/** Narrow provider boundary for business-license fact extraction; it never decides admission. */
public interface QualificationInspectionSpi {
    Facts inspect(Document document);

    final class Document {
        private final String mediaType;
        private final byte[] bytes;

        public Document(String mediaType, byte[] bytes) {
            if (!("application/pdf".equals(mediaType) || "image/jpeg".equals(mediaType)
                    || "image/png".equals(mediaType)) || bytes == null || bytes.length == 0
                    || bytes.length > 10 * 1024 * 1024) throw Failure.unavailable();
            this.mediaType = mediaType;
            this.bytes = bytes.clone();
        }

        public String mediaType() { return mediaType; }
        public byte[] bytes() { return bytes.clone(); }
        public void destroy() { Arrays.fill(bytes, (byte) 0); }
        @Override public String toString() { return "Document[mediaType=" + mediaType + ", bytes=[redacted]]"; }
    }

    record Facts(String companyName, String creditCode, double confidence, String requestId) { }

    final class Failure extends RuntimeException {
        private Failure() { super("qualification inspection unavailable", null, false, false); }
        public static Failure unavailable() { return new Failure(); }
    }
}
