package com.ycsopen.sms.core.cmpp;

/** Command IDs used by CMPP 2.0/3.0 compatible client and simulator tests. */
public final class CmppCommands {
    public static final int CONNECT = 0x00000001;
    public static final int CONNECT_RESP = 0x80000001;
    public static final int TERMINATE = 0x00000002;
    public static final int TERMINATE_RESP = 0x80000002;
    public static final int SUBMIT = 0x00000004;
    public static final int SUBMIT_RESP = 0x80000004;
    public static final int DELIVER = 0x00000005;
    public static final int DELIVER_RESP = 0x80000005;
    public static final int ACTIVE_TEST = 0x00000008;
    public static final int ACTIVE_TEST_RESP = 0x80000008;

    private CmppCommands() {
    }
}
