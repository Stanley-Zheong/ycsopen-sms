# Phase 30 Intent

Deliver an executable CMPP upstream connector core while avoiding a fake production socket service. The phase makes client-side session behavior, the 12-byte PDU header contract, simulator interoperability, and the upstream SPI adapter testable.

Boundary: this phase does not claim production carrier interoperability. The simulator body codec is intentionally compact and test-oriented; a future socket/carrier phase must map the body fields to the exact CMPP 2.0/3.0 carrier wire specification.
