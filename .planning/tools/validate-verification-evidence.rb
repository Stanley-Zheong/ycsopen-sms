#!/usr/bin/env ruby
# frozen_string_literal: true

require "json"
require "optparse"

require_relative "verification-evidence"

options = { envelopes: [], checks: [] }
OptionParser.new do |parser|
  parser.banner = "Usage: ruby validate-verification-evidence.rb [options]"
  parser.on("--root PATH") { |value| options[:root] = value }
  parser.on("--subject PATH") { |value| options[:subject] = value }
  parser.on("--envelope PATH") { |value| options[:envelopes] << value }
  parser.on("--aggregate PATH") { |value| options[:aggregate] = value }
  parser.on("--manifest PATH") { |value| options[:manifest] = value }
  parser.on("--require-owner OWNER") { |value| options[:require_owner] = value }
  parser.on("--check ID") { |value| options[:checks] << value }
end.parse!

root = if options[:root]
  File.expand_path(options[:root], Dir.pwd)
else
  File.expand_path("../..", __dir__)
end
errors = []

begin
  require File.join(root, "scripts/lib/phase-01/run_checks")
rescue LoadError
  errors << "EVIDENCE_REGISTRY_UNAVAILABLE"
end

registries = if defined?(Phase01RunChecks)
  Phase01RunChecks.subject_registries
else
  {}
end
contracts = defined?(Phase01RunChecks) ? Phase01RunChecks.check_contracts : {}
unless options[:checks].empty?
  unknown = options[:checks] - registries.keys
  unknown.each { |check_id| errors << "EVIDENCE_CHECK_UNKNOWN: #{check_id}" }
  registries = registries.slice(*options[:checks])
  contracts = contracts.slice(*options[:checks])
end

if options[:subject]
  begin
    subject = JSON.parse(File.read(File.expand_path(options[:subject], root)))
    errors.concat(VerificationEvidence.validate_subject_manifest(root: root, manifest: subject, registries: registries))
  rescue Errno::ENOENT
    errors << "SUBJECT_MANIFEST_MISSING: #{options[:subject]}"
  rescue JSON::ParserError
    errors << "SUBJECT_MANIFEST_JSON_INVALID"
  end
end

envelopes = []
options[:envelopes].each do |relative|
  begin
    envelope = JSON.parse(File.read(File.expand_path(relative, root)))
    envelopes << envelope
    errors.concat(
      VerificationEvidence.validate_envelope(
        root: root,
        envelope: envelope,
        registries: registries,
        subject_manifest_path: options[:subject],
        check_contracts: contracts
      )
    )
  rescue Errno::ENOENT
    errors << "EVIDENCE_FILE_MISSING: #{relative}"
  rescue JSON::ParserError
    errors << "EVIDENCE_JSON_INVALID: #{relative}"
  end
end

if options[:aggregate]
  begin
    aggregate = JSON.parse(File.read(File.expand_path(options[:aggregate], root)))
    errors.concat(VerificationEvidence.validate_aggregate(root: root, aggregate: aggregate, envelopes: envelopes))
  rescue Errno::ENOENT
    errors << "AGGREGATE_FILE_MISSING: #{options[:aggregate]}"
  rescue JSON::ParserError
    errors << "AGGREGATE_JSON_INVALID: #{options[:aggregate]}"
  end
end

if options[:manifest]
  if options[:require_owner].nil? || options[:require_owner].empty?
    errors << "EVIDENCE_MANIFEST_OWNER_REQUIRED"
  else
    begin
      manifest = JSON.parse(File.read(File.expand_path(options[:manifest], root)))
      validation = if manifest["schema_version"] == VerificationEvidence::OBLIGATION_MANIFEST_SCHEMA
        VerificationEvidence.validate_obligation_manifest(
          root: root,
          manifest: manifest,
          registries: registries,
          check_contracts: contracts,
          required_owner: options[:require_owner]
        )
      else
        VerificationEvidence.validate_evidence_manifest(
          root: root,
          manifest: manifest,
          registries: registries,
          check_contracts: contracts,
          required_owner: options[:require_owner]
        )
      end
      errors.concat(validation)
    rescue Errno::ENOENT
      errors << "EVIDENCE_MANIFEST_MISSING: #{options[:manifest]}"
    rescue JSON::ParserError
      errors << "EVIDENCE_MANIFEST_JSON_INVALID: #{options[:manifest]}"
    end
  end
end

if options[:subject].nil? && options[:envelopes].empty? && options[:aggregate].nil? && options[:manifest].nil?
  errors << "EVIDENCE_SELECTION_EMPTY"
end

if errors.empty?
  puts "verification_evidence=PASS envelopes=#{envelopes.length}"
  exit 0
end

warn "verification_evidence=BLOCKED errors=#{errors.uniq.length}"
errors.uniq.each { |error| warn "- #{error}" }
exit 1
