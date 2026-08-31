# Phase 03 Entry Evidence

Review subject commit: `20d427a61159354aa018300769209279466db581`
Evidence recorder identity: /root/phase3_research
Tool boundary: independent read-only inspection of the committed subject and local deterministic command execution; only ENTRY-EVIDENCE.md and ENTRY-REVIEW.md are written after capture; no implementation edit, service mutation, download, secret access, or claimed SoftHSM execution
Identity assurance: orchestration provenance only; deterministic commands, transcript digest, mandatory flag and separate main-agent reproduction are the acceptance boundary; no cryptographic process-identity claim is made

## Transcript 01 — Git subject and clean tree

Command:

```sh
set -e
expected=20d427a61159354aa018300769209279466db581
subject=$(git rev-parse HEAD)
test "${#subject}" -eq 40
test "$subject" = "$expected"
test -z "$(git status --porcelain)"
tree=$(git rev-parse HEAD^{tree})
printf 'subject=%s\ntree=%s\nworktree=clean\n' "$subject" "$tree"
```

Raw stdout:

```text
subject=20d427a61159354aa018300769209279466db581
tree=e5d1cb9574dc390ebf45757a9b87add0575e99ce
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
planning_validator_self_test=PASS positive=design_ui+production_ui+phase_entry_design+mandatory_entry_evidence_binding+open_current_todo+deterministic_plan_graph+planned_artifact_dependency+shared_file_dependency+rg_alternation_canary negative=entry_evidence_digest_mismatch,entry_evidence_flag_omitted,plan_rg_escaped_alternation,plan_unknown_dependency,plan_self_dependency,plan_cycle,plan_same_wave_dependency,plan_same_wave_file_overlap,plan_artifact_dependency_missing,plan_shared_file_dependency_missing,plan_bad_yaml,plan_id_mismatch,missing_stage,missing_artifact,foreign_obligation,missing_selector,ui_placeholder,free_text_test_matrix,missing_atomic_row,missing_atomic_link,wrong_behavior,wrong_requirement,wrong_catalog_test,current_todo_missing_owned,current_todo_prechecked,dependency_todo_unchecked,prototype_as_production,missing_pw_id,missing_case_id,missing_obl_id,metadata_token_boundary,unrelated_smoke,no_goto,no_action_or_assertion,dead_component_without_browser_closure,execution_missing,execution_fail,execution_checksum,fake_react_txt,fake_playwright_txt,comment_only_react,string_only_react,schema_conflict,template_path_regression
```

Exit status: `0`

The positive list explicitly contains `mandatory_entry_evidence_binding` and `rg_alternation_canary`; the negative list explicitly contains `entry_evidence_flag_omitted`, `entry_evidence_digest_mismatch`, and `plan_rg_escaped_alternation`.

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
set -e
docker image inspect mysql@sha256:b3b90af2a6552ae30c266fdb7d5dd55f3afb72404bb78d37fe8a23eb857fd3fb --format 'mysql={{index .RepoDigests 0}} id={{.Id}}'
docker image inspect minio/minio@sha256:14cea493d9a34af32f524e538b8346cf79f3321eff8e708c1e2960462bd8936e --format 'minio={{index .RepoDigests 0}} id={{.Id}}'
```

Raw stdout:

```text
mysql=mysql@sha256:b3b90af2a6552ae30c266fdb7d5dd55f3afb72404bb78d37fe8a23eb857fd3fb id=sha256:b3b90af2a6552ae30c266fdb7d5dd55f3afb72404bb78d37fe8a23eb857fd3fb
minio=minio/minio@sha256:14cea493d9a34af32f524e538b8346cf79f3321eff8e708c1e2960462bd8936e id=sha256:14cea493d9a34af32f524e538b8346cf79f3321eff8e708c1e2960462bd8936e
```

Exit status: `0`

## Transcript 08 — SoftHSM execution-prerequisite probe

This is an availability probe, not a SoftHSM conformance PASS. The absent runtime remains an OPEN execution prerequisite owned by Plan 03-03.

Command:

```sh
set -e
if command -v softhsm2-util >/dev/null 2>&1; then
  printf 'softhsm_runtime=present path=%s execution_prerequisite=REVIEW_REQUIRED\n' "$(command -v softhsm2-util)"
  exit 1
else
  printf 'softhsm_runtime=absent execution_prerequisite=OPEN owner=03-03\n'
fi
```

Raw stdout:

```text
softhsm_runtime=absent execution_prerequisite=OPEN owner=03-03
```

Exit status: `0`

## Transcript 09 — Test matrix, TODO and schema-claim audit

Command:

```sh
/usr/bin/env ruby -I.planning/tools -rplanning-validator-support -rset -e 'root=Dir.pwd; phase=".planning/phases/03-crypto-storage-bootstrap"; errors=[]; records=PlanningValidatorSupport.catalog_records(".planning/PRD-OBLIGATIONS.md",errors).select{|r| r.owner=="crypto-storage-bootstrap"}; rows=PlanningValidatorSupport.markdown_table("#{phase}/TEST-MATRIX.md",PlanningValidatorSupport::UI_TEST_MATRIX_HEADERS,errors,"phase03_test_matrix"); by=records.to_h{|r|[r.id,r]}; rows.each{|row| id,req,behavior,test,pw,page,tid,_case,_desc,command,evidence=row.map{|v|v.delete("`").strip}; rec=by[id]; errors << "unknown #{id}" unless rec; next unless rec; errors << "requirement #{id}" unless req.scan(PlanningValidatorSupport::REQUIREMENT_ID).uniq.sort==rec.requirements.sort; errors << "behavior #{id}" unless behavior==rec.behavior; errors << "test #{id}" unless test==rec.test_reference; errors << "evidence #{id}" unless evidence==rec.evidence; errors << "ui #{id}" unless [pw,page,tid]==["-","-","-"]; errors << "command #{id}" if command.empty?}; errors << "row set" unless rows.map{|r|r[0]}.to_set==by.keys.to_set; todo=File.read("#{phase}/TODO.md"); open=todo.scan(/^\s*- \[ \]/).length; closed=todo.scan(/^\s*- \[[xX]\]/).length; counts=records.to_h{|r|[r.id,todo.lines.count{|line| line.match?(/^\s*- \[ \].*#{Regexp.escape(r.id)}/)}]}; errors << "todo" unless open==22 && closed==0 && counts.values.all?{|n|n==1}; claims=[]; registry=PlanningValidatorSupport.validate_schema_registry(".planning/SCHEMA-OWNERSHIP.md",PlanningValidatorSupport.roadmap_packages(".planning/ROADMAP.md",claims).values,claims); PlanningValidatorSupport.validate_phase_schema_claims(root,File.join(root,phase),"crypto-storage-bootstrap",registry,claims); errors.concat(claims); abort(errors.join("\n")) unless errors.empty?; puts "entry_artifact_audit=PASS matrix_rows=#{rows.length} obligations=#{records.length} ui_stage=not-applicable todo_open=#{open} todo_checked=#{closed} todo_owned_once=#{counts.values.count(1)} schema_claims=declared"'
```

Raw stdout:

```text
entry_artifact_audit=PASS matrix_rows=4 obligations=4 ui_stage=not-applicable todo_open=22 todo_checked=0 todo_owned_once=4 schema_claims=declared
```

Exit status: `0`

## Transcript 10 — V1 third-party-risk schema and Java fail-fast audit

Command:

```sh
/usr/bin/env ruby -e 'schema=File.read("core/src/main/resources/db/migration/V1__init_schema.sql"); block=schema[/CREATE TABLE third_party_risk_check_logs\s*\(.*?\) ENGINE=.*?;/m] or abort("missing third_party_risk_check_logs DDL"); abort("missing mobile_hash CHAR(64)") unless block.match?(/mobile_hash\s+CHAR\(64\)\s+NOT NULL/); abort("missing idx_mobile_hash") unless block.match?(/KEY idx_mobile_hash \(mobile_hash\)/); java=Dir["core/src/main/java/**/*.java"].map{|p|[p,File.read(p)]}; hits=java.select{|_,body| body.match?(/third_party_risk_check_logs|ThirdPartyRiskCheckLog/)}; abort("unexpected Java reader/writer: #{hits.map(&:first).join(",")}") unless hits.empty?; decisions=File.read(".planning/phases/03-crypto-storage-bootstrap/03-DECISIONS.md"); abort("missing MIGRATABLE_SCHEMA_ONLY classification") unless decisions.include?("`third_party_risk_check_logs.mobile_hash` is a V1 `CHAR(64)` indexed `MIGRATABLE_SCHEMA_ONLY` surface"); puts "third_party_risk_surface=PASS ddl_table=present mobile_hash=CHAR(64) index=idx_mobile_hash java_reader_writer=0 classification=MIGRATABLE_SCHEMA_ONLY"'
```

Raw stdout:

```text
third_party_risk_surface=PASS ddl_table=present mobile_hash=CHAR(64) index=idx_mobile_hash java_reader_writer=0 classification=MIGRATABLE_SCHEMA_ONLY
```

Exit status: `0`

This fail-fast check requires the V1 table, exact indexed `CHAR(64)` field, zero current Java table/entity reader-writer matches, and the explicit schema-only migration classification to be true together.

## Transcript 11 — Mandatory entry-evidence flag omission negative test

Command:

```sh
set -e
if output=$(/usr/bin/env ruby .planning/tools/validate-phase-entry.rb --phase 03 --package crypto-storage-bootstrap --obligations .planning/PRD-OBLIGATIONS.md --entry-review .planning/phases/03-crypto-storage-bootstrap/ENTRY-REVIEW.md 2>&1); then
  printf 'unexpected_zero_exit\n'
  exit 1
else
  result_code=$?
fi
test "$result_code" -ne 0
case "$output" in
  *OPTION_ENTRY_EVIDENCE_REQUIRED*) ;;
  *) printf '%s\n' "$output"; exit 1 ;;
esac
printf 'observed_exit=%s\n%s\n' "$result_code" "$output"
```

Raw combined output:

```text
observed_exit=1
phase_entry=BLOCKED errors=3
- OPTION_ENTRY_EVIDENCE_REQUIRED: existing=/Users/laosanzheong/Documents/codebases/ycsopen-sms/.planning/phases/03-crypto-storage-bootstrap/ENTRY-EVIDENCE.md
- ENTRY_REVIEW_BLOCKER: criterion=ENTRY-03-12-COMPLETE-GATE
- ENTRY_REVIEW_FINAL_VERDICT_MISSING: /Users/laosanzheong/Documents/codebases/ycsopen-sms/.planning/phases/03-crypto-storage-bootstrap/ENTRY-REVIEW.md
```

The wrapper exited successfully only after proving the validator itself returned nonzero and emitted `OPTION_ENTRY_EVIDENCE_REQUIRED`.

Exit status: `0`
