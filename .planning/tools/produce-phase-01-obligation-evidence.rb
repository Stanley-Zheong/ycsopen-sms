#!/usr/bin/env ruby
# frozen_string_literal: true

require "optparse"

require_relative "verification-evidence"
require File.expand_path("../../scripts/lib/phase-01/run_checks", __dir__)

options = {}
OptionParser.new do |parser|
  parser.on("--check-manifest PATH") { |value| options[:check_manifest] = value }
  parser.on("--output-dir PATH") { |value| options[:output_dir] = value }
  parser.on("--matrix PATH") { |value| options[:matrix] = value }
  parser.on("--catalog PATH") { |value| options[:catalog] = value }
  parser.on("--runtime PATH") { |value| options[:runtime] = value }
end.parse!

required = %i[check_manifest output_dir matrix catalog runtime]
missing = required.reject { |key| options[key].is_a?(String) && !options[key].empty? }
abort("OBLIGATION_PRODUCER_ARGUMENT_REQUIRED: #{missing.join(',')}") unless missing.empty?

root = File.expand_path("../..", __dir__)
begin
  result = VerificationEvidence.build_obligation_evidence(root: root, **options)
  puts "obligation_evidence=PASS summaries=#{result.fetch('entries').length} manifest=#{File.join(options[:output_dir], 'evidence-manifest.json')}"
rescue StandardError => error
  warn "obligation_evidence=BLOCKED diagnostic=#{VerificationEvidence.redact(error.message).lines.first(20).join(';')}"
  exit 1
end
