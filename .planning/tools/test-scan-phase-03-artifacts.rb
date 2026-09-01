#!/usr/bin/env ruby
# frozen_string_literal: true

require "base64"
require "cgi"
require "fileutils"
require "json"
require "open3"
require "rbconfig"
require "tmpdir"
require_relative "scan-phase-03-artifacts"

SCANNER = File.expand_path("scan-phase-03-artifacts.rb", __dir__)
PHASE_DIR = ".planning/phases/03-crypto-storage-bootstrap"
GENERATED_ROOT = "core/target/phase03"
OUTPUT = File.join(GENERATED_ROOT, "leak-report.json")
MARKER = "ocap_v1_abcdefghijklmnopqrstuvwxyz0123456789"

def write(root, relative, content)
  path = File.join(root, relative)
  FileUtils.mkdir_p(File.dirname(path))
  File.binwrite(path, content)
end

def build_fixture(root)
  write(root, File.join(PHASE_DIR, "EVIDENCE/result.json"),
        JSON.generate("status" => "PASS", "digest" => "a" * 64))
  write(root, File.join(PHASE_DIR, "EVIDENCE/schema/ignored.json"),
        JSON.generate("description" => MARKER))
  write(root, File.join(GENERATED_ROOT, "results/test-report.json"),
        JSON.generate("status" => "PASS", "assertions" => 17))
end

def invoke(root, phase_dir: PHASE_DIR, generated_root: GENERATED_ROOT, output: OUTPUT)
  Open3.capture3(
    RbConfig.ruby, SCANNER,
    "--phase-dir", phase_dir,
    "--generated-root", generated_root,
    "--output", output,
    chdir: root
  )
end

def assert_run(name, root, success:, token:, phase_dir: PHASE_DIR, generated_root: GENERATED_ROOT, output: OUTPUT)
  stdout, stderr, status = invoke(
    root, phase_dir: phase_dir, generated_root: generated_root, output: output
  )
  combined = stdout + stderr
  unless status.success? == success
    abort "#{name}: expected success=#{success}, exit=#{status.exitstatus}:\n#{combined}"
  end
  abort "#{name}: missing #{token}:\n#{combined}" unless combined.include?(token)
  [combined, status]
end

def with_fixture(name)
  Dir.mktmpdir("phase03-artifact-scan-#{name}-") do |root|
    build_fixture(root)
    yield root
  end
end

cases = 0

2.times do |index|
  with_fixture("positive-#{index}") do |root|
    assert_run("positive #{index}", root, success: true, token: "phase03_artifact_scan=PASS")
    report = JSON.parse(File.binread(File.join(root, OUTPUT)))
    abort "positive #{index}: result digest invalid" unless report.fetch("result_digest") == Phase03ArtifactLeakScan.result_digest(report)
    abort "positive #{index}: target union invalid" unless report.fetch("targets").map { |row| row.fetch("id") } == %w[evidence reports]
    abort "positive #{index}: reader identity invalid" unless report.fetch("targets").all? { |row| row.fetch("reader_identity") == "phase03-artifact-scanner" }
    cases += 1
  end
end

variants = {
  "direct" => MARKER,
  "base64" => Base64.strict_encode64(MARKER),
  "base64url" => Base64.urlsafe_encode64(MARKER, padding: false),
  "hex" => MARKER.unpack1("H*"),
  "url" => CGI.escape(MARKER),
  "split-reader-boundary" => ("x" * 8_191) + MARKER
}

variants.each do |name, value|
  with_fixture(name) do |root|
    write(root, File.join(GENERATED_ROOT, "results/leak.txt"), value)
    combined, = assert_run(name, root, success: false, token: "prohibited_matches=1")
    report = File.binread(File.join(root, OUTPUT))
    abort "#{name}: raw marker escaped to output" if combined.include?(MARKER) || report.include?(MARKER)
    cases += 1
  end
end

with_fixture("evidence-leak") do |root|
  write(root, File.join(PHASE_DIR, "EVIDENCE/leak.json"), JSON.generate("value" => MARKER))
  assert_run("evidence leak", root, success: false, token: "target=evidence")
  cases += 1
end

with_fixture("self-output-excluded") do |root|
  write(root, OUTPUT, MARKER)
  assert_run("self output excluded", root, success: true, token: "phase03_artifact_scan=PASS")
  report = File.binread(File.join(root, OUTPUT))
  abort "self output excluded: previous scanner output was retained" if report.include?(MARKER)
  cases += 1
end

with_fixture("absolute") do |root|
  assert_run("absolute", root, success: false, token: "PHASE_DIR_PATH_INVALID",
             phase_dir: File.join(root, PHASE_DIR))
  cases += 1
end

with_fixture("traversal") do |root|
  assert_run("traversal", root, success: false, token: "OUTPUT_PATH_INVALID",
             output: File.join(GENERATED_ROOT, "../escaped.json"))
  cases += 1
end

with_fixture("output-outside") do |root|
  assert_run("output outside", root, success: false, token: "OUTPUT_OUTSIDE_GENERATED_ROOT",
             output: "outside/report.json")
  cases += 1
end

with_fixture("symlink-file") do |root|
  source = File.join(root, "outside.json")
  File.binwrite(source, "safe")
  File.symlink(source, File.join(root, GENERATED_ROOT, "results/link.json"))
  assert_run("symlink file", root, success: false, token: "REPORTS_SYMLINK_REJECTED")
  cases += 1
end

with_fixture("symlink-root") do |root|
  actual = File.join(root, "actual-generated")
  FileUtils.mv(File.join(root, GENERATED_ROOT), actual)
  File.symlink(actual, File.join(root, GENERATED_ROOT))
  assert_run("symlink root", root, success: false, token: "GENERATED_ROOT_SYMLINK_REJECTED")
  cases += 1
end

with_fixture("output-symlink") do |root|
  destination = File.join(root, "outside-output.json")
  File.binwrite(destination, "safe")
  File.symlink(destination, File.join(root, OUTPUT))
  assert_run("output symlink", root, success: false, token: "OUTPUT_TYPE_INVALID")
  cases += 1
end

with_fixture("unsupported-type") do |root|
  write(root, File.join(GENERATED_ROOT, "results/raw.bin"), "safe")
  assert_run("unsupported type", root, success: false, token: "REPORTS_FILE_TYPE_INVALID")
  cases += 1
end

with_fixture("oversized") do |root|
  path = File.join(root, GENERATED_ROOT, "results/oversized.log")
  File.open(path, "wb") { |file| file.truncate(Phase03ArtifactLeakScan::FILE_LIMIT + 1) }
  assert_run("oversized", root, success: false, token: "REPORTS_FILE_BOUND_INVALID")
  cases += 1
end

with_fixture("hardlink") do |root|
  source = File.join(root, GENERATED_ROOT, "results/test-report.json")
  File.link(source, File.join(root, GENERATED_ROOT, "results/hardlink.json"))
  assert_run("hardlink", root, success: false, token: "REPORTS_FILE_BOUND_INVALID")
  cases += 1
end

with_fixture("missing") do |root|
  FileUtils.rm_r(File.join(root, GENERATED_ROOT))
  assert_run("missing", root, success: false, token: "GENERATED_ROOT_TYPE_INVALID")
  cases += 1
end

with_fixture("empty-evidence") do |root|
  FileUtils.rm(File.join(root, PHASE_DIR, "EVIDENCE/result.json"))
  assert_run("empty evidence", root, success: false, token: "EVIDENCE_INPUTS_EMPTY")
  cases += 1
end

with_fixture("empty-reports") do |root|
  FileUtils.rm_r(File.join(root, GENERATED_ROOT, "results"))
  assert_run("empty reports", root, success: false, token: "REPORTS_INPUTS_EMPTY")
  cases += 1
end

puts "phase03_artifact_scan_tests=PASS cases=#{cases} positive=2 durable_canaries=0"
