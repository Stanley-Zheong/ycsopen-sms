#!/usr/bin/env ruby
# frozen_string_literal: true

require "digest"
require "json"
require "open3"
require "pathname"
require "rbconfig"
require "set"
require "yaml"

module PlanningValidatorSupport
  ELEMENT_TEST_ID = /\A(?:admin|tenant|shared|public)-[a-z0-9]+-[a-z0-9]+-[a-z0-9]+-[a-z0-9]+(?:-[a-z0-9]+)*\z/
  OBLIGATION_ID = /\bOBL-[A-Z0-9-]+\b/
  REQUIREMENT_ID = /\b(?:REQ-(?:F|NFR)-[A-Z0-9-]+|PROJECT-[A-Z0-9-]+)\b/
  ENTRY_HEADERS = ["Criterion ID", "Verdict", "Evidence", "Command or inspection rule"].freeze
  UI_HEADERS = [
    "Page ID/route", "Role/permission", "Region", "Element/type",
    "Data/validation/format", "Action and API effect", "States and feedback",
    "data-testid", "Obligation/requirement IDs", "Behavior IDs",
    "Catalog test/layer", "Playwright ID"
  ].freeze
  UI_TEST_MATRIX_HEADERS = [
    "Obligation ID", "Requirement IDs", "Behavior ID", "Catalog test/layer",
    "Playwright ID", "Page ID/route", "data-testid", "Case ID", "Case",
    "Command", "Evidence"
  ].freeze
  SCHEMA_HEADERS = [
    "Schema ID", "PRD data domain", "Schema object/prefix", "Owner package",
    "Migration namespace", "Dependencies", "Compatibility", "Rollback",
    "Cross-owner protocol"
  ].freeze
  SCHEMA_CLAIM_HEADERS = [
    "Claim ID", "Schema object/prefix", "Owner package", "Migration ID",
    "Depends on migration", "Compatibility step", "Rollback", "Cross-owner approval"
  ].freeze

  CatalogRecord = Struct.new(
    :id, :requirements, :owner, :behavior, :ui_reference, :test_reference, :evidence,
    keyword_init: true
  )
  SchemaOwner = Struct.new(
    :id, :domain, :prefix, :owner, :range_start, :range_end, :dependencies,
    :compatibility, :rollback, :protocol, keyword_init: true
  )
  PlanNode = Struct.new(
    :id, :phase, :plan, :wave, :dependencies, :files, :read_first, :path,
    keyword_init: true
  )

  module_function

  def read(path, errors, label = "file")
    File.read(path)
  rescue Errno::ENOENT
    errors << "MISSING_#{label.upcase.tr(' ', '_')}: #{path}"
    ""
  rescue Errno::EACCES
    errors << "UNREADABLE_#{label.upcase.tr(' ', '_')}: #{path}"
    ""
  end

  def markdown_table(path, expected_headers, errors, label)
    body = read(path, errors, label)
    return [] if body.empty?

    lines = body.lines.map(&:strip)
    header_index = lines.index do |line|
      table_cells(line)&.map(&:downcase) == expected_headers.map(&:downcase)
    end
    if header_index.nil?
      errors << "BAD_#{label.upcase}_HEADER: #{path} expected=#{expected_headers.join(' | ')}"
      return []
    end

    rows = []
    lines.drop(header_index + 1).each do |line|
      break unless line.start_with?("|") && line.end_with?("|")

      cells = table_cells(line)
      next if cells.length == expected_headers.length && cells.all? { |cell| cell.match?(/\A:?-{3,}:?\z/) }

      if cells.length != expected_headers.length
        errors << "BAD_#{label.upcase}_COLUMN_COUNT: #{path} expected=#{expected_headers.length} actual=#{cells.length}"
        next
      end
      rows << cells
    end
    errors << "EMPTY_#{label.upcase}_TABLE: #{path}" if rows.empty?
    rows
  end

  def markdown_tables(path, expected_headers, errors, label)
    body = read(path, errors, label)
    return [] if body.empty?

    lines = body.lines.map(&:strip)
    header_indexes = lines.each_index.select do |index|
      table_cells(lines[index])&.map(&:downcase) == expected_headers.map(&:downcase)
    end
    if header_indexes.empty?
      errors << "BAD_#{label.upcase}_HEADER: #{path} expected=#{expected_headers.join(' | ')}"
      return []
    end

    header_indexes.map.with_index do |header_index, table_index|
      rows = []
      lines.drop(header_index + 1).each do |line|
        break unless line.start_with?("|") && line.end_with?("|")

        cells = table_cells(line)
        next if cells.length == expected_headers.length && cells.all? { |cell| cell.match?(/\A:?-{3,}:?\z/) }
        if cells.length != expected_headers.length
          errors << "BAD_#{label.upcase}_COLUMN_COUNT: #{path} table=#{table_index + 1} expected=#{expected_headers.length} actual=#{cells.length}"
          next
        end
        rows << cells
      end
      errors << "EMPTY_#{label.upcase}_TABLE: #{path} table=#{table_index + 1}" if rows.empty?
      rows
    end
  end

  def table_cells(line)
    return nil unless line.start_with?("|") && line.end_with?("|")

    line.delete_prefix("|").delete_suffix("|").split("|", -1).map(&:strip)
  end

  def roadmap_packages(path, errors)
    body = read(path, errors, "roadmap")
    phases = {}
    body.split(/(?=^### Phase \d+:)/).each do |block|
      next unless block =~ /^### Phase (\d+):/

      number = Regexp.last_match(1).to_i
      package = block[/^\*\*Package ID\*\*: `([^`]+)`$/, 1]
      if package.nil?
        errors << "ROADMAP_PACKAGE_MISSING: phase=#{number}"
      elsif phases.value?(package)
        errors << "ROADMAP_PACKAGE_DUPLICATE: package=#{package}"
      else
        phases[number] = package
      end
    end
    phases
  end

  def roadmap_dependencies(path, errors)
    body = read(path, errors, "roadmap")
    dependencies = {}
    body.split(/(?=^### Phase \d+:)/).each do |block|
      next unless block =~ /^### Phase (\d+):/

      phase = Regexp.last_match(1).to_i
      line = block[/^\*\*Depends on\*\*: (.+)$/, 1]
      if line.nil?
        errors << "ROADMAP_DEPENDENCY_DECLARATION_MISSING: phase=#{phase}"
        dependencies[phase] = []
        next
      end
      values = []
      unless line == "Nothing."
        line.scan(/\d+(?:-\d+)?/).each do |token|
          if token.include?("-")
            left, right = token.split("-").map(&:to_i)
            values.concat((left..right).to_a)
          else
            values << token.to_i
          end
        end
      end
      duplicate_values = duplicates(values)
      errors << "ROADMAP_DEPENDENCY_DUPLICATE: phase=#{phase} values=#{duplicate_values.join(',')}" unless duplicate_values.empty?
      invalid = values.select { |value| value < 1 || value >= phase }
      errors << "ROADMAP_DEPENDENCY_INVALID: phase=#{phase} values=#{invalid.join(',')}" unless invalid.empty?
      dependencies[phase] = values.uniq
    end
    dependencies
  end

  def validate_dependency_evidence(root, phase, phases, dependency_map, errors)
    dependency_map.fetch(phase, []).each do |dependency_phase|
      dependency_package = phases[dependency_phase]
      if dependency_package.nil?
        errors << "DEPENDENCY_PACKAGE_UNKNOWN: phase=#{phase} dependency=#{dependency_phase}"
        next
      end
      token = format("%02d", dependency_phase)
      directory = File.join(root, ".planning/phases/#{token}-#{dependency_package}")
      errors << "DEPENDENCY_DIRECTORY_MISSING: #{directory}" unless File.directory?(directory)
      summary_path = File.join(directory, "SUMMARY.md")
      verification_path = File.join(directory, "#{token}-VERIFICATION.md")
      todo_path = File.join(directory, "TODO.md")
      verification = read(verification_path, errors, "dependency verification")
      validate_todo(todo_path, errors, require_empty: true)
      read(summary_path, errors, "dependency summary")
      unless verification.match?(/^## Verdict\s*\n+PASS\s*$/)
        errors << "DEPENDENCY_VERIFICATION_NOT_PASS: #{verification_path}"
      end
      validate_dependency_delivery_attestation(root, token, directory, summary_path, errors)
    end
  end

  def validate_dependency_delivery_attestation(root, phase_token, directory, summary_path, errors)
    validator = File.join(root, ".planning/tools/validate-delivery-attestation.rb")
    unless File.file?(validator)
      errors << "DEPENDENCY_DELIVERY_VALIDATOR_MISSING: #{validator}"
      return
    end
    evidence_manifest = File.join(directory, "EVIDENCE/evidence-manifest.json")
    stdout, stderr, status = Open3.capture3(
      RbConfig.ruby,
      validator,
      "--phase", phase_token,
      "--summary", repository_relative(root, summary_path),
      "--evidence-manifest", repository_relative(root, evidence_manifest),
      "--require-pr-check-pass",
      chdir: root
    )
    return if status.success?

    diagnostics = (stdout + stderr).lines.map(&:strip).reject(&:empty?).first(8).join(" | ")
    errors << "DEPENDENCY_DELIVERY_ATTESTATION_INVALID: phase=#{phase_token} diagnostics=#{diagnostics}"
  rescue Errno::ENOENT => error
    errors << "DEPENDENCY_DELIVERY_ATTESTATION_EXECUTION_FAILED: phase=#{phase_token} error=#{error.class}"
  end

  def repository_relative(root, path)
    Pathname(File.expand_path(path)).relative_path_from(Pathname(File.expand_path(root))).to_s
  end

  def catalog_records(path, errors)
    records = []
    read(path, errors, "obligation catalog").lines.each_with_index do |line, index|
      next unless line.start_with?("- OBL-")

      fields = line.chomp.split(" | ", -1)
      if fields.length != 9
        errors << "CATALOG_BAD_FIELDS: line=#{index + 1} expected=9 actual=#{fields.length}"
        next
      end
      records << CatalogRecord.new(
        id: fields[0].delete_prefix("- "),
        requirements: fields[2].split(","),
        owner: fields[3],
        behavior: fields[4],
        ui_reference: fields[5],
        test_reference: fields[6],
        evidence: fields[7]
      )
    end
    errors << "CATALOG_EMPTY: #{path}" if records.empty?
    duplicate_ids = duplicates(records.map(&:id))
    errors << "CATALOG_DUPLICATE_IDS: #{duplicate_ids.join(',')}" unless duplicate_ids.empty?
    records
  end

  def exact_obligation_trace(path, owned_ids, known_ids, errors, label)
    body = read(path, errors, label)
    return if body.empty?

    actual = body.scan(OBLIGATION_ID).to_set
    missing = owned_ids - actual
    foreign = actual - owned_ids
    unknown = actual - known_ids
    errors << "MISSING_OWNED_OBLIGATION: artifact=#{path} ids=#{missing.to_a.sort.join(',')}" unless missing.empty?
    errors << "FOREIGN_OBLIGATION_ID: artifact=#{path} ids=#{foreign.to_a.sort.join(',')}" unless foreign.empty?
    errors << "UNKNOWN_OBLIGATION_ID: artifact=#{path} ids=#{unknown.to_a.sort.join(',')}" unless unknown.empty?
  end

  def validate_entry_review(path, errors)
    rows = markdown_table(path, ENTRY_HEADERS, errors, "entry_review")
    ids = rows.map(&:first)
    duplicate_ids = duplicates(ids)
    errors << "ENTRY_REVIEW_DUPLICATE_CRITERION: #{duplicate_ids.join(',')}" unless duplicate_ids.empty?
    rows.each do |criterion_id, verdict, evidence, command|
      errors << "ENTRY_REVIEW_EMPTY_CRITERION_ID" if criterion_id.empty?
      errors << "ENTRY_REVIEW_BAD_VERDICT: criterion=#{criterion_id} verdict=#{verdict}" unless %w[PASS BLOCKER].include?(verdict)
      errors << "ENTRY_REVIEW_EMPTY_EVIDENCE: criterion=#{criterion_id}" if evidence.empty?
      errors << "ENTRY_REVIEW_EMPTY_COMMAND: criterion=#{criterion_id}" if command.empty?
      errors << "ENTRY_REVIEW_BLOCKER: criterion=#{criterion_id}" if verdict == "BLOCKER"
    end

    body = File.file?(path) ? File.read(path) : ""
    errors << "ENTRY_REVIEW_FINAL_VERDICT_MISSING: #{path}" unless body.match?(/^## Verdict\s*\n+PASS\s*$/)
  end

  def validate_entry_evidence(path, review_path, errors)
    body = read(path, errors, "entry evidence")
    return if body.empty?

    errors << "ENTRY_EVIDENCE_SUBJECT_MISSING: #{path}" unless body.match?(/^Review subject commit: `[0-9a-f]{40}`$/)
    errors << "ENTRY_EVIDENCE_RECORDER_MISSING: #{path}" unless body.match?(/^Evidence recorder identity: \S.+$/)
    errors << "ENTRY_EVIDENCE_TOOL_BOUNDARY_MISSING: #{path}" unless body.match?(/^Tool boundary: \S.+$/)
    errors << "ENTRY_EVIDENCE_IDENTITY_ASSURANCE_MISSING: #{path}" unless body.match?(/^Identity assurance: \S.+$/)
    successful_commands = body.scan(/^Exit status: `0`$/).length
    errors << "ENTRY_EVIDENCE_COMMAND_TRANSCRIPT_INCOMPLETE: #{path} successful=#{successful_commands}" if successful_commands < 4

    digest = Digest::SHA256.file(path).hexdigest
    review = read(review_path, errors, "entry review")
    errors << "ENTRY_EVIDENCE_DIGEST_MISSING: expected=#{digest}" unless review.include?("ENTRY-EVIDENCE-SHA256: #{digest}")
  rescue Errno::ENOENT, Errno::EACCES
    # read/Digest diagnostics are normalized above or by this fallback.
    errors << "ENTRY_EVIDENCE_DIGEST_UNAVAILABLE: #{path}" unless errors.any? { |error| error.include?(path) }
  end

  def validate_plans(plan_paths, errors)
    errors << "PLAN_MISSING: expected at least one plan" if plan_paths.empty?
    nodes = []
    plan_paths.each do |path|
      body = read(path, errors, "plan")
      node = parse_plan_node(path, body, errors)
      nodes << node unless node.nil?
      tasks = body.scan(/<task\b[^>]*>(.*?)<\/task>/m).flatten
      errors << "PLAN_TASK_MISSING: #{path}" if tasks.empty?
      tasks.each_with_index do |task, index|
        %w[files action verify done].each do |field|
          match = task.match(/<#{field}>\s*(.*?)\s*<\/#{field}>/m)
          if match.nil? || match[1].strip.empty?
            errors << "PLAN_TASK_FIELD_MISSING: plan=#{path} task=#{index + 1} field=#{field}"
          end
        end
        automated = task[/<automated>\s*(.*?)\s*<\/automated>/m, 1]
        if automated&.match?(/\brg\b/) && automated.include?("\\|")
          errors << "PLAN_RG_ESCAPED_ALTERNATION: plan=#{path} task=#{index + 1}"
        end
      end
    end

    validate_plan_graph(nodes, errors)
  end

  def parse_plan_node(path, body, errors)
    filename = File.basename(path)
    filename_match = filename.match(/\A(?<phase>\d{2})-(?<plan>\d{2})-PLAN\.md\z/)
    if filename_match.nil?
      errors << "PLAN_FILENAME_BAD: #{path}"
      return nil
    end

    unless body.start_with?("---\n") || body.start_with?("---\r\n")
      errors << "PLAN_FRONTMATTER_MISSING: #{path}"
      return nil
    end
    boundary = body.match(/\A---\r?\n(.*?)\r?\n---\r?\n/m)
    if boundary.nil?
      errors << "PLAN_FRONTMATTER_UNTERMINATED: #{path}"
      return nil
    end

    data = begin
      YAML.safe_load(
        boundary[1],
        permitted_classes: [],
        permitted_symbols: [],
        aliases: false
      )
    rescue Psych::Exception => error
      errors << "PLAN_FRONTMATTER_YAML_INVALID: #{path} error=#{error.class}"
      return nil
    end
    unless data.is_a?(Hash)
      errors << "PLAN_FRONTMATTER_NOT_MAPPING: #{path}"
      return nil
    end

    phase = data["phase"]
    plan = data["plan"]
    wave = data["wave"]
    dependencies = data["depends_on"]
    files = data["files_modified"]
    read_first = plan_read_first_paths(path, body, errors)
    valid = true

    unless phase.is_a?(String) && phase.match?(/\A#{Regexp.escape(filename_match[:phase])}-[a-z0-9]+(?:-[a-z0-9]+)*\z/)
      errors << "PLAN_PHASE_BAD: path=#{path} value=#{phase.inspect}"
      valid = false
    end
    unless plan.is_a?(String) && plan.match?(/\A\d{2}\z/)
      errors << "PLAN_ID_BAD: path=#{path} value=#{plan.inspect}"
      valid = false
    end
    if plan.is_a?(String) && plan.match?(/\A\d{2}\z/) && plan != filename_match[:plan]
      errors << "PLAN_ID_FILENAME_MISMATCH: path=#{path} filename=#{filename_match[:plan]} frontmatter=#{plan}"
      valid = false
    end
    unless wave.is_a?(Integer) && wave >= 0
      errors << "PLAN_WAVE_BAD: path=#{path} value=#{wave.inspect}"
      valid = false
    end
    unless dependencies.is_a?(Array) && dependencies.all? { |value| value.is_a?(String) && value.match?(/\A\d{2}-\d{2}\z/) }
      errors << "PLAN_DEPENDS_ON_BAD: path=#{path} value=#{dependencies.inspect}"
      valid = false
    end
    unless files.is_a?(Array) && !files.empty? && files.all? { |value| valid_plan_file_path?(value) }
      errors << "PLAN_FILES_MODIFIED_BAD: path=#{path} value=#{files.inspect}"
      valid = false
    end

    if dependencies.is_a?(Array)
      duplicate_dependencies = duplicates(dependencies)
      unless duplicate_dependencies.empty?
        errors << "PLAN_DEPENDENCY_DUPLICATE: path=#{path} ids=#{duplicate_dependencies.sort.join(',')}"
        valid = false
      end
    end
    if files.is_a?(Array)
      duplicate_files = duplicates(files)
      unless duplicate_files.empty?
        errors << "PLAN_FILES_MODIFIED_DUPLICATE: path=#{path} files=#{duplicate_files.sort.join(',')}"
        valid = false
      end
    end
    return nil unless valid

    PlanNode.new(
      id: "#{filename_match[:phase]}-#{plan}",
      phase: phase,
      plan: plan,
      wave: wave,
      dependencies: dependencies,
      files: files,
      read_first: read_first,
      path: path
    )
  end

  def plan_read_first_paths(path, body, errors)
    blocks = body.scan(/<read_first>\s*(.*?)\s*<\/read_first>/m).flatten
    paths = blocks.flat_map do |block|
      block.lines.filter_map do |line|
        stripped = line.strip
        next if stripped.empty?

        match = stripped.match(/\A-\s+(.+?)\s*\z/)
        if match.nil?
          errors << "PLAN_READ_FIRST_BAD: path=#{path} value=#{stripped.inspect}"
          next
        end
        value = match[1].delete_prefix("`").delete_suffix("`")
        unless valid_plan_file_path?(value)
          errors << "PLAN_READ_FIRST_BAD: path=#{path} value=#{value.inspect}"
          next
        end
        value
      end
    end
    # Re-reading one shared prerequisite in separate tasks is intentional and
    # collapses to one plan-level consumer edge.
    paths.uniq
  end

  def valid_plan_file_path?(value)
    return false unless value.is_a?(String)
    return false if value.empty? || value.start_with?("/") || value.include?("\\")

    segments = value.split("/", -1)
    segments.none? { |segment| segment.empty? || segment == "." || segment == ".." }
  end

  def validate_plan_graph(nodes, errors)
    grouped = nodes.group_by(&:id)
    grouped.each do |id, matches|
      errors << "PLAN_ID_DUPLICATE: id=#{id} paths=#{matches.map(&:path).sort.join(',')}" if matches.length > 1
    end

    phase_groups = nodes.group_by { |node| node.id.split("-", 2).first }
    phase_groups.each_value do |phase_nodes|
      phases = phase_nodes.map(&:phase).uniq
      if phases.length > 1
        errors << "PLAN_PHASE_SET_MISMATCH: phases=#{phases.sort.join(',')} paths=#{phase_nodes.map(&:path).sort.join(',')}"
      end
    end

    by_id = grouped.transform_values(&:first)
    nodes.each do |node|
      node.dependencies.each do |dependency_id|
        if dependency_id == node.id
          errors << "PLAN_DEPENDENCY_SELF: plan=#{node.id}"
          next
        end
        dependency = by_id[dependency_id]
        if dependency.nil?
          errors << "PLAN_DEPENDENCY_UNKNOWN: plan=#{node.id} dependency=#{dependency_id}"
          next
        end
        unless dependency.wave < node.wave
          errors << "PLAN_DEPENDENCY_WAVE_NOT_EARLIER: plan=#{node.id} wave=#{node.wave} dependency=#{dependency_id} dependency_wave=#{dependency.wave}"
        end
      end
    end

    plan_dependency_cycles(by_id).each do |cycle|
      errors << "PLAN_DEPENDENCY_CYCLE: #{cycle.join('->')}"
    end

    validate_planned_artifact_wiring(nodes, by_id, errors)
    validate_shared_file_dependency_wiring(nodes, by_id, errors)

    nodes.group_by(&:wave).sort.each do |wave, wave_nodes|
      owners = Hash.new { |hash, key| hash[key] = [] }
      wave_nodes.each do |node|
        node.files.each { |file| owners[file] << node.id }
      end
      owners.sort.each do |file, plan_ids|
        unique_ids = plan_ids.uniq.sort
        next unless unique_ids.length > 1

        errors << "PLAN_SAME_WAVE_FILE_OVERLAP: wave=#{wave} file=#{file} plans=#{unique_ids.join(',')}"
      end
    end
  end

  # Every file with owners in multiple waves is an incremental ownership chain,
  # regardless of whether the file already exists in the repository. Wave order
  # alone is not an executable prerequisite: each later owner must be able to
  # reach the immediately preceding owner through depends_on. Chaining adjacent
  # owners makes all earlier mutations transitively reachable without requiring
  # a redundant complete graph.
  def validate_shared_file_dependency_wiring(nodes, by_id, errors)
    owners = Hash.new { |hash, key| hash[key] = [] }
    nodes.each do |node|
      node.files.each { |file| owners[file] << node }
    end

    owners.sort.each do |file, file_nodes|
      ordered = file_nodes.uniq { |node| node.id }.sort_by { |node| [node.wave, node.id] }
      next unless ordered.length > 1

      ordered.each_cons(2) do |earlier, later|
        # Same-wave ambiguity is reported by PLAN_SAME_WAVE_FILE_OVERLAP.
        next unless earlier.wave < later.wave
        next if plan_dependency_reachable?(later.id, earlier.id, by_id)

        errors << "PLAN_SHARED_FILE_DEPENDENCY_MISSING: file=#{file} earlier=#{earlier.id} earlier_wave=#{earlier.wave} later=#{later.id} later_wave=#{later.wave}"
      end
    end
  end

  def validate_planned_artifact_wiring(nodes, by_id, errors)
    producers = Hash.new { |hash, key| hash[key] = [] }
    nodes.each do |node|
      node.files.each { |file| producers[file] << node }
    end

    nodes.sort_by(&:id).each do |consumer|
      consumer.read_first.sort.each do |file|
        # Existing files are shared repository inputs or incremental modifications,
        # not uniquely created planned artifacts.
        next if File.exist?(File.expand_path(file, Dir.pwd))

        candidates = producers[file]
        # An absent path with no files_modified owner is an external/generated input;
        # another validator or the plan's executable preflight owns its availability.
        next if candidates.empty?

        earlier = candidates.select { |candidate| candidate.wave < consumer.wave }
        if earlier.empty?
          # A plan may read and create its own new artifact. A same/later-wave
          # producer in another plan cannot supply the consumer.
          next if candidates.any? { |candidate| candidate.id == consumer.id }

          errors << "PLAN_ARTIFACT_DEPENDENCY_MISSING: consumer=#{consumer.id} file=#{file} producer=#{candidates.map(&:id).uniq.sort.join(',')} producer_wave=#{candidates.map(&:wave).uniq.sort.join(',')} consumer_wave=#{consumer.wave}"
          next
        end

        latest_wave = earlier.map(&:wave).max
        latest = earlier.select { |candidate| candidate.wave == latest_wave }
        if latest.length > 1
          errors << "PLAN_ARTIFACT_PRODUCER_AMBIGUOUS: consumer=#{consumer.id} file=#{file} producers=#{latest.map(&:id).uniq.sort.join(',')} wave=#{latest_wave}"
          next
        end

        # Multiple writers in distinct waves are an explicit incremental chain.
        # The consumer binds to the latest earlier writer; that writer is itself
        # checked as a consumer when it declares read_first for the same path.
        producer = latest.first
        unless plan_dependency_reachable?(consumer.id, producer.id, by_id)
          errors << "PLAN_ARTIFACT_DEPENDENCY_MISSING: consumer=#{consumer.id} file=#{file} producer=#{producer.id} producer_wave=#{producer.wave} consumer_wave=#{consumer.wave}"
        end
      end
    end
  end

  def plan_dependency_reachable?(consumer_id, producer_id, by_id)
    pending = by_id.fetch(consumer_id).dependencies.dup
    visited = Set.new
    until pending.empty?
      dependency_id = pending.shift
      next unless visited.add?(dependency_id)
      return true if dependency_id == producer_id

      dependency = by_id[dependency_id]
      pending.concat(dependency.dependencies) unless dependency.nil?
    end
    false
  end

  def plan_dependency_cycles(by_id)
    state = {}
    stack = []
    cycles = Set.new
    visit = lambda do |id|
      state[id] = :visiting
      stack << id
      by_id.fetch(id).dependencies.sort.each do |dependency_id|
        next unless by_id.key?(dependency_id)

        if state[dependency_id] == :visiting
          start = stack.index(dependency_id)
          cycle = stack[start..] + [dependency_id]
          rotations = cycle[0...-1].each_index.map do |index|
            body = cycle[0...-1].rotate(index)
            body + [body.first]
          end
          cycles << rotations.min.join("->")
        elsif state[dependency_id].nil?
          visit.call(dependency_id)
        end
      end
      stack.pop
      state[id] = :visited
    end
    by_id.keys.sort.each { |id| visit.call(id) if state[id].nil? }
    cycles.to_a.sort.map { |cycle| cycle.split("->") }
  end

  def validate_todo(path, errors, require_empty:, owned_ids: nil)
    body = read(path, errors, "todo")
    checkbox_rows = body.lines.each_with_index.filter_map do |line, index|
      match = line.match(/^\s*- \[([ xX])\]\s*(.*)$/)
      next if match.nil?

      obligation_ids = match[2].scan(OBLIGATION_ID)
      { line: index + 1, checked: match[1].downcase == "x", obligation_ids: obligation_ids }
    end

    if require_empty
      unchecked = checkbox_rows.reject { |row| row[:checked] }.map { |row| row[:line] }
      errors << "TODO_UNCHECKED: artifact=#{path} lines=#{unchecked.join(',')}" unless unchecked.empty?
      return
    end

    checked = checkbox_rows.select { |row| row[:checked] }.map { |row| row[:line] }
    errors << "TODO_PRECHECKED: artifact=#{path} lines=#{checked.join(',')}" unless checked.empty?
    return if owned_ids.nil?

    checkbox_ids = checkbox_rows.flat_map { |row| row[:obligation_ids] }
    missing = owned_ids - checkbox_ids.to_set
    foreign = checkbox_ids.to_set - owned_ids
    duplicate_ids = duplicates(checkbox_ids)
    errors << "TODO_OWNED_CHECKBOX_MISSING: artifact=#{path} ids=#{missing.to_a.sort.join(',')}" unless missing.empty?
    errors << "TODO_FOREIGN_CHECKBOX: artifact=#{path} ids=#{foreign.to_a.sort.join(',')}" unless foreign.empty?
    errors << "TODO_DUPLICATE_OBLIGATION_CHECKBOX: artifact=#{path} ids=#{duplicate_ids.join(',')}" unless duplicate_ids.empty?
  end

  def validate_schema_registry(path, roadmap_owners, errors)
    rows = markdown_table(path, SCHEMA_HEADERS, errors, "schema_registry")
    claims = []
    rows.each do |id, domain, prefix, owner, namespace, dependencies, compatibility, rollback, protocol|
      match = namespace.match(/\AV(\d+)-V(\d+)\z/)
      if match.nil? || match[1].to_i > match[2].to_i
        errors << "SCHEMA_BAD_NAMESPACE: id=#{id} value=#{namespace}"
        range_start = range_end = -1
      else
        range_start = match[1].to_i
        range_end = match[2].to_i
      end
      dependency_ids = dependencies == "-" ? [] : dependencies.split(",")
      errors << "SCHEMA_EMPTY_VALUE: id=#{id}" if [id, domain, prefix, owner, compatibility, rollback, protocol].any?(&:empty?)
      errors << "SCHEMA_UNKNOWN_OWNER: id=#{id} owner=#{owner}" unless roadmap_owners.include?(owner)
      unknown_dependencies = dependency_ids - roadmap_owners
      errors << "SCHEMA_UNKNOWN_DEPENDENCY: id=#{id} dependencies=#{unknown_dependencies.join(',')}" unless unknown_dependencies.empty?
      errors << "SCHEMA_SELF_DEPENDENCY: id=#{id}" if dependency_ids.include?(owner)
      errors << "SCHEMA_BAD_COMPATIBILITY: id=#{id}" unless compatibility == "expand-migrate-contract"
      errors << "SCHEMA_BAD_ROLLBACK: id=#{id}" unless rollback.downcase.include?("rollback")
      errors << "SCHEMA_BAD_CROSS_OWNER_PROTOCOL: id=#{id}" unless protocol.include?("DECISIONS.md")
      claims << SchemaOwner.new(
        id: id, domain: domain, prefix: prefix, owner: owner,
        range_start: range_start, range_end: range_end,
        dependencies: dependency_ids, compatibility: compatibility,
        rollback: rollback, protocol: protocol
      )
    end

    %i[id prefix].each do |field|
      values = claims.map { |claim| claim.public_send(field) }
      duplicate_values = duplicates(values)
      errors << "SCHEMA_DUPLICATE_#{field.to_s.upcase}: #{duplicate_values.join(',')}" unless duplicate_values.empty?
    end
    claims.combination(2).each do |left, right|
      next if left.range_start.negative? || right.range_start.negative?
      next if left.range_end < right.range_start || right.range_end < left.range_start

      errors << "SCHEMA_NAMESPACE_CONFLICT: #{left.id}=V#{left.range_start}-V#{left.range_end} #{right.id}=V#{right.range_start}-V#{right.range_end}"
    end
    uncovered = roadmap_owners - claims.map(&:owner).uniq
    errors << "SCHEMA_OWNER_UNCOVERED: #{uncovered.join(',')}" unless uncovered.empty?
    claims
  end

  def validate_phase_schema_claims(root, phase_dir, phase_package, registry, errors)
    design_path = File.join(phase_dir, "DESIGN.md")
    design = File.file?(design_path) ? File.read(design_path) : ""
    claims_path = File.join(phase_dir, "SCHEMA-CLAIMS.md")
    declared = design.match?(/^Schema migrations:\s*declared\s*$/i)
    none = design.match?(/^Schema migrations:\s*none\s*$/i)
    claims_exist = File.file?(claims_path)
    errors << "SCHEMA_DECLARATION_MISSING: #{design_path}" if claims_exist && !declared
    if (declared && none) || (claims_exist && none)
      errors << "SCHEMA_DECLARATION_CONTRADICTION: #{design_path}"
      return
    end
    return unless declared

    rows = markdown_table(claims_path, SCHEMA_CLAIM_HEADERS, errors, "schema_claims")
    decision_body = File.file?(File.join(phase_dir, "DECISIONS.md")) ? File.read(File.join(phase_dir, "DECISIONS.md")) : ""
    rows.each do |claim_id, object, owner, migration_id, migration_dependencies, step, rollback, approval|
      matches = registry.select { |entry| schema_prefix_match?(entry.prefix, object) }
      if matches.length != 1
        errors << "SCHEMA_CLAIM_OWNER_AMBIGUOUS: claim=#{claim_id} object=#{object} matches=#{matches.map(&:id).join(',')}"
        next
      end
      registry_entry = matches.first
      errors << "SCHEMA_CLAIM_OWNER_MISMATCH: claim=#{claim_id} expected=#{registry_entry.owner} actual=#{owner}" unless owner == registry_entry.owner
      migration_number = migration_id[/\AV(\d+)\z/, 1]&.to_i
      unless migration_number && migration_number.between?(registry_entry.range_start, registry_entry.range_end)
        errors << "SCHEMA_CLAIM_NAMESPACE_VIOLATION: claim=#{claim_id} migration=#{migration_id} namespace=V#{registry_entry.range_start}-V#{registry_entry.range_end}"
      end
      dependencies = migration_dependencies == "-" ? [] : migration_dependencies.split(",")
      bad_dependencies = dependencies.reject { |value| value.match?(/\AV\d+\z/) }
      errors << "SCHEMA_CLAIM_BAD_DEPENDENCY: claim=#{claim_id} values=#{bad_dependencies.join(',')}" unless bad_dependencies.empty?
      errors << "SCHEMA_CLAIM_BAD_STEP: claim=#{claim_id} step=#{step}" unless %w[expand migrate contract].include?(step)
      errors << "SCHEMA_CLAIM_EMPTY_ROLLBACK: claim=#{claim_id}" if rollback.empty?
      if owner != phase_package
        unless approval.match?(/\ADR-[A-Z0-9-]+\z/) && decision_body.include?(approval)
          errors << "SCHEMA_CROSS_OWNER_APPROVAL_MISSING: claim=#{claim_id} owner=#{owner} phase=#{phase_package} approval=#{approval}"
        end
      end
    end

    all_claim_files = Dir.glob(File.join(root, ".planning/phases/*/SCHEMA-CLAIMS.md"))
    all_claim_files << claims_path unless all_claim_files.include?(claims_path)
    migration_locations = Hash.new { |hash, key| hash[key] = [] }
    all_claim_files.select { |path| File.file?(path) }.each do |path|
      local_errors = []
      markdown_table(path, SCHEMA_CLAIM_HEADERS, local_errors, "schema_claims").each do |row|
        migration_locations[row[3]] << path
      end
      errors.concat(local_errors)
    end
    migration_locations.each do |migration_id, locations|
      errors << "SCHEMA_MIGRATION_DUPLICATE: migration=#{migration_id} files=#{locations.join(',')}" if locations.length > 1
    end

    sql_versions = Hash.new { |hash, key| hash[key] = [] }
    Dir.glob(File.join(root, "**/db/migration/V*__*.sql")).reject do |path|
      path.match?(%r{/(?:target|build|node_modules|\.git)/})
    end.each do |path|
      version = File.basename(path)[/\AV(\d+)__/, 1]
      sql_versions[version] << path if version
    end
    sql_versions.each do |version, paths|
      errors << "SCHEMA_SQL_MIGRATION_DUPLICATE: migration=V#{version} files=#{paths.join(',')}" if paths.length > 1
    end
    known_migrations = migration_locations.keys.to_set | sql_versions.keys.map { |version| "V#{version}" }.to_set
    rows.each do |row|
      claim_id = row[0]
      dependencies = row[4] == "-" ? [] : row[4].split(",")
      unknown_dependencies = dependencies.reject { |migration_id| known_migrations.include?(migration_id) }
      unless unknown_dependencies.empty?
        errors << "SCHEMA_CLAIM_DEPENDENCY_UNKNOWN: claim=#{claim_id} migrations=#{unknown_dependencies.join(',')}"
      end
    end
  end

  def schema_prefix_match?(prefix, object)
    base = prefix.delete_suffix("*")
    object.start_with?(base)
  end

  def source_paths(root, section, errors, label)
    sources = section["sources"]
    unless sources.is_a?(Array) && !sources.empty?
      errors << "UI_SOURCE_LIST_MISSING: section=#{label}"
      return []
    end
    sources.filter_map do |entry|
      unless entry.is_a?(Hash) && entry["path"].is_a?(String) && entry["sha256"].is_a?(String)
        errors << "UI_SOURCE_ENTRY_BAD: section=#{label} entry=#{entry.inspect}"
        next
      end
      expanded = File.expand_path(entry["path"], root)
      unless expanded.start_with?(File.expand_path(root) + File::SEPARATOR)
        errors << "UI_SOURCE_OUTSIDE_ROOT: section=#{label} path=#{entry['path']}"
        next
      end
      unless File.file?(expanded)
        errors << "UI_SOURCE_MISSING: section=#{label} path=#{entry['path']}"
        next
      end
      actual = Digest::SHA256.file(expanded).hexdigest
      errors << "UI_SOURCE_CHECKSUM_MISMATCH: section=#{label} path=#{entry['path']}" unless actual == entry["sha256"]
      expanded
    end
  end

  def validate_source_contains(paths, values, errors, label)
    body = paths.filter_map do |path|
      File.binread(path).force_encoding("UTF-8").scrub if File.file?(path)
    end.join("\n")
    missing = values.reject { |value| body.include?(value) }
    errors << "UI_SOURCE_VALUE_MISSING: section=#{label} values=#{missing.join(',')}" unless missing.empty?
  end

  def placeholder?(value)
    value.to_s.strip.empty? || %w[- n/a na tbd todo].include?(value.to_s.strip.downcase)
  end

  def comma_tokens(value)
    value.to_s.split(",").map(&:strip).reject(&:empty?)
  end

  def relative_source_path(root, path)
    Pathname(path).relative_path_from(Pathname(File.expand_path(root))).to_s
  end

  def js_code_position?(source, offset)
    quote = nil
    escaped = false
    line_comment = false
    block_comment = false
    index = 0
    while index < offset
      char = source[index]
      following = source[index + 1]
      if line_comment
        line_comment = false if char == "\n"
      elsif block_comment
        if char == "*" && following == "/"
          block_comment = false
          index += 1
        end
      elsif quote
        if escaped
          escaped = false
        elsif char == "\\"
          escaped = true
        elsif char == quote
          quote = nil
        end
      elsif char == "/" && following == "/"
        line_comment = true
        index += 1
      elsif char == "/" && following == "*"
        block_comment = true
        index += 1
      elsif ["'", '"', "`"].include?(char)
        quote = char
      end
      index += 1
    end
    quote.nil? && !line_comment && !block_comment
  end

  def syntax_match?(source, pattern)
    source.to_enum(:scan, pattern).any? do
      match = Regexp.last_match
      js_code_position?(source, match.begin(0))
    end
  end

  def validate_production_implementation_sources(root, paths, routes, selectors, errors)
    valid_paths = paths.select do |path|
      relative = relative_source_path(root, path)
      valid = relative.match?(%r{\Aweb/src/.+\.(?:tsx|ts|jsx|js)\z})
      errors << "UI_PRODUCTION_SOURCE_BAD_PATH: #{relative}" unless valid
      valid
    end
    sources = valid_paths.to_h { |path| [path, File.read(path)] }
    routes.each do |route|
      escaped = Regexp.escape(route)
      pattern = /(?:<Route\b[^>]*\bpath\s*=\s*(?:\{\s*)?["']#{escaped}["'](?:\s*\})?|(?:\A|[\n,{])\s*path\s*:\s*["']#{escaped}["'])/m
      unless sources.any? { |_path, source| syntax_match?(source, pattern) }
        errors << "UI_PRODUCTION_ROUTE_SYNTAX_MISSING: #{route}"
      end
    end
    selectors.each do |selector|
      escaped = Regexp.escape(selector)
      jsx_pattern = /data-testid\s*=\s*(?:["']#{escaped}["']|\{\s*["']#{escaped}["']\s*\})/
      create_element_pattern = /(?:React\.)?createElement\s*\([^,]+,\s*\{[^}]*["']data-testid["']\s*:\s*["']#{escaped}["']/m
      found = sources.any? do |_path, source|
        syntax_match?(source, jsx_pattern) || syntax_match?(source, create_element_pattern)
      end
      errors << "UI_PRODUCTION_TEST_ID_SYNTAX_MISSING: #{selector}" unless found
    end
  end

  def validate_playwright_sources(root, paths, selectors, errors, production:)
    valid_paths = paths.select do |path|
      relative = relative_source_path(root, path)
      valid_extension = relative.match?(%r{\.(?:spec|test)\.(?:ts|tsx|js|jsx)\z})
      valid_location = !production || relative.match?(%r{\Aweb/.+\.(?:spec|test)\.(?:ts|tsx|js|jsx)\z})
      valid = valid_extension && valid_location
      errors << "UI_PLAYWRIGHT_SOURCE_BAD_PATH: #{relative}" unless valid
      valid
    end
    sources = valid_paths.to_h { |path| [path, File.read(path)] }
    unless sources.any? { |_path, source| syntax_match?(source, /\b(?:test|it)\s*\(/) }
      errors << "UI_PLAYWRIGHT_TEST_SYNTAX_MISSING"
    end
    selectors.each do |selector|
      escaped = Regexp.escape(selector)
      getter = /getByTestId\s*\(\s*["']#{escaped}["']\s*\)/
      locator = /locator\s*\(\s*(?:'\[data-testid="#{escaped}"\]'|"\[data-testid='#{escaped}'\]"|`\[data-testid=["']#{escaped}["']\]`)\s*\)/
      unless sources.any? { |_path, source| syntax_match?(source, getter) || syntax_match?(source, locator) }
        errors << "UI_PLAYWRIGHT_SELECTOR_SYNTAX_MISSING: #{selector}"
      end
    end
  end

  def extract_test_blocks(source)
    blocks = []
    source.to_enum(:scan, /\b(?:test|it)\s*\(/).each do
      match = Regexp.last_match
      next unless js_code_position?(source, match.begin(0))

      opening = source.index("(", match.begin(0))
      depth = 0
      quote = nil
      escaped = false
      line_comment = false
      block_comment = false
      index = opening
      while index < source.length
        char = source[index]
        following = source[index + 1]
        if line_comment
          line_comment = false if char == "\n"
        elsif block_comment
          if char == "*" && following == "/"
            block_comment = false
            index += 1
          end
        elsif quote
          if escaped
            escaped = false
          elsif char == "\\"
            escaped = true
          elsif char == quote
            quote = nil
          end
        elsif char == "/" && following == "/"
          line_comment = true
          index += 1
        elsif char == "/" && following == "*"
          block_comment = true
          index += 1
        elsif ["'", '"', "`"].include?(char)
          quote = char
        elsif char == "("
          depth += 1
        elsif char == ")"
          depth -= 1
          if depth.zero?
            blocks << source[match.begin(0)..index]
            break
          end
        end
        index += 1
      end
    end
    blocks
  end

  def test_block_metadata(block)
    callback_offsets = [block.index("=>"), block.index("async function"), block.index("function")].compact
    callback_offsets.empty? ? block : block[0...callback_offsets.min]
  end

  def exact_metadata_token?(metadata, token)
    escaped = Regexp.escape(token)
    metadata.match?(/(?:\A|[^A-Za-z0-9_-])#{escaped}(?:\z|[^A-Za-z0-9_-])/)
  end

  def playwright_locator_pattern(selector)
    escaped = Regexp.escape(selector)
    getter = /(?:page\.)?getByTestId\s*\(\s*["']#{escaped}["']\s*\)/
    locator = /(?:page\.)?locator\s*\(\s*(?:'\[data-testid="#{escaped}"\]'|"\[data-testid='#{escaped}'\]"|`\[data-testid=["']#{escaped}["']\]`)\s*\)/
    [getter, locator]
  end

  def validate_playwright_matrix_blocks(paths, links, errors, label:)
    blocks = paths.flat_map { |path| extract_test_blocks(File.read(path)) }
    links.each do |link|
      metadata = blocks.to_h { |block| [block, test_block_metadata(block)] }
      playwright_blocks = blocks.select { |block| exact_metadata_token?(metadata[block], link.fetch(:playwright_id)) }
      case_blocks = blocks.select { |block| exact_metadata_token?(metadata[block], link.fetch(:case_id)) }
      obligation_blocks = blocks.select { |block| exact_metadata_token?(metadata[block], link.fetch(:obligation_id)) }
      errors << "UI_PW_BLOCK_PLAYWRIGHT_ID_MISSING: stage=#{label} id=#{link[:playwright_id]}" if playwright_blocks.empty?
      errors << "UI_PW_BLOCK_CASE_ID_MISSING: stage=#{label} id=#{link[:case_id]}" if case_blocks.empty?
      errors << "UI_PW_BLOCK_OBLIGATION_ID_MISSING: stage=#{label} id=#{link[:obligation_id]}" if obligation_blocks.empty?
      matching = playwright_blocks & case_blocks & obligation_blocks
      if matching.empty?
        errors << "UI_PW_BLOCK_ID_COMBINATION_MISSING: stage=#{label} obligation=#{link[:obligation_id]}"
        next
      end

      route = Regexp.escape(link.fetch(:route))
      with_goto = matching.select do |block|
        syntax_match?(block, /await\s+page\.goto\s*\(\s*["']#{route}["']/)
      end
      if with_goto.empty?
        errors << "UI_PW_BLOCK_GOTO_MISSING: stage=#{label} obligation=#{link[:obligation_id]} route=#{link[:route]}"
        next
      end

      getter, locator = playwright_locator_pattern(link.fetch(:selector))
      actionable = with_goto.any? do |block|
        locator_expression = "(?:#{getter.source}|#{locator.source})"
        action = /await\s+#{locator_expression}\s*\.\s*(?:click|fill|check|uncheck|selectOption|press|hover|focus)\s*\(/
        assertion = /await\s+expect\s*\(\s*#{locator_expression}\s*\)\s*\.\s*(?:toBeVisible|toBeHidden|toHaveText|toContainText|toBeEnabled|toBeDisabled|toHaveValue|toHaveAttribute|toHaveCount|toBeChecked)\s*\(/
        syntax_match?(block, action) || syntax_match?(block, assertion)
      end
      unless actionable
        errors << "UI_PW_BLOCK_ACTION_OR_ASSERTION_MISSING: stage=#{label} obligation=#{link[:obligation_id]} selector=#{link[:selector]}"
      end
    end
  end

  def validate_production_execution(root, phase_dir, execution, expected_cases, errors)
    unless execution.is_a?(Hash)
      errors << "UI_PRODUCTION_EXECUTION_MISSING"
      return
    end
    command = execution["command"].to_s
    commit = execution["commit"].to_s
    config = execution["config"].to_s
    result = execution["result"].to_s
    errors << "UI_PRODUCTION_EXECUTION_COMMAND_MISSING" if placeholder?(command) || !command.match?(/playwright/i)
    errors << "UI_PRODUCTION_EXECUTION_COMMIT_BAD: #{commit}" unless commit.match?(/\A[0-9a-f]{40}\z/i)
    errors << "UI_PRODUCTION_EXECUTION_CONFIG_MISSING" if placeholder?(config)
    errors << "UI_PRODUCTION_EXECUTION_NOT_PASS: #{result}" unless result == "PASS"

    report = execution["report"]
    unless report.is_a?(Hash) && report["path"].is_a?(String) && report["sha256"].is_a?(String)
      errors << "UI_PRODUCTION_EXECUTION_REPORT_BAD"
      return
    end
    report_path = File.expand_path(report["path"], root)
    evidence_root = File.expand_path(File.join(phase_dir, "EVIDENCE")) + File::SEPARATOR
    unless report_path.start_with?(evidence_root)
      errors << "UI_PRODUCTION_EXECUTION_REPORT_OUTSIDE_EVIDENCE: #{report['path']}"
      return
    end
    unless File.file?(report_path)
      errors << "UI_PRODUCTION_EXECUTION_REPORT_MISSING: #{report['path']}"
      return
    end
    actual_checksum = Digest::SHA256.file(report_path).hexdigest
    unless actual_checksum == report["sha256"]
      errors << "UI_PRODUCTION_EXECUTION_REPORT_CHECKSUM_MISMATCH: #{report['path']}"
      return
    end
    report_json = begin
      JSON.parse(File.read(report_path))
    rescue JSON::ParserError
      errors << "UI_PRODUCTION_EXECUTION_REPORT_JSON_BAD: #{report['path']}"
      return
    end
    {
      "command" => command,
      "commit" => commit,
      "config" => config,
      "result" => "PASS"
    }.each do |key, expected|
      errors << "UI_PRODUCTION_EXECUTION_REPORT_MISMATCH: key=#{key}" unless report_json[key] == expected
    end
    actual_cases = report_json["cases"]
    unless actual_cases.is_a?(Array) && actual_cases.all? { |value| value.is_a?(String) }
      errors << "UI_PRODUCTION_EXECUTION_REPORT_CASES_BAD"
      return
    end
    duplicate_cases = duplicates(actual_cases)
    errors << "UI_PRODUCTION_EXECUTION_REPORT_CASE_DUPLICATE: #{duplicate_cases.join(',')}" unless duplicate_cases.empty?
    missing = expected_cases - actual_cases
    stale = actual_cases - expected_cases
    errors << "UI_PRODUCTION_EXECUTION_REPORT_CASE_MISSING: #{missing.join(',')}" unless missing.empty?
    errors << "UI_PRODUCTION_EXECUTION_REPORT_CASE_STALE: #{stale.join(',')}" unless stale.empty?
  end

  def exact_json_set(section, key, expected, errors, label)
    actual = section[key]
    unless actual.is_a?(Array) && actual.all? { |value| value.is_a?(String) }
      errors << "UI_INVENTORY_BAD_ARRAY: section=#{label} key=#{key}"
      return []
    end
    duplicate_values = duplicates(actual)
    errors << "UI_INVENTORY_DUPLICATE: section=#{label} key=#{key} values=#{duplicate_values.join(',')}" unless duplicate_values.empty?
    missing = expected - actual
    stale = actual - expected
    errors << "UI_INVENTORY_MISSING: section=#{label} key=#{key} values=#{missing.join(',')}" unless missing.empty?
    errors << "UI_INVENTORY_STALE: section=#{label} key=#{key} values=#{stale.join(',')}" unless stale.empty?
    actual
  end

  def duplicates(values)
    values.group_by(&:itself).select { |value, matches| !value.to_s.empty? && matches.length > 1 }.keys
  end
end
