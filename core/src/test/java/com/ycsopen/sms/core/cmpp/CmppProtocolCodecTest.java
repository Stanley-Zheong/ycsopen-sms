package com.ycsopen.sms.core.cmpp;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CmppProtocolCodecTest {
    @Test
    void pduRoundTripAndFragmentedTcpFramesAreDecodedBySequence() {
        CmppPdu first = new CmppPdu(CmppCommands.ACTIVE_TEST, 7, new byte[]{1, 2});
        CmppPdu second = new CmppPdu(CmppCommands.TERMINATE, 8, new byte[]{3});
        byte[] joined = join(first.encode(), second.encode());

        CmppFrameReader reader = new CmppFrameReader();
        assertThat(reader.append(Arrays.copyOfRange(joined, 0, 5))).isEmpty();
        assertThat(reader.append(Arrays.copyOfRange(joined, 5, first.encode().length + 2))).hasSize(1)
                .first().extracting(CmppPdu::sequenceId).isEqualTo(7);
        assertThat(reader.append(Arrays.copyOfRange(joined, first.encode().length + 2, joined.length))).hasSize(1)
                .first().extracting(CmppPdu::commandId).isEqualTo(CmppCommands.TERMINATE);
    }

    @Test
    void rejectsMalformedFrameLength() {
        assertThatThrownBy(() -> CmppPdu.decode(new byte[]{0, 0, 0, 11, 0, 0, 0, 1, 0, 0, 0, 1}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("length");
    }

    @Test
    void longMessageSegmentationUsesStableConcatenationHeaders() {
        String content = "验证码".repeat(60);

        var segments = new CmppSubmitSegmenter().segment(content);

        assertThat(segments).hasSizeGreaterThan(1);
        assertThat(segments).allSatisfy(segment -> {
            assertThat(segment.concatenated()).isTrue();
            assertThat(segment.total()).isEqualTo(segments.size());
            assertThat(segment.payload().length).isLessThanOrEqualTo(134);
        });
    }

    private static byte[] join(byte[] left, byte[] right) {
        byte[] joined = Arrays.copyOf(left, left.length + right.length);
        System.arraycopy(right, 0, joined, left.length, right.length);
        return joined;
    }
}
