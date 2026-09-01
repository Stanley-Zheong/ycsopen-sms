#!/usr/bin/env ruby
# frozen_string_literal: true

require "base64"
require "cgi"
require "digest"
require "json"
require "optparse"
require "pathname"
require "securerandom"
require "set"

module Phase03ArtifactLeakScan
  PHASE = "03-crypto-storage-bootstrap"
  SCHEMA = "phase03-artifact-leak-scan-v1"
  CHECK_ID = "phase03-artifact-leak-scan"
  READER_IDENTITY = "phase03-artifact-scanner"
  FILE_LIMIT = 16 * 1024 * 1024
  TOTAL_LIMIT = 128 * 1024 * 1024
  FILE_COUNT_LIMIT = 4_096
  ALLOWED_SUFFIXES = Set.new(%w[.json .xml .txt .log .md]).freeze
  TARGETS = %w[evidence reports].freeze
  SHA256 = /\A[0-9a-f]{64}\z/
  DIRECT_PATTERNS = [
    /YCSLEAK_[A-Z]+_[A-Za-z0-9_-]{12,}/,
    /(?<!\d)1[3-9]\d{9}(?!\d)/,
    /(?<!\d)110101\d{12}(?!\d)/,
    /(?:cred|object|dek)_[0-9a-f]{24,}/i,
    /(?:ocap_v1_|regup_v1_)[A-Za-z0-9._~-]{16,}/,
    %r{https://canary\.invalid/[A-Za-z0-9._~-]{12,}}i,
    /canary\r?\n[0-9a-f]{12,}/i,
    /-----BEGIN [^-\r\n]*PRIVATE KEY-----/i,
    /\bPIN\s*[:=]\s*\S+/i
  ].freeze

  module_function

  def canonicalize(value)
    case value
    when Hash
      value.keys.map(&:to_s).sort.to_h do |key|
        nested = value.key?(key) ? value[key] : value[key.to_sym]
        [key, canonicalize(nested)]
      end
    when Array then value.map { |item| canonicalize(item) }
    else value
    end
  end

  def canonical_json(value)
    JSON.generate(canonicalize(value))
  end

  def result_digest(value)
    Digest::SHA256.hexdigest(canonical_json(value.reject { |key, _| key.to_s == "result_digest" }))
  end

  class Scanner
    attr_reader :errors, :report

    def initialize(root:, phase_dir:, generated_root:, output:)
      @root = Pathname(root).realpath
      @phase_dir = require_relative(phase_dir, "PHASE_DIR")
      @generated_root = require_relative(generated_root, "GENERATED_ROOT")
      @output = require_relative(output, "OUTPUT")
      @errors = []
      @report = nil
    rescue Errno::ENOENT, ArgumentError => exception
      @errors = [sanitized_code(exception.message, "ROOT_INVALID")]
    end

    def scan
      return self unless @errors.empty?

      phase = safe_directory(@phase_dir, "PHASE_DIR")
      generated = safe_directory(@generated_root, "GENERATED_ROOT")
      output = safe_output(generated)
      return self unless phase && generated && output

      evidence_root = phase.join("EVIDENCE")
      evidence = collect(
        evidence_root, target: "evidence", excluded_directories: Set["schema"], output: output
      )
      reports = collect(
        generated, target: "reports", excluded_directories: Set["services"], output: output
      )
      return self unless @errors.empty?

      target_rows = []
      all_inputs = []
      { "evidence" => evidence, "reports" => reports }.each do |target, files|
        if files.empty?
          @errors << "#{target.upcase}_INPUTS_EMPTY"
          next
        end
        matches = 0
        files.each do |path|
          bytes = bounded_read(path, target)
          next unless bytes
          digest = Digest::SHA256.hexdigest(bytes)
          all_inputs << [target, relative_identity(path), path.size, digest]
          matches += 1 if prohibited?(bytes)
        ensure
          bytes&.replace("\0" * bytes.bytesize)
        end
        target_rows << {
          "id" => target,
          "reader_identity" => READER_IDENTITY,
          "scanned_items" => files.length,
          "prohibited_matches" => matches,
          "sensitivity_status" => sensitivity_proven? ? "DETECTED_SEEDED_MUTATION" : "FAILED"
        }
      end
      return self unless @errors.empty?

      clean = target_rows.all? do |row|
        row["prohibited_matches"].zero? && row["sensitivity_status"] == "DETECTED_SEEDED_MUTATION"
      end
      @report = {
        "schema_version" => SCHEMA,
        "phase" => PHASE,
        "check_id" => CHECK_ID,
        "status" => clean ? "PASS" : "FAIL",
        "exit_code" => clean ? 0 : 1,
        "input_digest" => Digest::SHA256.hexdigest(
          Phase03ArtifactLeakScan.canonical_json(all_inputs.sort)
        ),
        "targets" => target_rows.sort_by { |row| row.fetch("id") }
      }
      @report["result_digest"] = Phase03ArtifactLeakScan.result_digest(@report)
      atomic_write(output, Phase03ArtifactLeakScan.canonical_json(@report) + "\n")
      self
    rescue StandardError => exception
      @errors << "SCANNER_INTERNAL_ERROR_#{exception.class.name.gsub(/[^A-Za-z0-9]/, "_").upcase}"
      @errors.uniq!
      self
    end

    def success?
      @errors.empty? && @report && @report["status"] == "PASS" && @report["exit_code"] == 0
    end

    private

    def require_relative(value, label)
      raise ArgumentError, "#{label}_PATH_INVALID" unless canonical_relative?(value)
      Pathname(value).cleanpath.to_s
    end

    def canonical_relative?(value)
      return false unless value.is_a?(String) && !value.empty? && !value.include?("\0") && !value.include?("\\")
      path = Pathname(value)
      !path.absolute? && path.cleanpath.to_s == value && path.each_filename.none? { |part| part == ".." }
    end

    def safe_directory(relative, label)
      candidate = contained(relative, label)
      return nil unless candidate
      unless candidate.exist? && candidate.lstat.directory? && !candidate.lstat.symlink?
        @errors << "#{label}_TYPE_INVALID"
        return nil
      end
      candidate
    end

    def safe_output(generated)
      candidate = contained(@output, "OUTPUT", allow_missing_leaf: true)
      return nil unless candidate
      unless candidate.to_s.start_with?("#{generated}#{File::SEPARATOR}")
        @errors << "OUTPUT_OUTSIDE_GENERATED_ROOT"
        return nil
      end
      parent = candidate.parent
      unless parent.exist? && parent.lstat.directory? && !parent.lstat.symlink?
        @errors << "OUTPUT_PARENT_INVALID"
        return nil
      end
      if candidate.exist? && (!candidate.lstat.file? || candidate.lstat.symlink? || candidate.lstat.nlink != 1)
        @errors << "OUTPUT_TYPE_INVALID"
        return nil
      end
      candidate
    end

    def contained(relative, label, allow_missing_leaf: false)
      candidate = @root.join(relative).cleanpath
      unless candidate.to_s.start_with?("#{@root}#{File::SEPARATOR}")
        @errors << "#{label}_PATH_ESCAPE"
        return nil
      end
      current = allow_missing_leaf ? candidate.parent : candidate
      until current == @root
        if current.exist? && current.lstat.symlink?
          @errors << "#{label}_SYMLINK_REJECTED"
          return nil
        end
        current = current.parent
      end
      candidate
    end

    def collect(directory, target:, excluded_directories:, output:)
      unless directory.exist? && directory.lstat.directory? && !directory.lstat.symlink?
        @errors << "#{target.upcase}_ROOT_INVALID"
        return []
      end
      files = []
      pending = [directory]
      until pending.empty?
        current = pending.pop
        current.children.sort.each do |entry|
          if entry.lstat.symlink?
            @errors << "#{target.upcase}_SYMLINK_REJECTED"
          elsif entry.lstat.directory?
            next if current == directory && excluded_directories.include?(entry.basename.to_s)
            pending << entry
          elsif entry.lstat.file?
            next if entry == output
            unless ALLOWED_SUFFIXES.include?(entry.extname.downcase)
              @errors << "#{target.upcase}_FILE_TYPE_INVALID"
              next
            end
            if entry.lstat.nlink != 1 || entry.size > FILE_LIMIT
              @errors << "#{target.upcase}_FILE_BOUND_INVALID"
              next
            end
            files << entry
            @errors << "FILE_COUNT_LIMIT_EXCEEDED" if files.length > FILE_COUNT_LIMIT
          else
            @errors << "#{target.upcase}_FILE_TYPE_INVALID"
          end
        end
      end
      files.sort
    rescue Errno::ENOENT, Errno::ENOTDIR
      @errors << "#{target.upcase}_ROOT_INVALID"
      []
    end

    def bounded_read(path, target)
      before = path.lstat
      flags = File::RDONLY
      flags |= File::NOFOLLOW if File.const_defined?(:NOFOLLOW)
      File.open(path.to_s, flags) do |file|
        opened = file.stat
        unless opened.file? && opened.nlink == 1 && opened.size <= FILE_LIMIT &&
               opened.dev == before.dev && opened.ino == before.ino
          @errors << "#{target.upcase}_FILE_BOUND_INVALID"
          return nil
        end
        @total_bytes ||= 0
        @total_bytes = Integer(@total_bytes) + opened.size
        if @total_bytes > TOTAL_LIMIT
          @errors << "TOTAL_SIZE_LIMIT_EXCEEDED"
          return nil
        end
        bytes = file.read(FILE_LIMIT + 1)
        after = file.stat
        unless bytes.bytesize == opened.size && bytes.bytesize <= FILE_LIMIT &&
               after.dev == opened.dev && after.ino == opened.ino &&
               after.size == opened.size && after.mtime == opened.mtime
          bytes.replace("\0" * bytes.bytesize)
          @errors << "#{target.upcase}_FILE_CHANGED"
          return nil
        end
        bytes
      end
    rescue Errno::ENOENT, Errno::ENOTDIR, IOError
      @errors << "#{target.upcase}_FILE_UNREADABLE"
      nil
    end

    def prohibited?(bytes)
      candidates = decoded_candidates(bytes)
      found = false
      candidates.each do |candidate|
        text = candidate.dup.force_encoding(Encoding::UTF_8)
        found ||= text.valid_encoding? && DIRECT_PATTERNS.any? { |pattern| text.match?(pattern) }
        text.replace("\0" * text.bytesize)
      ensure
        candidate.replace("\0" * candidate.bytesize)
      end
      found
    ensure
      candidates&.each { |candidate| candidate.replace("\0" * candidate.bytesize) }
    end

    def decoded_candidates(bytes)
      candidates = [bytes]
      text = bytes.dup.force_encoding(Encoding::ASCII_8BIT)
      text.scan(/[A-Za-z0-9+\/_-]{16,4096}={0,2}/).first(4_096).each do |token|
        candidates << decode64(token, false)
        candidates << decode64(token, true)
      end
      text.scan(/[0-9a-fA-F]{22,4096}/).first(4_096).each do |token|
        candidates << [token].pack("H*") if token.length.even?
      end
      if text.include?("%") || text.include?("+")
        candidates << CGI.unescape(text)
      end
      candidates.compact.uniq
    end

    def decode64(token, urlsafe)
      padded = token + ("=" * ((4 - token.length % 4) % 4))
      urlsafe ? Base64.urlsafe_decode64(padded) : Base64.strict_decode64(padded)
    rescue ArgumentError
      nil
    end

    def sensitivity_proven?
      seed = "ocap_v1_#{SecureRandom.hex(24)}"
      variants = [
        seed,
        Base64.strict_encode64(seed),
        seed.unpack1("H*"),
        CGI.escape(seed)
      ]
      variants.all? { |value| prohibited?(value.b) }
    ensure
      seed&.replace("\0" * seed.bytesize)
      variants&.each { |value| value.replace("\0" * value.bytesize) }
    end

    def atomic_write(output, content)
      temporary = output.parent.join(".phase03-leak-scan-#{Process.pid}-#{SecureRandom.hex(8)}.tmp")
      File.open(temporary, File::WRONLY | File::CREAT | File::EXCL, 0o600) do |file|
        file.write(content)
        file.flush
        file.fsync
      end
      File.rename(temporary, output)
    ensure
      File.delete(temporary) if temporary && temporary.exist?
      content&.replace("\0" * content.bytesize)
    end

    def relative_identity(path)
      path.relative_path_from(@root).to_s
    end

    def sanitized_code(message, fallback)
      value = message.to_s
      value.match?(/\A[A-Z0-9_]+\z/) ? value : fallback
    end
  end
end

if $PROGRAM_NAME == __FILE__
  options = {}
  parser = OptionParser.new do |cli|
    cli.on("--phase-dir PATH") { |value| options[:phase_dir] = value }
    cli.on("--generated-root PATH") { |value| options[:generated_root] = value }
    cli.on("--output PATH") { |value| options[:output] = value }
  end
  begin
    parser.parse!(ARGV)
    unless ARGV.empty? && %i[phase_dir generated_root output].all? { |key| options[key] }
      warn "phase03_artifact_scan=FAIL error=ARGUMENTS_INVALID"
      exit 2
    end
    scanner = Phase03ArtifactLeakScan::Scanner.new(root: Dir.pwd, **options).scan
    if scanner.success?
      puts "phase03_artifact_scan=PASS targets=2"
      exit 0
    end
    scanner.errors.each { |error| warn "phase03_artifact_scan=FAIL error=#{error}" }
    if scanner.report
      scanner.report.fetch("targets").each do |target|
        next if target.fetch("prohibited_matches").zero?
        warn "phase03_artifact_scan=FAIL target=#{target.fetch('id')} prohibited_matches=#{target.fetch('prohibited_matches')}"
      end
    end
    exit 1
  rescue OptionParser::ParseError
    warn "phase03_artifact_scan=FAIL error=ARGUMENTS_INVALID"
    exit 2
  end
end
