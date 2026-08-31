# Phase 03 Entry Evidence

Review subject commit: `f3749bd41b37f622b2f809ee2af8f2a2e6ff4218`
Evidence recorder identity: /root/phase3_validation_map
Tool boundary: independent read-only inspection of the committed subject and local command execution; only ENTRY-EVIDENCE.md and ENTRY-REVIEW.md are written after capture; no implementation edit, service mutation, download, secret access, or claimed SoftHSM execution

## Transcript 01 — Git subject and tree

Command:

```sh
subject=$(git rev-parse HEAD); tree=$(git rev-parse HEAD^{tree}); test "$subject" = f3749bd41b37f622b2f809ee2af8f2a2e6ff4218; test -z "$(git status --porcelain)"; printf 'subject=%s\ntree=%s\nworktree=clean\n' "$subject" "$tree"
```

Raw stdout:

```text
subject=f3749bd41b37f622b2f809ee2af8f2a2e6ff4218
tree=8e0ea6dfd0d81a94bee419383e5d9105bfa810af
worktree=clean
```

Exit status: `0`

## Transcript 02 — Phase 1 live delivery attestation

Command:

```sh
/usr/bin/env ruby .planning/tools/validate-delivery-attestation.rb --phase 01 --summary .planning/phases/01-engineering-verification-foundation/SUMMARY.md --evidence-manifest .planning/phases/01-engineering-verification-foundation/EVIDENCE/evidence-manifest.json --require-pr-check-pass
```

Raw stdout:

```text
DELIVERY_ATTESTATION PASS phase=01 tag=refs/tags/ycsopen-sms/phase-01/delivery
```

Exit status: `0`

## Transcript 03 — Owner exact-four obligation validator

Command:

```sh
/usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --owner crypto-storage-bootstrap --assert-unique --assert-traced
```

Raw stdout:

```text
validation=PASS count=522 fields=9 requirements=108/108 unknown_requirements=0 duplicate_record_requirement_links=0 owners=56/56 unknown_owners=0 duplicate_obligation_ids=0 duplicate_test_ids=0 duplicate_evidence_targets=0 element_refs=195 invalid_element_refs=0 ui_owners=42 non_ui_owner_refs=0 selected=4 projects=19
```

Exit status: `0`

## Transcript 04 — Planning-validator destructive self-test

Command:

```sh
/usr/bin/env ruby .planning/tools/test-planning-validators.rb
```

Raw stdout:

```text
planning_validator_self_test=PASS positive=design_ui+production_ui+phase_entry_design+entry_evidence_binding+open_current_todo+deterministic_plan_graph+planned_artifact_dependency+shared_file_dependency negative=entry_evidence_digest_mismatch,plan_unknown_dependency,plan_self_dependency,plan_cycle,plan_same_wave_dependency,plan_same_wave_file_overlap,plan_artifact_dependency_missing,plan_shared_file_dependency_missing,plan_bad_yaml,plan_id_mismatch,missing_stage,missing_artifact,foreign_obligation,missing_selector,ui_placeholder,free_text_test_matrix,missing_atomic_row,missing_atomic_link,wrong_behavior,wrong_requirement,wrong_catalog_test,current_todo_missing_owned,current_todo_prechecked,dependency_todo_unchecked,prototype_as_production,missing_pw_id,missing_case_id,missing_obl_id,metadata_token_boundary,unrelated_smoke,no_goto,no_action_or_assertion,dead_component_without_browser_closure,execution_missing,execution_fail,execution_checksum,fake_react_txt,fake_playwright_txt,comment_only_react,string_only_react,schema_conflict,template_path_regression
```

Exit status: `0`

## Transcript 05 — Current 30-plan PlanningValidatorSupport graph

Command:

```sh
/usr/bin/env ruby -I.planning/tools -rplanning-validator-support -ryaml -e 'paths=Dir[".planning/phases/03-crypto-storage-bootstrap/03-*-PLAN.md"].sort; errors=[]; PlanningValidatorSupport.validate_plans(paths, errors); abort(errors.join("\n")) unless errors.empty?; nodes=paths.map{|p| YAML.safe_load(File.read(p).split("---",3)[1], aliases: false)}; edges=nodes.sum{|n| Array(n["depends_on"]).length}; waves=nodes.map{|n| n["wave"]}.uniq.sort; puts "planning_validator_graph=PASS plans=#{paths.length} edges=#{edges} waves=#{waves.join(",")}"'
```

Raw stdout:

```text
planning_validator_graph=PASS plans=30 edges=79 waves=0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15
```

Exit status: `0`

## Transcript 06 — GSD plan-structure checks for all 30 plans

Command:

```sh
/usr/bin/env ruby -rjson -ropen3 -e 'tool="/Users/laosanzheong/.codex/gsd-core/bin/gsd-tools.cjs"; paths=Dir[".planning/phases/03-crypto-storage-bootstrap/03-*-PLAN.md"].sort; tasks=0; paths.each{|path| out,err,status=Open3.capture3("node",tool,"verify","plan-structure",path); abort("#{path}\n#{out}#{err}") unless status.success?; result=JSON.parse(out); abort("#{path}: #{result.inspect}") unless result["valid"] && result["errors"].empty? && result["warnings"].empty?; tasks += result["task_count"]; puts "#{File.basename(path)}=PASS tasks=#{result["task_count"]}"}; puts "gsd_plan_structure=PASS plans=#{paths.length} tasks=#{tasks} errors=0 warnings=0"'
```

Raw stdout:

```text
03-01-PLAN.md=PASS tasks=2
03-02-PLAN.md=PASS tasks=1
03-03-PLAN.md=PASS tasks=2
03-04-PLAN.md=PASS tasks=1
03-05-PLAN.md=PASS tasks=1
03-06-PLAN.md=PASS tasks=1
03-07-PLAN.md=PASS tasks=2
03-08-PLAN.md=PASS tasks=1
03-09-PLAN.md=PASS tasks=1
03-10-PLAN.md=PASS tasks=2
03-11-PLAN.md=PASS tasks=1
03-12-PLAN.md=PASS tasks=1
03-13-PLAN.md=PASS tasks=1
03-14-PLAN.md=PASS tasks=2
03-15-PLAN.md=PASS tasks=1
03-16-PLAN.md=PASS tasks=1
03-17-PLAN.md=PASS tasks=1
03-18-PLAN.md=PASS tasks=1
03-19-PLAN.md=PASS tasks=2
03-20-PLAN.md=PASS tasks=1
03-21-PLAN.md=PASS tasks=1
03-22-PLAN.md=PASS tasks=2
03-23-PLAN.md=PASS tasks=2
03-24-PLAN.md=PASS tasks=1
03-25-PLAN.md=PASS tasks=1
03-26-PLAN.md=PASS tasks=1
03-27-PLAN.md=PASS tasks=1
03-28-PLAN.md=PASS tasks=1
03-29-PLAN.md=PASS tasks=1
03-30-PLAN.md=PASS tasks=1
gsd_plan_structure=PASS plans=30 tasks=38 errors=0 warnings=0
```

Exit status: `0`

## Transcript 07 — Local MySQL and MinIO digest identities

Command:

```sh
docker image inspect mysql@sha256:b3b90af2a6552ae30c266fdb7d5dd55f3afb72404bb78d37fe8a23eb857fd3fb --format 'mysql={{index .RepoDigests 0}} id={{.Id}}' && docker image inspect minio/minio@sha256:14cea493d9a34af32f524e538b8346cf79f3321eff8e708c1e2960462bd8936e --format 'minio={{index .RepoDigests 0}} id={{.Id}}'
```

Raw stdout:

```text
mysql=mysql@sha256:b3b90af2a6552ae30c266fdb7d5dd55f3afb72404bb78d37fe8a23eb857fd3fb id=sha256:b3b90af2a6552ae30c266fdb7d5dd55f3afb72404bb78d37fe8a23eb857fd3fb
minio=minio/minio@sha256:14cea493d9a34af32f524e538b8346cf79f3321eff8e708c1e2960462bd8936e id=sha256:14cea493d9a34af32f524e538b8346cf79f3321eff8e708c1e2960462bd8936e
```

Exit status: `0`

## Transcript 08 — SoftHSM execution-prerequisite probe

This is an availability probe, not a SoftHSM conformance PASS. The absent runtime remains an open execution prerequisite owned by Plan 03-03.

Command:

```sh
if command -v softhsm2-util >/dev/null 2>&1; then printf 'softhsm_runtime=present path=%s\n' "$(command -v softhsm2-util)"; exit 1; else printf 'softhsm_runtime=absent execution_prerequisite=OPEN owner=03-03\n'; fi
```

Raw stdout:

```text
softhsm_runtime=absent execution_prerequisite=OPEN owner=03-03
```

Exit status: `0`

## Transcript 09 — Attempt-2 correction ownership extraction

Command:

```sh
rg -n 'MIGRATABLE_SCHEMA_ONLY|maximum three per purpose|fifteen per session|concurrent CAS races|milestone_name: YCSOpen SMS v1\.0' .planning/phases/03-crypto-storage-bootstrap/{03-DECISIONS.md,03-17-PLAN.md,03-27-PLAN.md} .planning/STATE.md
```

Raw stdout:

```text
.planning/phases/03-crypto-storage-bootstrap/03-DECISIONS.md:60:- **Migration:** the exact current legacy-index targets are `mobile_portability.mobile_hash`, `blacklist_entries.mobile_hash`, `third_party_risk_check_logs.mobile_hash`, `message_tasks.mobile_hash`, and `unsubscribe_records.mobile_hash`; protected mobile columns without a `mobile_hash` are field-encryption targets but not invented equality indexes. `third_party_risk_check_logs.mobile_hash` is a V1 `CHAR(64)` indexed `MIGRATABLE_SCHEMA_ONLY` surface: current Java has no reader/writer, but deployed databases may contain historical rows, so it remains in migration/scrub evidence and its writer fence must prove no unknown external writer. Each target advances `DISCOVERED -> BACKFILLED -> VERIFIED -> CUTOVER -> SCRUBBED -> COMPLETE`. Backfill inserts metadata idempotently; cutover requires a compatible deployed-writer fence; scrub replaces legacy `CHAR(64)` raw digests with non-queryable row-binding locators and atomically updates any locator-based metadata binding. Blacklist and portability targets must prove query hits before backfill, during dual-read compatibility, and after HMAC-only cutover on real MySQL. Concurrent legacy writes, checkpoint drift, duplicate key-version rows, or missing bindings block advancement and leave the earlier reader mode intact.
.planning/phases/03-crypto-storage-bootstrap/03-27-PLAN.md:60:  <action>No repository analog exists, so implement DR-P03-008 exactly with constructor-injected final collaborators and sanitized stable failures. Define closed schemas for `ycs-writer-fence/v1` and `ycs-encrypted-snapshot/v1`; both require identical canonical `migration_set_id`, environment, database-instance fingerprint, schema, Flyway-set digest, unsigned global sequence and `signer_key_version`, with bounded strings and no unknown fields. The writer role owns issued/expiry markers and unique compatible writer records. The snapshot role owns canonical snapshot ID, recovery key reference, completed marker, totals and an ordered array of at most 104858 chunk records, each containing zero-based index, exactly one terminal final flag, plaintext size at most 10485760, envelope size at most 10485905 and SHA-256 digest; enforce the 1 TiB plaintext, 1099526832186-byte ciphertext and 33554432-byte manifest bounds from `ENVELOPE-CONTRACT.md`. Read only bounded canonical regular files at explicit canonical paths and reject symlinks, duplicates and noncanonical JSON. Compute each canonical role digest and the exact DR-P03-008 pair digest. Verify the writer role signature over role byte `0x01` and the snapshot role signature over `0x02`, both committing the same pair digest and their role digest under the same configured signer version. Configure exactly one ACTIVE and explicit RETIRING Ed25519 X.509 fingerprints. Only ACTIVE may atomically CAS a higher global sequence with both role digests and pair digest; RETIRING may reverify only the exact accepted tuple. Test missing/duplicate/reordered/truncated/extra/post-final/size-total mismatch chunks, migration-set/subject/sequence/signer mismatch, cross-pair splice, individual replay, same-sequence digest change, simulated half-write, unknown/retired/revoked signer, rollout, old-anchor removal, compromise invalidation and recovery through one fresh higher-sequence pair. Add concurrent CAS races: identical tuples may produce one insert plus idempotent exact re-verifications; different higher-sequence or same-sequence/different-digest pairs have exactly one winner and every loser leaves the accepted tuple unchanged; no race can expose one role or half a pair. Every rejection or losing competitor asserts zero lease, checkpoint, event and business-table mutations.</action>
.planning/phases/03-crypto-storage-bootstrap/03-17-PLAN.md:55:  <action>No repository object-upload analog exists, so implement DR-P03-009 and `ENVELOPE-CONTRACT.md` using constructor injection, before-allocation bounded input handling, stable errors and MVC/service tests per `03-PATTERNS.md`. `POST /api/v1/console/tenants/registration-object-sessions` returns opaque session ID, one `regup_v1_` session-bound upload token and expiry marker. Generate a nonsecret lookup ID plus 32-byte CSPRNG secret, use only `OpaqueTokenDigestPort` purpose `REGISTRATION_UPLOAD` with tenant-draft/session binding, and store its ACTIVE key version plus 32-byte digest. Every upload verifies the stored ACTIVE/RETIRING version in constant time; it never calls `BlindIndexPort` or accepts an object-capability token. The same token may upload all five purposes sequentially and replace the current STAGED object for one purpose while that exact tenant-draft/session is OPEN; it never crosses session or tenant draft. After media/size validation and before encryption/store work, atomically reserve an admitted-attempt slot only while OPEN: maximum three per purpose and fifteen per session. Concurrent callers cannot exceed either bound; any provider/store/reconciliation failure after reservation burns the slot and stable HTTP 429 `REGISTRATION_UPLOAD_LIMIT_REACHED` is returned at the boundary. Explicit close, successful claim or expiry invalidates it. Upload accepts exactly one multipart part `file` plus `X-Registration-Upload-Token` and returns only `pobj_v1_*`, purpose and expiry. Enforce exact content limits and before-allocation checks for all five purposes; reconcile replaced objects by operation ID. Document routes, token format/purpose/version policy, repeat-use/admission scope, terminal invalidation, states, errors, limits, privacy and legacy rejection; tests compare docs to runtime constants and cover attempts 2/3 and session 14/15/16, concurrent reservations, burned post-reservation failure, ACTIVE/RETIRING rotation, revoked/unknown version and cross-domain rejection.</action>
.planning/phases/03-crypto-storage-bootstrap/03-17-PLAN.md:57:  <acceptance_criteria>One token sequentially uploads five purposes and replaces one only inside its OPEN binding; atomic admission never exceeds three attempts per purpose or fifteen per session even under concurrency/failure; claim/close/expiry/cross-session/cross-tenant-draft reuse fails; all exact size/envelope boundaries and documents match runtime; no endpoint accepts a URL, bucket, key or public/presigned reference.</acceptance_criteria>
.planning/STATE.md:4:milestone_name: YCSOpen SMS v1.0
```

Exit status: `0`

## Transcript 10 — Test matrix, TODO and schema-claim audit

Command:

```sh
/usr/bin/env ruby -I.planning/tools -rplanning-validator-support -rset -e 'root=Dir.pwd; phase=".planning/phases/03-crypto-storage-bootstrap"; errors=[]; records=PlanningValidatorSupport.catalog_records(".planning/PRD-OBLIGATIONS.md",errors).select{|r| r.owner=="crypto-storage-bootstrap"}; rows=PlanningValidatorSupport.markdown_table("#{phase}/TEST-MATRIX.md",PlanningValidatorSupport::UI_TEST_MATRIX_HEADERS,errors,"phase03_test_matrix"); by=records.to_h{|r|[r.id,r]}; rows.each{|row| id,req,behavior,test,pw,page,tid,_case,_desc,command,evidence=row.map{|v|v.delete("`").strip}; rec=by[id]; errors << "unknown #{id}" unless rec; next unless rec; errors << "requirement #{id}" unless req.scan(PlanningValidatorSupport::REQUIREMENT_ID).uniq.sort==rec.requirements.sort; errors << "behavior #{id}" unless behavior==rec.behavior; errors << "test #{id}" unless test==rec.test_reference; errors << "evidence #{id}" unless evidence==rec.evidence; errors << "ui #{id}" unless [pw,page,tid]==["-","-","-"]; errors << "command #{id}" if command.empty?}; errors << "row set" unless rows.map{|r|r[0]}.to_set==by.keys.to_set; todo=File.read("#{phase}/TODO.md"); open=todo.scan(/^\s*- \[ \]/).length; closed=todo.scan(/^\s*- \[[xX]\]/).length; counts=records.to_h{|r|[r.id,todo.lines.count{|line| line.match?(/^\s*- \[ \].*#{Regexp.escape(r.id)}/)}]}; errors << "todo" unless open==22 && closed==0 && counts.values.all?{|n|n==1}; claims=[]; registry=PlanningValidatorSupport.validate_schema_registry(".planning/SCHEMA-OWNERSHIP.md",PlanningValidatorSupport.roadmap_packages(".planning/ROADMAP.md",claims).values,claims); PlanningValidatorSupport.validate_phase_schema_claims(root,File.join(root,phase),"crypto-storage-bootstrap",registry,claims); errors.concat(claims); abort(errors.join("\n")) unless errors.empty?; puts "entry_artifact_audit=PASS matrix_rows=#{rows.length} obligations=#{records.length} ui_stage=not-applicable todo_open=#{open} todo_checked=#{closed} todo_owned_once=#{counts.values.count(1)} schema_claims=declared"'
```

Raw stdout:

```text
entry_artifact_audit=PASS matrix_rows=4 obligations=4 ui_stage=not-applicable todo_open=22 todo_checked=0 todo_owned_once=4 schema_claims=declared
```

Exit status: `0`
