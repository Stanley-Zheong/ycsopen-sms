/**
 * CMPP 协议编解码与连接管理（F-6.7/F-6.8/F-6.9，上游连接器见 PRD 4.2 节"通道层"）。
 * <p>Phase 30 provides the protocol core and authoritative simulator used by the upstream connector.
 * The socket transport adapter is intentionally outside this package; the protocol contract is verified
 * through binary PDU header round-trips, window/sequence/reconnect state, simulator interoperability, and the
 * existing upstream provider SPI adapter. The simulator body codec is a test contract, not the final carrier
 * CMPP 2.0/3.0 body mapping.</p>
 */
package com.ycsopen.sms.core.cmpp;
