# Phase 30 Design

Components:

- `CmppPdu`: binary frame contract with 12-byte CMPP header.
- `CmppFrameReader`: TCP fragmentation reassembly.
- `CmppAuthenticator`: CONNECT authenticator source calculation.
- `CmppSubmitSegmenter`: UTF-16BE long-message segmentation with concatenation metadata.
- `CmppClientSession`: session state, auth, heartbeat, submit window, sequence correlation, disconnect/reconnect state, and delivery normalization.
- `CmppAuthoritativeSimulator`: deterministic simulator for connect/submit/receipt/uplink interop.
- `CmppSmsUpstreamProviderClient`: adapter to existing upstream provider SPI.
