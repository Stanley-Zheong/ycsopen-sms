#!/usr/bin/env ruby
# frozen_string_literal: true

require "optparse"

require_relative "phase2-ui-evidence"

options = {}
OptionParser.new do |parser|
  parser.banner = "Usage: ruby .planning/tools/phase2-ui-case-runner.rb --phase 02 --case OBL-*"
  parser.on("--phase NN") { |value| options[:phase] = value }
  parser.on("--case OBLIGATION") { |value| options[:case] = value }
end.parse!

abort("PHASE2_UI_PHASE_REQUIRED") unless options[:phase] == Phase2UiEvidence::PHASE_NAME
abort("PHASE2_UI_CASE_REQUIRED") unless options[:case]&.match?(/\AOBL-[A-Z0-9-]+\z/)

root = File.expand_path("../..", __dir__)

begin
  validated = Phase2UiEvidence.validate!(root: root)
  row = validated.fetch(:rows).find { |candidate| candidate.fetch("obligation_id") == options[:case] }
  abort("PHASE2_UI_CASE_UNKNOWN: #{options[:case]}") unless row

  entry = validated.fetch(:manifest).fetch("entries").find do |candidate|
    candidate.fetch("obligation_id") == options[:case]
  end
  abort("PHASE2_UI_ENTRY_MISSING: #{options[:case]}") unless entry
  abort("PHASE2_UI_ENTRY_NOT_PASS: #{options[:case]}") unless entry["status"] == "PASS"

  puts [
    "phase2_ui_case=PASS",
    "phase=02",
    "obligation=#{row['obligation_id']}",
    "case=#{row['case_id']}",
    "playwright=#{row['playwright_id']}",
    "evidence=#{row['evidence_path']}"
  ].join(" ")
rescue StandardError => e
  warn "phase2_ui_case=BLOCKED obligation=#{options[:case]}"
  warn e.message
  exit 1
end
