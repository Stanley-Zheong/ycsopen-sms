#!/usr/bin/env ruby
# frozen_string_literal: true

require "json"
require "open3"
require "rbconfig"
require "tmpdir"
require_relative "phase01-chrome-entry-contract"
require_relative "produce-phase-01-chrome-entry"

def deep_copy(value)
  Marshal.load(Marshal.dump(value))
end

def assert(condition, message)
  abort(message) unless condition
end

def expect_error(errors, diagnostic, label)
  assert(errors.any? { |error| error.include?(diagnostic) }, "#{label} lacked #{diagnostic}: #{errors.inspect}")
end

contract = Phase01ChromeEntryContract
producer = File.join(__dir__, "produce-phase-01-chrome-entry.rb")
mutations = 0

Dir.mktmpdir("phase01-local-chrome-producer-test-") do |tmpdir|
  output_path = File.join(tmpdir, "local-chrome-entry.json")
  stdout, stderr, status = Open3.capture3(RbConfig.ruby, producer, "--output", output_path)
  assert(status.success?, "real local Chrome producer failed:\n#{stdout}#{stderr}")
  assert(stdout.include?("PHASE_01_LOCAL_CHROME_ENTRY PASS"), "producer omitted PASS marker: #{stdout}")
  assert(File.file?(output_path), "producer did not create #{output_path}")

  baseline = JSON.parse(File.read(output_path))
  errors = contract.validate_entry(baseline)
  assert(errors.empty?, "real entry evidence failed its contract: #{errors.inspect}")
  assert(baseline.dig("chrome", "executable_path") == contract::CHROME_PATH, "producer used a nonstandard Chrome path")
  assert(baseline.dig("chrome", "version_output").start_with?("Google Chrome "), "producer did not prove Google Chrome brand")
  assert(baseline.dig("synthetic_launch", "marker_observed") == true, "producer did not observe the synthetic marker")
  assert(baseline.dig("synthetic_launch", "exit_code") == 0, "producer did not record zero launch exit")
  assert(!File.read(output_path).match?(%r{/var/folders/|/private/tmp/}), "evidence leaked a random temporary profile path")

  run_mutation = lambda do |label, diagnostic, &mutation|
    document = deep_copy(baseline)
    mutation.call(document)
    expect_error(contract.validate_entry(document), diagnostic, label)
    mutations += 1
  end

  run_mutation.call("wrong path", "CHROME_PATH_INVALID") { |doc| doc["chrome"]["executable_path"] = "/Applications/Chromium.app/Contents/MacOS/Chromium" }
  run_mutation.call("wrong canonical path", "CHROME_CANONICAL_PATH_INVALID") { |doc| doc["chrome"]["canonical_path"] = "/tmp/chrome" }
  run_mutation.call("wrong brand", "VERSION_BRAND_INVALID") { |doc| doc["chrome"]["version_output"] = "Chromium 151.0.7922.174" }
  run_mutation.call("bad version grammar", "FULL_VERSION_INVALID") { |doc| doc["chrome"]["full_version"] = "151" }
  run_mutation.call("major mismatch", "MAJOR_VERSION_MISMATCH") { |doc| doc["chrome"]["major"] += 1 }
  run_mutation.call("launch failure", "LAUNCH_EXIT_INVALID") { |doc| doc["synthetic_launch"]["exit_code"] = 7 }
  run_mutation.call("launch status", "LAUNCH_STATUS_INVALID") { |doc| doc["synthetic_launch"]["status"] = "BLOCKED" }
  run_mutation.call("missing marker", "MARKER_NOT_OBSERVED") { |doc| doc["synthetic_launch"]["marker_observed"] = false }
  run_mutation.call("wrong marker", "MARKER_INVALID") { |doc| doc["synthetic_launch"]["marker"] = "WRONG" }
  run_mutation.call("profile mode", "PROFILE_MODE_INVALID") { |doc| doc["synthetic_launch"]["profile_mode"] = "0755" }
  run_mutation.call("profile owner", "PROFILE_OWNER_INVALID") { |doc| doc["synthetic_launch"]["profile_owner_uid"] += 1 }
  run_mutation.call("legacy field", "FORBIDDEN_FIELD") { |doc| doc["source"] = { "download_url" => "https://example.invalid/chrome.zip" } }
  run_mutation.call("driver field", "FORBIDDEN_FIELD") { |doc| doc["chromedriver"] = "unused" }
  run_mutation.call("dynamic command", "COMMAND_IDENTITY_INVALID") { |doc| doc["synthetic_launch"]["command_identity"] << "--remote-debugging-port=0" }

  # Installed version is a fact, never a pinned acceptance value.
  unpinned = deep_copy(baseline)
  unpinned["chrome"]["version_output"] = "Google Chrome 999.1.2.3"
  unpinned["chrome"]["full_version"] = "999.1.2.3"
  unpinned["chrome"]["major"] = 999
  version_errors = contract.validate_entry(unpinned).select { |error| error.include?("VERSION") || error.include?("MAJOR") }
  assert(version_errors.empty?, "contract pins a Chrome version: #{version_errors.inspect}")

  Dir.mktmpdir("phase01-path-fixtures-") do |path_tmp|
    missing = File.join(path_tmp, "missing")
    expect_error(contract.inspect_regular_executable(missing), "CHROME_MISSING", "missing executable")

    directory = File.join(path_tmp, "directory")
    Dir.mkdir(directory)
    expect_error(contract.inspect_regular_executable(directory), "CHROME_NOT_REGULAR", "directory executable")

    non_executable = File.join(path_tmp, "non-executable")
    File.write(non_executable, "fixture")
    File.chmod(0o600, non_executable)
    expect_error(contract.inspect_regular_executable(non_executable), "CHROME_NOT_EXECUTABLE", "non-executable file")

    executable = File.join(path_tmp, "executable")
    File.write(executable, "#!/bin/sh\nexit 0\n")
    File.chmod(0o700, executable)
    symlink = File.join(path_tmp, "symlink")
    File.symlink(executable, symlink)
    expect_error(contract.inspect_regular_executable(symlink), "CHROME_SYMLINK", "symlink executable")
  end

  timeout_result = Phase01LocalChromeEntryProducer.capture_bounded(
    [RbConfig.ruby, "-e", "sleep 2"], timeout_seconds: 0.05, output_limit: 1024
  )
  assert(timeout_result.fetch(:timed_out), "bounded runner did not report timeout")
  assert(!timeout_result.fetch(:success), "bounded runner treated timeout as success")

  huge_result = Phase01LocalChromeEntryProducer.capture_bounded(
    [RbConfig.ruby, "-e", "STDOUT.write('x' * 4096)"], timeout_seconds: 2, output_limit: 512
  )
  assert(huge_result.fetch(:stdout).bytesize <= 512, "bounded runner retained unbounded stdout")

  %w[produce-phase-01-chrome-entry.rb bootstrap-phase-01.rb].each do |name|
    body = File.read(File.join(__dir__, name))
    forbidden = %w[ChromeDriver WebDriver chrome-for-testing browser-source-admission browser-source-entry-attestation]
    hits = forbidden.select { |token| body.downcase.include?(token.downcase) }
    assert(hits.empty?, "#{name} retains legacy Gate D tokens: #{hits.join(', ')}")
  end
end

puts "local_chrome_producer_self_test=PASS mutations=#{mutations} fixed_path=#{Phase01ChromeEntryContract::CHROME_PATH}"
