# Phase 47 INTENT

Phase 47 closes the retention/archive product gap with the smallest useful implementation: a durable encrypted archive manifest and a controlled restore/export flow.

The phase avoids physical partition movement and external object storage because those are deployment concerns for later assurance phases. The user-visible and testable behavior is preserved: policy, eligible scan, encrypted archive evidence, checksum verification, restore, export and failure visibility.
