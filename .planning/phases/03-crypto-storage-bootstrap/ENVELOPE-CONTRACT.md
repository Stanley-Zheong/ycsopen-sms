# YCSE/v1 Envelope Contract

This file is the single canonical binary, AAD, capacity, and parser contract for
Phase 03. `03-RESEARCH.md`, decisions, design, and executable plans reference
this contract and must not restate an alternative wire format.

## Binary layout

All integers are unsigned big-endian. The fixed header is exactly 19 bytes.
The production provider is the six ASCII bytes `pkcs11`. A key reference is
ASCII matching `[a-z0-9][a-z0-9._-]{0,31}`.

| Offset | Field | Encoding | YCSE/v1 invariant |
| ---: | --- | --- | --- |
| 0 | magic | 4 bytes | ASCII `YCSE` |
| 4 | envelope version | u8 | `1` |
| 5 | data algorithm | u8 | `1` = AES-256-GCM with 128-bit tag |
| 6 | wrap algorithm | u8 | `1` = HSM AES-256-GCM with 128-bit tag |
| 7 | AAD schema | u8 | `1` |
| 8 | flags | u8 | `0`; every other value is unsupported |
| 9 | provider ID length | u8 | exactly `6` |
| 10 | key-reference length | u8 | `1..32` |
| 11 | wrap-nonce length | u8 | exactly `12` |
| 12 | data-nonce length | u8 | exactly `12` |
| 13 | wrapped-DEK length | u16 | exactly `48` |
| 15 | ciphertext length | u32 | plaintext length plus the 16-byte data tag and within the selected target bound |
| 19 | provider ID | declared bytes | exactly `pkcs11` |
| following | key reference | declared bytes | canonical ASCII grammar above |
| following | wrap nonce | 12 bytes | fresh for this KEK wrap |
| following | wrapped DEK | 48 bytes | 32-byte DEK encrypted with AES-GCM plus tag |
| following | data nonce | 12 bytes | fresh for this data encryption |
| following | ciphertext | declared bytes | protected bytes plus 16-byte GCM tag |

The parser rejects unknown versions, algorithms, AAD schemas, flags, provider
IDs, noncanonical key references, zero or oversized lengths, arithmetic
overflow, truncated input, and trailing bytes. It validates the complete fixed
header and all declared lengths before any length-derived allocation.

## Authenticated canonical headers

`wrap-header-auth-v1` is the exact 19-byte serialized header followed by the exact
provider-ID bytes and key-reference bytes. It includes `ciphertext length`; the
plaintext size is known before encryption, so there is no mutable-length
exception.

`data-header-auth-v1` is the same fixed header with only the key-reference length
normalized to zero, followed by the exact provider-ID bytes and no key-reference
bytes. This normalization is limited to data AAD: it keeps the data key, nonce,
ciphertext and tag byte-stable during a KEK rewrap. The independent wrap AEAD
authenticates the actual key-reference length and bytes, so the two tags together
still authenticate the complete envelope. Rewrap must first authenticate the old
full wrap header, then authenticate the replacement full wrap header and finally
verify the complete envelope before its compare-and-set update.

The semantic context is encoded as:

1. one AAD-schema byte (`1`);
2. six fields, each encoded as `u16 byte-length || canonical UTF-8 bytes`:
   purpose, logical owner/package, logical table or object class, field or
   content role, tenant scope, and immutable resource identity.

Every field is nonempty, each encoded field is at most 1024 bytes, the combined
semantic context is at most 6147 bytes, tenant scope is either `tenant:<id>` or
`global`, and purpose is exactly one of `database-field`, `protected-object`, or
`mysql-encrypted-snapshot-chunk`.

Domain separation is exact:

- data AAD is `ASCII("YCSE-DATA-AAD\u0000") || u32(len(data-header-auth-v1)) || data-header-auth-v1 || u32(len(context-v1)) || context-v1`;
- DEK-wrap AAD is `ASCII("YCSE-WRAP-AAD\u0000") || u32(len(wrap-header-auth-v1)) || wrap-header-auth-v1 || u32(len(context-v1)) || context-v1`.

No implementation may reuse one domain prefix for the other operation. A
mutation of magic, version, either algorithm, AAD schema, flags, any declared
length, provider ID, key reference, nonce, wrapped DEK, ciphertext, semantic
context, or domain prefix must fail closed under the applicable data or wrap tag.
Wrong key, wrong context, malformed tag, and tamper expose one sanitized
authentication category.

## Capacity and allocation bounds

Maximum YCSE/v1 overhead is 145 bytes: fixed header `19`, provider `6`, key
reference `32`, wrap nonce `12`, wrapped DEK `48`, data nonce `12`, and data tag
`16`.

| Target | Maximum plaintext | Maximum complete envelope |
| --- | ---: | ---: |
| V1 protected database field | 110 bytes | 255 bytes |
| business license | 10,485,760 bytes | 10,485,905 bytes |
| representative ID front | 5,242,880 bytes | 5,243,025 bytes |
| representative ID back | 5,242,880 bytes | 5,243,025 bytes |
| short-link-domain proof | 10,485,760 bytes | 10,485,905 bytes |
| trademark proof | 10,485,760 bytes | 10,485,905 bytes |
| MySQL encrypted-snapshot chunk | 10,485,760 bytes | 10,485,905 bytes |

The global protected-object plaintext ceiling is 10,485,760 bytes. Before
reading or allocation, reject a declared plaintext or ciphertext length above
the selected purpose bound, a complete envelope above its bound, any value that
cannot be represented in u32, and every checked-add overflow. A declared input
length must equal the bytes actually read. With no trusted declared length, a
bounded counting reader may consume at most the purpose limit plus one byte and
must reject the extra byte. Object-store reads first validate trusted metadata
and HEAD length, then read through an exact bounded stream; unbounded
`readAllBytes()` and allocation directly from an unvalidated header are
forbidden.

## MySQL encrypted-snapshot streaming contract

A database dump is never represented as one in-memory envelope. It is a
strictly ordered sequence of independently authenticated YCSE/v1 envelopes
whose purpose is `mysql-encrypted-snapshot-chunk`. Each chunk uses a fresh DEK,
data nonce, and wrap nonce and is subject to the 10,485,760-byte plaintext and
10,485,905-byte complete-envelope bound above.

The six semantic-context values for every snapshot chunk are fixed as follows:

1. purpose: `mysql-encrypted-snapshot-chunk`;
2. logical owner/package: `crypto-storage-bootstrap`;
3. logical object class: `mysql-snapshot`;
4. content role: `dump-chunk`;
5. tenant scope: `global`;
6. immutable resource identity: canonical UTF-8
   `migration_set_id=<id>;snapshot_id=<id>;chunk_index=<u64>;final=<0|1>`.

`migration_set_id` and `snapshot_id` use canonical ASCII
`[a-z0-9][a-z0-9._-]{0,63}`. Chunk indexes start at zero, are contiguous, and
never repeat. Exactly one nonempty chunk has `final=1`, it is the last chunk,
and no chunk follows it. A producer may retain at most one bounded plaintext
chunk plus one-byte lookahead; it may not read the complete dump into memory.

The canonical hard snapshot limits are:

| Snapshot quantity | Maximum |
| --- | ---: |
| total plaintext dump bytes | 1,099,511,627,776 bytes (1 TiB) |
| chunk count | 104,858 |
| total complete-envelope bytes | 1,099,526,832,186 bytes |
| canonical snapshot manifest bytes | 33,554,432 bytes |

Creation stops and fails before writing a byte beyond any per-chunk or total
limit. It streams the fixed-argument MySQL dump through bounded chunking,
encrypts each chunk before persistence, and tracks totals with checked u64
arithmetic. Restore first validates the bounded manifest and the complete
ordered chunk inventory, then fetches, authenticates, and streams one chunk at
a time into the MySQL client. It never uses whole-dump `readAllBytes()`, never
allocates from an untrusted manifest/header length, and never exposes a
plaintext temporary dump. Missing, duplicate, reordered, truncated, extra,
post-final, wrong-context, digest-mismatched, or over-limit chunks fail before
recovery can be marked complete.

The admitted `ycs-encrypted-snapshot/v1` manifest binds `migration_set_id`,
`snapshot_id`, recovery key reference, exact ordered chunk count, and one record
per chunk containing index, final flag, plaintext size, complete-envelope size,
and SHA-256 digest. It also binds checked total plaintext and ciphertext sizes.
The manifest is paired atomically with its writer-fence manifest under
DR-P03-008's canonical pair digest; an independently valid chunk list or
manifest cannot authorize migration by itself.

## Required tests

- Byte-exact golden encode/decode and maximum-overhead assertions.
- One mutation for every fixed-header field and every provider/key-reference
  byte class, plus nonce, wrapped-DEK, ciphertext, semantic-context, and domain
  prefix mutations.
- Database 110/111-byte and 255/256-byte envelope boundaries.
- Each object purpose at its exact plaintext/envelope boundary and one byte
  over, declared-versus-actual mismatch, missing declared length, u32 overflow,
  oversized provider/key-reference/context lengths, truncation, and trailing
  input.
- Snapshot chunk exact/over-limit cases; multi-chunk streaming; exact hard
  total boundary; manifest/chunk-count bounds; and missing, duplicate,
  reordered, truncated, extra, post-final, wrong-context, digest/size/total
  mismatch rejection without whole-dump allocation or recovery completion.
- Data and wrap nonce uniqueness tests remain separate. `KeyProtectionPort.wrap`
  is the only caller-visible wrap operation; its production adapter alone
  performs persistent admission, then generates the wrap nonce, invokes the
  provider and returns the immutable wrapped result. The codec cannot reserve,
  supply a wrap nonce or retry. DR-P03-002 governs the per-key ceiling.
