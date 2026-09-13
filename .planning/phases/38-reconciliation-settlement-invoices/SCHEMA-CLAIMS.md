# Phase 38 Schema Claims

| Claim ID | Object | Migration | Claim | Dependencies | Evidence |
| --- | --- | --- | --- | --- | --- |
| SCHEMA-P38-STATEMENTS | statements | V4700 | Statements preserve source period, counts, amount, billing mode, price version, confirmations, and settlement state. | V4600 | ReconciliationSettlementInvoicesMigrationTest |
| SCHEMA-P38-DIFFERENCES | statement_differences | V4700 | Differences preserve type, claimed amount, note, evidence, owner, status, and resolution. | V4700 | ReconciliationSettlementServiceTest |
| SCHEMA-P38-SETTLEMENTS | settlement_records | V4700 | Settlement state advances once per statement with payment evidence. | V4700 | ReconciliationSettlementServiceTest |
| SCHEMA-P38-INVOICES | invoices | V4700 | Invoices link to statements and preserve request/issue trace. | V4700 | ReconciliationSettlementServiceTest |
