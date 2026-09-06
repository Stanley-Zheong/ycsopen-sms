# frozen_string_literal: true

require "digest"

module Phase01ChromeEntryContract
  PHASE = "01-engineering-verification-foundation"
  SCHEMA_VERSION = "phase01-local-chrome-entry-v1"
  CRITERION_ID = "ENTRY-LOCAL-CHROME"
  CHROME_PATH = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
  RUNNER_ID = "phase01-local-chrome-entry-producer-v1"
  RUNNER_IMPLEMENTATION = ".planning/tools/produce-phase-01-chrome-entry.rb"
  MARKER = "YCSOPEN_SMS_PHASE01_LOCAL_CHROME_OK"
  VERSION_PATTERN = /\AGoogle Chrome (\d+)\.\d+\.\d+\.\d+\z/
  FULL_VERSION_PATTERN = /\A(\d+)\.\d+\.\d+\.\d+\z/
  SHA256_PATTERN = /\A[0-9a-f]{64}\z/
  EXPECTED_COMMAND_IDENTITY = [
    "<standard-google-chrome>",
    "--headless=new",
    "--disable-background-networking",
    "--disable-component-update",
    "--disable-default-apps",
    "--disable-sync",
    "--metrics-recording-only",
    "--host-resolver-rules=MAP * ~NOTFOUND",
    "--no-first-run",
    "--no-default-browser-check",
    "--user-data-dir=<isolated-0700-profile>",
    "--dump-dom",
    "file://<synthetic-local-page>"
  ].freeze
  REVIEW_CRITERIA = %w[
    ENTRY-00-WAVE0-OWNERSHIP
    ENTRY-01-LOCAL-CHROME-PATH
    ENTRY-02-VERSION-BRAND
    ENTRY-03-HEADLESS-SYNTHETIC-LAUNCH
    ENTRY-04-NO-LEGACY-CHAIN
    ENTRY-05-PLAN-SET-13
    ENTRY-06-REVIEWER-INDEPENDENCE
    ENTRY-07-BOOTSTRAP
  ].freeze
  FORBIDDEN_KEYS = %w[
    admission archive attestation browser_source cache chromedriver download source
    driver matrix platform playwright probe provider secret tunnel version_pair
    vm webdriver
  ].freeze

  module_function

  def validate_entry(document)
    errors = []
    exact_hash(document, %w[schema_version phase criterion_id status runner chrome synthetic_launch], errors, "ENTRY")
    return errors unless document.is_a?(Hash)

    expect(document, "schema_version", SCHEMA_VERSION, errors, "ENTRY")
    expect(document, "phase", PHASE, errors, "ENTRY")
    expect(document, "criterion_id", CRITERION_ID, errors, "ENTRY")
    expect(document, "status", "PASS", errors, "ENTRY")
    validate_runner(document["runner"], errors)
    validate_chrome(document["chrome"], errors)
    validate_launch(document["synthetic_launch"], errors)
    reject_forbidden_keys(document, errors)
    errors.uniq
  end

  def inspect_regular_executable(path)
    errors = []
    stat = File.lstat(path)
    errors << "CHROME_SYMLINK" if stat.symlink?
    errors << "CHROME_NOT_REGULAR" unless stat.file?
    errors << "CHROME_NOT_EXECUTABLE" unless File.executable?(path)
    errors << "CHROME_WORLD_WRITABLE" unless (stat.mode & 0o002).zero?
    errors
  rescue Errno::ENOENT, Errno::ENOTDIR
    ["CHROME_MISSING"]
  rescue SystemCallError => error
    ["CHROME_STAT_FAILED: #{error.class}"]
  end

  def executable_facts(path = CHROME_PATH)
    errors = []
    errors << "CHROME_PATH_INVALID" unless path == CHROME_PATH
    errors.concat(inspect_regular_executable(path))
    return [nil, errors.uniq] unless errors.empty?

    canonical = File.realpath(path)
    errors << "CHROME_CANONICAL_PATH_INVALID" unless canonical == CHROME_PATH
    stat = File.lstat(path)
    facts = {
      "executable_path" => CHROME_PATH,
      "canonical_path" => canonical,
      "file_type" => "regular",
      "executable" => true,
      "mode" => format("%04o", stat.mode & 0o7777),
      "owner_uid" => stat.uid,
      "current_uid" => Process.uid
    }
    [facts, errors]
  rescue SystemCallError => error
    [nil, ["CHROME_CANONICALIZE_FAILED: #{error.class}"]]
  end

  def validate_live_file(document)
    chrome = document.is_a?(Hash) ? document["chrome"] : nil
    facts, errors = executable_facts
    return errors unless errors.empty? && facts && chrome.is_a?(Hash)

    facts.each do |key, actual|
      errors << "LIVE_CHROME_#{key.upcase}_MISMATCH" unless chrome[key] == actual
    end
    errors
  end

  def validate_runner(runner, errors)
    exact_hash(runner, %w[id implementation argv_policy shell network_access], errors, "RUNNER")
    return unless runner.is_a?(Hash)

    expect(runner, "id", RUNNER_ID, errors, "RUNNER")
    expect(runner, "implementation", RUNNER_IMPLEMENTATION, errors, "RUNNER")
    expect(runner, "argv_policy", "fixed-code-owned", errors, "RUNNER")
    expect(runner, "shell", false, errors, "RUNNER")
    expect(runner, "network_access", false, errors, "RUNNER")
  end

  def validate_chrome(chrome, errors)
    exact_hash(
      chrome,
      %w[executable_path canonical_path file_type executable mode owner_uid current_uid version_argv version_output brand full_version major],
      errors,
      "CHROME"
    )
    return unless chrome.is_a?(Hash)

    errors << "CHROME_PATH_INVALID" unless chrome["executable_path"] == CHROME_PATH
    errors << "CHROME_CANONICAL_PATH_INVALID" unless chrome["canonical_path"] == CHROME_PATH
    expect(chrome, "file_type", "regular", errors, "CHROME")
    expect(chrome, "executable", true, errors, "CHROME")
    errors << "CHROME MODE_INVALID" unless chrome["mode"].is_a?(String) && chrome["mode"].match?(/\A0[0-7]{3}\z/)
    errors << "CHROME OWNER_UID_INVALID" unless chrome["owner_uid"].is_a?(Integer) && chrome["owner_uid"] >= 0
    errors << "CHROME CURRENT_UID_INVALID" unless chrome["current_uid"].is_a?(Integer) && chrome["current_uid"] >= 0
    expect(chrome, "version_argv", ["<standard-google-chrome>", "--version"], errors, "CHROME")
    expect(chrome, "brand", "Google Chrome", errors, "CHROME")

    version_output = chrome["version_output"]
    version_match = version_output.is_a?(String) ? version_output.match(VERSION_PATTERN) : nil
    errors << "CHROME VERSION_BRAND_INVALID" unless version_match
    full_version = chrome["full_version"]
    full_match = full_version.is_a?(String) ? full_version.match(FULL_VERSION_PATTERN) : nil
    errors << "CHROME FULL_VERSION_INVALID" unless full_match
    errors << "CHROME VERSION_OUTPUT_MISMATCH" if version_match && full_version != version_output.delete_prefix("Google Chrome ")
    errors << "CHROME MAJOR_VERSION_INVALID" unless chrome["major"].is_a?(Integer) && chrome["major"].positive?
    errors << "CHROME MAJOR_VERSION_MISMATCH" if full_match && chrome["major"] != full_match[1].to_i
  end

  def validate_launch(launch, errors)
    exact_hash(
      launch,
      %w[status exit_code timed_out completion_mode marker marker_observed profile_mode profile_owner_uid current_uid synthetic_page_mode command_identity stdout_sha256],
      errors,
      "LAUNCH"
    )
    return unless launch.is_a?(Hash)

    errors << "LAUNCH_STATUS_INVALID" unless launch["status"] == "PASS"
    errors << "LAUNCH_EXIT_INVALID" unless launch["exit_code"] == 0
    expect(launch, "timed_out", false, errors, "LAUNCH")
    unless %w[natural-exit marker-observed-graceful-stop].include?(launch["completion_mode"])
      errors << "LAUNCH COMPLETION_MODE_INVALID"
    end
    errors << "MARKER_INVALID" unless launch["marker"] == MARKER
    errors << "MARKER_NOT_OBSERVED" unless launch["marker_observed"] == true
    expect(launch, "profile_mode", "0700", errors, "LAUNCH")
    expect(launch, "synthetic_page_mode", "0600", errors, "LAUNCH")
    errors << "LAUNCH PROFILE_OWNER_INVALID" unless launch["profile_owner_uid"].is_a?(Integer) && launch["profile_owner_uid"] == launch["current_uid"]
    errors << "LAUNCH CURRENT_UID_INVALID" unless launch["current_uid"].is_a?(Integer) && launch["current_uid"] >= 0
    expect(launch, "command_identity", EXPECTED_COMMAND_IDENTITY, errors, "LAUNCH")
    errors << "LAUNCH STDOUT_SHA256_INVALID" unless launch["stdout_sha256"].is_a?(String) && launch["stdout_sha256"].match?(SHA256_PATTERN)
  end

  def exact_hash(value, expected_keys, errors, label)
    unless value.is_a?(Hash)
      errors << "#{label} TYPE_INVALID"
      return
    end

    actual = value.keys.map(&:to_s).sort
    expected = expected_keys.sort
    (expected - actual).each { |key| errors << "#{label} MISSING_KEY: #{key}" }
    (actual - expected).each { |key| errors << "#{label} UNKNOWN_KEY: #{key}" }
  end

  def expect(hash, key, expected, errors, label)
    errors << "#{label} #{key.upcase}_INVALID" unless hash.is_a?(Hash) && hash[key] == expected
  end

  def reject_forbidden_keys(value, errors, path = [])
    case value
    when Hash
      value.each do |key, nested|
        key_name = key.to_s.downcase
        current = path + [key.to_s]
        errors << "ENTRY FORBIDDEN_FIELD: #{current.join('.')}" if FORBIDDEN_KEYS.include?(key_name)
        reject_forbidden_keys(nested, errors, current)
      end
    when Array
      value.each_with_index { |nested, index| reject_forbidden_keys(nested, errors, path + [index.to_s]) }
    end
  end
end
