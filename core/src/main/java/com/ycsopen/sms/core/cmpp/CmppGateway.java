package com.ycsopen.sms.core.cmpp;

import java.util.List;

/** Authoritative simulator or socket adapter boundary for one CMPP upstream connection. */
public interface CmppGateway {
    CmppPdu exchange(CmppPdu request);

    default List<CmppPdu> drainDeliveries() {
        return List.of();
    }
}
