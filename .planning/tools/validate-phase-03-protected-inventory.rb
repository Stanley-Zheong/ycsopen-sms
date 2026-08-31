#!/usr/bin/env ruby
# frozen_string_literal: true

require "optparse"
require_relative "phase3-protected-inventory"

options = { acceptance: false }
OptionParser.new do |parser|
  parser.banner = "Usage: ruby .planning/tools/validate-phase-03-protected-inventory.rb --manifest PATH --schema PATH --source-root PATH [--acceptance]"
  parser.on("--manifest PATH") { |value| options[:manifest] = value }
  parser.on("--schema PATH") { |value| options[:schema] = value }
  parser.on("--source-root PATH") { |value| options[:source_root] = value }
  parser.on("--acceptance") { options[:acceptance] = true }
end.parse!

missing = %i[manifest schema source_root].select { |key| options[key].to_s.empty? }
unless missing.empty?
  warn "protected_inventory=BLOCKED errors=#{missing.length}"
  missing.each { |key| warn "- OPTION_#{key.to_s.upcase}_REQUIRED" }
  exit 1
end

validator = Phase3ProtectedInventory::Validator.new(
  root: Dir.pwd,
  manifest_path: options[:manifest],
  sql_schema_path: options[:schema],
  source_root: options[:source_root],
  acceptance: options[:acceptance]
).validate

if validator.errors.empty?
  readiness = validator.blocking_surfaces.empty? ? "READY" : "BLOCKED_BY_CURRENT_IMPLEMENTATION"
  puts "protected_inventory=PASS inline_targets=#{Phase3ProtectedInventory::INLINE_TARGETS.length} " \
       "object_targets=#{Phase3ProtectedInventory::OBJECT_TARGETS.length} " \
       "digest_targets=#{Phase3ProtectedInventory::DIGEST_TARGETS.length} " \
       "source_surfaces=#{Phase3ProtectedInventory::REQUIRED_SURFACES.length} " \
       "obligation_readiness=#{readiness} blocking_surfaces=#{validator.blocking_surfaces.length}"
  exit 0
end

warn "protected_inventory=BLOCKED errors=#{validator.errors.length}"
validator.errors.each { |error| warn "- #{error}" }
exit 1
