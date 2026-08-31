#!/usr/bin/env ruby
# frozen_string_literal: true

require_relative "phase2-ui-evidence"

root = File.expand_path("../..", __dir__)

begin
  result = Phase2UiEvidence.validate!(root: root)
  puts "phase02_obligation_evidence=PASS targets=#{result[:rows].length} json=80 png=3 report_specs=#{result[:specs].length}"
rescue StandardError => e
  warn "phase02_obligation_evidence=BLOCKED"
  warn e.message
  exit 1
end
