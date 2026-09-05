#!/usr/bin/env ruby
# frozen_string_literal: true

require "digest"
require "fileutils"
require "json"
require "open3"
require "optparse"
require "pathname"
require "tmpdir"
require "uri"
require_relative "phase01-chrome-entry-contract"

module Phase01LocalChromeEntryProducer
  DEFAULT_OUTPUT = ".planning/phases/01-engineering-verification-foundation/EVIDENCE/local-chrome-entry.json"
  VERSION_TIMEOUT_SECONDS = 10
  LAUNCH_TIMEOUT_SECONDS = 30
  OUTPUT_LIMIT = 1_048_576

  module_function

  def capture_bounded(argv, timeout_seconds:, output_limit:, success_marker: nil)
    stdout_data = +""
    stderr_data = +""
    status = nil
    timed_out = false
    marker_observed = false
    stopped_after_marker = false

    Open3.popen3(*argv, pgroup: true) do |stdin, stdout, stderr, wait_thread|
      stdin.close
      stdout_reader = Thread.new do
        drain_bounded(stdout, stdout_data, output_limit) do |chunk|
          marker_observed ||= success_marker && chunk.include?(success_marker)
        end
      end
      stderr_reader = Thread.new { drain_bounded(stderr, stderr_data, output_limit) }

      deadline = Process.clock_gettime(Process::CLOCK_MONOTONIC) + timeout_seconds
      until wait_thread.join(0.05)
        if success_marker && (marker_observed || stdout_data.include?(success_marker))
          stopped_after_marker = true
          terminate_process_group(wait_thread.pid)
          wait_thread.join(2)
          kill_process_group(wait_thread.pid) if wait_thread.alive?
          wait_thread.join
          break
        end
        next if Process.clock_gettime(Process::CLOCK_MONOTONIC) < deadline

        timed_out = true
        terminate_process_group(wait_thread.pid)
        wait_thread.join(2)
        kill_process_group(wait_thread.pid) if wait_thread.alive?
        wait_thread.join
        break
      end
      status = wait_thread.value
      stdout_reader.join
      stderr_reader.join
    end

    {
      success: !timed_out && status&.success? == true && (!success_marker || marker_observed || stdout_data.include?(success_marker)),
      exit_code: status&.exitstatus,
      timed_out: timed_out,
      stopped_after_marker: stopped_after_marker,
      stdout: stdout_data,
      stderr: stderr_data
    }
  rescue SystemCallError => error
    {
      success: false,
      exit_code: nil,
      timed_out: false,
      stopped_after_marker: false,
      stdout: stdout_data,
      stderr: "#{error.class}: #{error.message}".byteslice(0, output_limit)
    }
  end

  def drain_bounded(io, destination, limit)
    loop do
      chunk = io.readpartial(16_384)
      yield chunk if block_given?
      remaining = limit - destination.bytesize
      destination << chunk.byteslice(0, remaining) if remaining.positive?
    end
  rescue EOFError, IOError
    nil
  end

  def terminate_process_group(pid)
    Process.kill("TERM", -pid)
  rescue Errno::ESRCH, Errno::EPERM
    nil
  end

  def kill_process_group(pid)
    Process.kill("KILL", -pid)
  rescue Errno::ESRCH, Errno::EPERM
    nil
  end

  def produce
    contract = Phase01ChromeEntryContract
    chrome_facts, errors = contract.executable_facts
    return [nil, errors] unless errors.empty?

    version_result = capture_bounded(
      [contract::CHROME_PATH, "--version"],
      timeout_seconds: VERSION_TIMEOUT_SECONDS,
      output_limit: 4096
    )
    errors << "VERSION_COMMAND_TIMEOUT" if version_result[:timed_out]
    errors << "VERSION_COMMAND_FAILED: exit=#{version_result[:exit_code].inspect}" unless version_result[:success]
    version_output = version_result[:stdout].strip
    match = version_output.match(contract::VERSION_PATTERN)
    errors << "VERSION_BRAND_INVALID: #{version_output.inspect}" unless match
    return [nil, errors] unless errors.empty?

    full_version = version_output.delete_prefix("Google Chrome ")
    launch_facts = nil
    Dir.mktmpdir("ycsopen-sms-phase01-local-chrome-") do |run_dir|
      File.chmod(0o700, run_dir)
      profile_dir = File.join(run_dir, "profile")
      Dir.mkdir(profile_dir, 0o700)
      synthetic_page = File.join(run_dir, "entry-probe.html")
      File.write(
        synthetic_page,
        "<!doctype html><html><head><meta charset=\"utf-8\"></head><body><main id=\"phase01-marker\">#{contract::MARKER}</main></body></html>\n",
        mode: "w",
        perm: 0o600
      )
      File.chmod(0o600, synthetic_page)

      page_uri = URI::Generic.build(scheme: "file", path: synthetic_page).to_s
      argv = [
        contract::CHROME_PATH,
        "--headless=new",
        "--disable-background-networking",
        "--disable-component-update",
        "--disable-default-apps",
        "--disable-sync",
        "--metrics-recording-only",
        "--host-resolver-rules=MAP * ~NOTFOUND",
        "--no-first-run",
        "--no-default-browser-check",
        "--user-data-dir=#{profile_dir}",
        "--dump-dom",
        page_uri
      ]
      launch_result = capture_bounded(
        argv,
        timeout_seconds: LAUNCH_TIMEOUT_SECONDS,
        output_limit: OUTPUT_LIMIT,
        success_marker: contract::MARKER
      )
      marker_observed = launch_result[:stdout].include?(contract::MARKER)
      errors << "HEADLESS_LAUNCH_TIMEOUT" if launch_result[:timed_out]
      errors << "HEADLESS_LAUNCH_FAILED: exit=#{launch_result[:exit_code].inspect}" unless launch_result[:success]
      errors << "HEADLESS_MARKER_MISSING" unless marker_observed

      profile_stat = File.lstat(profile_dir)
      page_stat = File.lstat(synthetic_page)
      launch_facts = {
        "status" => errors.empty? ? "PASS" : "BLOCKED",
        "exit_code" => launch_result[:exit_code],
        "timed_out" => launch_result[:timed_out],
        "completion_mode" => launch_result[:stopped_after_marker] ? "marker-observed-graceful-stop" : "natural-exit",
        "marker" => contract::MARKER,
        "marker_observed" => marker_observed,
        "profile_mode" => format("%04o", profile_stat.mode & 0o7777),
        "profile_owner_uid" => profile_stat.uid,
        "current_uid" => Process.uid,
        "synthetic_page_mode" => format("%04o", page_stat.mode & 0o7777),
        "command_identity" => contract::EXPECTED_COMMAND_IDENTITY,
        "stdout_sha256" => Digest::SHA256.hexdigest(launch_result[:stdout])
      }
    end
    return [nil, errors] unless errors.empty?

    document = {
      "schema_version" => contract::SCHEMA_VERSION,
      "phase" => contract::PHASE,
      "criterion_id" => contract::CRITERION_ID,
      "status" => "PASS",
      "runner" => {
        "id" => contract::RUNNER_ID,
        "implementation" => contract::RUNNER_IMPLEMENTATION,
        "argv_policy" => "fixed-code-owned",
        "shell" => false,
        "network_access" => false
      },
      "chrome" => chrome_facts.merge(
        "version_argv" => ["<standard-google-chrome>", "--version"],
        "version_output" => version_output,
        "brand" => "Google Chrome",
        "full_version" => full_version,
        "major" => match[1].to_i
      ),
      "synthetic_launch" => launch_facts
    }
    contract_errors = contract.validate_entry(document)
    errors.concat(contract_errors)
    [errors.empty? ? document : nil, errors]
  end

  def write_atomically(path, document)
    destination = Pathname(path)
    parent = destination.dirname
    FileUtils.mkdir_p(parent, mode: 0o755)
    raise "OUTPUT_PARENT_UNSAFE" if parent.lstat.symlink?
    raise "OUTPUT_PATH_UNSAFE" if destination.exist? && destination.lstat.symlink?

    temp_path = parent.join(".#{destination.basename}.tmp-#{Process.pid}")
    begin
      File.open(temp_path, File::WRONLY | File::CREAT | File::EXCL, 0o600) do |file|
        file.write(JSON.pretty_generate(document))
        file.write("\n")
        file.flush
        file.fsync
      end
      File.chmod(0o644, temp_path)
      File.rename(temp_path, destination)
    ensure
      FileUtils.rm_f(temp_path) if temp_path.exist?
    end
  end

  def run(argv)
    options = { output: DEFAULT_OUTPUT }
    parser = OptionParser.new do |option_parser|
      option_parser.banner = "Usage: produce-phase-01-chrome-entry.rb [--output PATH]"
      option_parser.on("--output PATH") { |value| options[:output] = value }
    end
    parser.parse!(argv)
    raise OptionParser::InvalidOption, argv.join(" ") unless argv.empty?

    document, errors = produce
    unless errors.empty?
      warn "PHASE_01_LOCAL_CHROME_ENTRY BLOCKED"
      errors.each { |error| warn "- #{error}" }
      return 1
    end

    write_atomically(options[:output], document)
    puts "PHASE_01_LOCAL_CHROME_ENTRY PASS"
    puts "path=#{Phase01ChromeEntryContract::CHROME_PATH}"
    puts "version=#{document.dig('chrome', 'full_version')}"
    puts "marker=#{document.dig('synthetic_launch', 'marker')}"
    puts "output=#{options[:output]}"
    0
  rescue OptionParser::ParseError, RuntimeError => error
    warn "PHASE_01_LOCAL_CHROME_ENTRY BLOCKED"
    warn "- #{error.message}"
    1
  end
end

exit Phase01LocalChromeEntryProducer.run(ARGV) if $PROGRAM_NAME == __FILE__
