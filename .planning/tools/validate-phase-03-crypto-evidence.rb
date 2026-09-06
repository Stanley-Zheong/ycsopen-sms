#!/usr/bin/env ruby
# frozen_string_literal: true

require "optparse"
require_relative "phase3-crypto-evidence"

options = {}
OptionParser.new do |parser|
  parser.banner = "Usage: ruby .planning/tools/validate-phase-03-crypto-evidence.rb --phase-dir PATH [--require-owner OWNER]"
  parser.on("--phase-dir PATH") { |value| options[:phase_dir] = value }
  parser.on("--require-owner OWNER") { |value| options[:require_owner] = value }
end.parse!

if options[:phase_dir].to_s.empty?
  warn "phase03_crypto_evidence=BLOCKED errors=1"
  warn "- OPTION_PHASE_DIR_REQUIRED"
  exit 1
end

validator = Phase3CryptoEvidence::Validator.new(
  root: Dir.pwd,
  phase_dir: options[:phase_dir],
  require_owner: options[:require_owner] || Phase3CryptoEvidence::OWNER
).validate

if validator.errors.empty?
  puts "phase03_crypto_evidence=PASS obligations=4 owner=#{Phase3CryptoEvidence::OWNER} pass_targets=4"
  exit 0
end

warn "phase03_crypto_evidence=BLOCKED errors=#{validator.errors.length}"
validator.errors.each { |error| warn "- #{error}" }
exit 1
