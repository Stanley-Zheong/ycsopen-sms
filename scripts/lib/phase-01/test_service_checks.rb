#!/usr/bin/env ruby
# frozen_string_literal: true

require "json"
require "rbconfig"
require "tmpdir"
require_relative "service_checks"

module Phase01
  class ServiceChecksTest
    def initialize(selector)
      @selector = selector
      @assertions = 0
    end

    def run
      run_mysql if @selector == :mysql
      run_redis if @selector == :redis
      run_timezone if @selector == :timezone
      run_bounded_spawn if @selector == :bounded_spawn
      if @selector == :runtime_contract
        run_image_platform_contract
        run_bounded_spawn
      end
      puts("PHASE_01_SERVICE_CHECKS_TEST PASS selector=#{@selector} assertions=#{@assertions}")
      0
    rescue StandardError => e
      warn("PHASE_01_SERVICE_CHECKS_TEST FAIL selector=#{@selector} #{e.class}: #{e.message}")
      1
    end

    private

    def assert(condition, message)
      raise message unless condition

      @assertions += 1
    end

    def assert_error(error_id)
      yield
      raise "expected #{error_id}"
    rescue ServiceChecks::CheckError => e
      assert(e.error_id == error_id, "expected #{error_id}, got #{e.error_id}")
    end

    def run_mysql
      assert(ServiceChecks::MYSQL_IMAGE.include?("@sha256:"), "MySQL image is not digest pinned")
      assert_platform_references_are_pinned(:mysql)
      run_image_platform_contract
      run_bounded_spawn
      assert_docker_unavailable(:mysql, "linux/amd64")
      assert_error("MYSQL_ACCESS_DENIED") { ServiceChecks.validate_mysql_probe!({ "authenticated" => false }) }
      assert_error("MYSQL_READINESS_ONLY") { ServiceChecks.validate_mysql_probe!({ "authenticated" => true }) }
      assert_error("MYSQL_STALE_STATE") do
        ServiceChecks.validate_mysql_probe!(mysql_probe.merge("fresh_schema" => false))
      end
      assert_error("MYSQL_MIGRATION_CHECKSUM_MISMATCH") do
        ServiceChecks.validate_mysql_probe!(mysql_probe.merge("migration_sha256" => "0" * 64))
      end
      ServiceChecks.validate_mysql_probe!(mysql_probe)
      real = ServiceChecks.real_mysql_self_test!
      assert(real.fetch("functional") == true, "real MySQL functional check did not pass")
      assert(real.fetch("image_digest") == ServiceChecks::MYSQL_IMAGE.split("@", 2).last,
             "real MySQL image digest mismatch")
      assert(real.fetch("container_image_digest") == real.fetch("platform_image_digest"),
             "real MySQL container was not created from the approved platform child")
      assert(real.fetch("container_platform") == real.fetch("platform"),
             "real MySQL container platform identity changed")
    end

    def run_image_platform_contract
      ServiceChecks::SUPPORTED_SERVICE_PLATFORMS.each do |platform|
        %i[mysql redis].each do |service|
          contract = ServiceChecks.image_contract(service, platform)
          assert(contract.fetch("index_reference") == ServiceChecks.stable_image_reference(service),
                 "#{service} #{platform} child is not bound to its index")
          assert(contract.fetch("config_digest").match?(/\Asha256:[0-9a-f]{64}\z/),
                 "#{service} #{platform} config digest is malformed")
        end
      end
      mysql_raw = Zlib::Inflate.inflate(Base64.strict_decode64(
                                           ServiceChecks::RAW_MANIFESTS_DEFLATE_BASE64.fetch(ServiceChecks::MYSQL_IMAGE)
                                         ))
      corrupted = mysql_raw.sub('"schemaVersion":2', '"schemaVersion":1')
      assert_error("SERVICE_MANIFEST_DIGEST_MISMATCH") do
        ServiceChecks.validate_manifest_contract!(:mysql, raw_overrides: { ServiceChecks::MYSQL_IMAGE => corrupted })
      end
      with_fake_docker do |log_path|
        set_fake_platform(server: "linux/amd64", image: "linux/amd64")
        amd64_mysql = ServiceChecks.image_contract(:mysql, "linux/amd64").fetch("reference")
        mysql = ServiceChecks.inspect_image!(:mysql, amd64_mysql, expected_platform: "linux/amd64")
        assert(mysql.fetch("platform") == "linux/amd64", "amd64 image platform was not retained")
        assert(mysql.fetch("image_digest") == ServiceChecks::MYSQL_IMAGE.split("@", 2).last,
               "stable multi-platform MySQL digest changed")
        assert(mysql.fetch("platform_image_digest") == amd64_mysql.split("@", 2).last,
               "amd64 platform-manifest digest was not retained")
        assert(mysql.fetch("index_contains_platform_manifest") == true,
               "amd64 image did not retain its validated index relationship")
        assert(mysql.fetch("config_digest") == ServiceChecks.image_contract(:mysql, "linux/amd64").fetch("config_digest"),
               "amd64 image config digest changed")

        set_fake_platform(server: "linux/arm64", image: "linux/arm64")
        arm64_redis = ServiceChecks.image_contract(:redis, "linux/arm64").fetch("reference")
        redis = ServiceChecks.inspect_image!(:redis, arm64_redis, expected_platform: "linux/arm64")
        assert(redis.fetch("platform") == "linux/arm64", "arm64 image platform was not retained")

        prepared = ServiceChecks.prepare_images!(
          mysql_reference: ServiceChecks.image_contract(:mysql, "linux/arm64").fetch("reference"),
          redis_reference: arm64_redis,
          platform: "linux/arm64"
        )
        assert(prepared.keys.sort == %w[mysql redis], "native platform image preparation was incomplete")

        set_fake_platform(server: "linux/arm64", image: "linux/amd64")
        before_runner_mismatch = File.readlines(log_path, chomp: true).length
        assert_error("SERVICE_RUNNER_PLATFORM_MISMATCH") do
          ServiceChecks.prepare_images!(
            mysql_reference: amd64_mysql,
            redis_reference: ServiceChecks.image_contract(:redis, "linux/amd64").fetch("reference"),
            platform: "linux/amd64"
          )
        end
        runner_mismatch_commands = File.readlines(log_path, chomp: true).drop(before_runner_mismatch)
        assert(runner_mismatch_commands.length == 1 && runner_mismatch_commands.first.start_with?("version "),
               "runner/image mismatch reached image pull")

        set_fake_platform(server: "linux/amd64", image: "linux/arm64")
        before_opposite_arch = File.readlines(log_path, chomp: true).length
        assert_error("SERVICE_IMAGE_PLATFORM_MISMATCH") do
          ServiceChecks.start_service!(
            :mysql,
            run_id: "mysql-platform01",
            credentials: { user: "phase01_deadbeef", password: "p" * 24, root_password: "r" * 24 }
          )
        end
        opposite_arch_commands = File.readlines(log_path, chomp: true).drop(before_opposite_arch)
        assert(opposite_arch_commands.map { |line| line.split.first(2).join(" ") } == ["version --format", "image inspect"],
               "opposite-architecture fixture did not stop after image inspection")
        assert(opposite_arch_commands.none? { |line| line.start_with?("network create ") || line.start_with?("run ") },
               "opposite-architecture image reached container creation")
      end
    end

    def assert_platform_references_are_pinned(service)
      ServiceChecks::SUPPORTED_SERVICE_PLATFORMS.each do |platform|
        reference = ServiceChecks.image_contract(service, platform).fetch("reference")
        ServiceChecks.validate_image_reference!(service, reference, platform: platform)
        assert(reference.match?(/@sha256:[0-9a-f]{64}\z/), "#{service} #{platform} image is not digest pinned")
      end
    end

    def run_bounded_spawn
      assert_output_is_capped
      assert_timed_out_tree_is_reaped(:ignore_term, <<~'RUBY')
        trap("TERM") { }
        File.write(ARGV.fetch(0), Process.pid.to_s)
        loop { sleep 1 }
      RUBY
      assert_timed_out_tree_is_reaped(:grandchild, <<~'RUBY', descendants: 2)
        trap("TERM") { }
        child = fork do
          File.write(ARGV.fetch(1), Process.pid.to_s)
          loop { sleep 1 }
        end
        File.write(ARGV.fetch(0), Process.pid.to_s)
        Process.wait(child)
        loop { sleep 1 }
      RUBY
      assert_timed_out_tree_is_reaped(:no_close, <<~'RUBY', descendants: 2)
        child = fork do
          File.write(ARGV.fetch(1), Process.pid.to_s)
          sleep 60
        end
        File.write(ARGV.fetch(0), Process.pid.to_s)
        exit 0
      RUBY
      assert_timed_out_tree_is_reaped(
        :closed_pipes,
        <<~'RUBY',
          child = Process.spawn(
            "/bin/sleep", "60", in: File::NULL, out: File::NULL, err: File::NULL
          )
          File.write(ARGV.fetch(1), child.to_s)
          File.write(ARGV.fetch(0), Process.pid.to_s)
          exit 0
        RUBY
        descendants: 2,
        error_id: "SERVICE_COMMAND_DESCENDANT_REMAINED",
        timeout: 5
      )
    end

    def assert_output_is_capped
      source = <<~'RUBY'
        STDOUT.write("o" * 262_144)
        STDERR.write("e" * 262_144)
      RUBY
      stdout, stderr, status = ServiceChecks.command(
        [RbConfig.ruby, "-e", source], timeout: 5, output_limit: 4_096
      )
      assert(status.zero?, "bounded output fixture did not exit successfully")
      assert(stdout.bytesize <= 4_096 && stderr.bytesize <= 4_096, "subprocess output exceeded its cap")
      assert(stdout.end_with?("[OUTPUT_TRUNCATED]\n") && stderr.end_with?("[OUTPUT_TRUNCATED]\n"),
             "bounded output did not record truncation")
    end

    def assert_timed_out_tree_is_reaped(label, source, descendants: 1,
                                        error_id: "SERVICE_COMMAND_TIMEOUT", timeout: 0.25)
      Dir.mktmpdir("phase01-#{label}-") do |directory|
        pid_paths = descendants.times.map { |index| File.join(directory, "pid-#{index}") }
        begin
          assert_error(error_id) do
            ServiceChecks.command(
              [RbConfig.ruby, "-e", source, *pid_paths],
              timeout: timeout, term_grace: 0.1, output_limit: 4_096
            )
          end
          pids = pid_paths.map { |path| wait_for_pid(path) }
          pids.each do |pid|
            assert(wait_until_process_gone(pid), "#{label} descendant #{pid} survived bounded spawn cleanup")
          end
        ensure
          pid_paths.filter_map { |path| Integer(File.read(path), 10) if File.file?(path) }.each do |pid|
            Process.kill("KILL", pid) if process_alive?(pid)
          rescue Errno::ESRCH
            nil
          end
        end
      end
    end

    def wait_for_pid(path)
      deadline = Process.clock_gettime(Process::CLOCK_MONOTONIC) + 1
      until File.file?(path)
        raise "fixture did not publish pid: #{path}" if Process.clock_gettime(Process::CLOCK_MONOTONIC) >= deadline

        sleep 0.01
      end
      Integer(File.read(path), 10)
    end

    def wait_until_process_gone(pid)
      deadline = Process.clock_gettime(Process::CLOCK_MONOTONIC) + 1
      while process_alive?(pid) && Process.clock_gettime(Process::CLOCK_MONOTONIC) < deadline
        sleep 0.01
      end
      !process_alive?(pid)
    end

    def process_alive?(pid)
      Process.kill(0, pid)
      true
    rescue Errno::ESRCH
      false
    rescue Errno::EPERM
      true
    end

    def with_fake_docker
      prior = {
        "PHASE01_DOCKER_BIN" => ENV["PHASE01_DOCKER_BIN"],
        "PHASE01_FAKE_DOCKER_LOG" => ENV["PHASE01_FAKE_DOCKER_LOG"],
        "PHASE01_FAKE_DOCKER_SERVER_OS" => ENV["PHASE01_FAKE_DOCKER_SERVER_OS"],
        "PHASE01_FAKE_DOCKER_SERVER_ARCH" => ENV["PHASE01_FAKE_DOCKER_SERVER_ARCH"],
        "PHASE01_FAKE_DOCKER_IMAGE_OS" => ENV["PHASE01_FAKE_DOCKER_IMAGE_OS"],
        "PHASE01_FAKE_DOCKER_IMAGE_ARCH" => ENV["PHASE01_FAKE_DOCKER_IMAGE_ARCH"]
      }
      Dir.mktmpdir("phase01-fake-docker-") do |directory|
        executable = File.join(directory, "docker")
        log_path = File.join(directory, "commands.log")
        File.write(executable, <<~RUBY)
          #!#{RbConfig.ruby}
          require "json"
          File.open(ENV.fetch("PHASE01_FAKE_DOCKER_LOG"), "a") { |file| file.puts(ARGV.join(" ")) }
          if ARGV[0, 2] == ["image", "inspect"]
            reference = ARGV.fetch(2)
            image_id = reference.split("@", 2).fetch(1)
            puts([JSON.generate([reference]), image_id, ENV.fetch("PHASE01_FAKE_DOCKER_IMAGE_OS"),
                  ENV.fetch("PHASE01_FAKE_DOCKER_IMAGE_ARCH")].join("|"))
          elsif ARGV[0] == "version"
            puts([ENV.fetch("PHASE01_FAKE_DOCKER_SERVER_OS"), ENV.fetch("PHASE01_FAKE_DOCKER_SERVER_ARCH")].join("|"))
          elsif ARGV[0] == "pull"
            puts(ARGV.last)
          else
            warn("unexpected fake docker command: \#{ARGV.join(' ')}")
            exit 70
          end
        RUBY
        File.chmod(0o700, executable)
        ENV["PHASE01_DOCKER_BIN"] = executable
        ENV["PHASE01_FAKE_DOCKER_LOG"] = log_path
        yield log_path
      end
    ensure
      prior&.each { |key, value| value.nil? ? ENV.delete(key) : ENV[key] = value }
    end

    def set_fake_platform(server:, image:)
      server_os, server_arch = server.split("/", 2)
      image_os, image_arch = image.split("/", 2)
      ENV["PHASE01_FAKE_DOCKER_SERVER_OS"] = server_os
      ENV["PHASE01_FAKE_DOCKER_SERVER_ARCH"] = server_arch
      ENV["PHASE01_FAKE_DOCKER_IMAGE_OS"] = image_os
      ENV["PHASE01_FAKE_DOCKER_IMAGE_ARCH"] = image_arch
    end

    def run_redis
      assert(ServiceChecks::REDIS_IMAGE.include?("@sha256:"), "Redis image is not digest pinned")
      assert_platform_references_are_pinned(:redis)
      assert_docker_unavailable(:redis, "linux/amd64")
      assert_error("REDIS_READINESS_ONLY") { ServiceChecks.validate_redis_probe!({ "ping" => true }) }
      assert_error("REDIS_TTL_MISSING") do
        ServiceChecks.validate_redis_probe!(redis_probe.merge("ttl" => -1))
      end
      assert_error("REDIS_CROSS_RUN_KEY_VISIBLE") do
        ServiceChecks.validate_redis_probe!(redis_probe.merge("preexisting" => true))
      end
      assert_error("REDIS_DELETE_FAILED") do
        ServiceChecks.validate_redis_probe!(redis_probe.merge("deleted" => false))
      end
      assert_error("REDIS_SPRING_WIRING_MISSING") do
        ServiceChecks.validate_redis_probe!(redis_probe.merge("spring_round_trip" => false))
      end
      ServiceChecks.validate_redis_probe!(redis_probe)
      real = ServiceChecks.real_redis_self_test!
      assert(real.fetch("functional") == true, "real Redis functional check did not pass")
      assert(real.fetch("image_digest") == ServiceChecks::REDIS_IMAGE.split("@", 2).last,
             "real Redis image digest mismatch")
      assert(real.fetch("container_image_digest") == real.fetch("platform_image_digest"),
             "real Redis container was not created from the approved platform child")
      assert(real.fetch("container_platform") == real.fetch("platform"),
             "real Redis container platform identity changed")
    end

    def run_timezone
      fixture = JSON.parse(File.read(File.expand_path("../../../core/src/test/resources/verification/timezone-contract.json", __dir__)))
      ServiceChecks.validate_timezone_contract!(fixture)
      assert_error("TIMEZONE_HOST_DEFAULT_LEAK") do
        ServiceChecks.validate_timezone_contract!(fixture.merge("host_default_zone" => "Asia/Shanghai"))
      end
      assert_error("TIMEZONE_OFFSET_MISMATCH") do
        ServiceChecks.validate_timezone_contract!(fixture.merge("expected_offset" => "+09:00"))
      end
      assert_error("TIMEZONE_IANA_ZONE_MISSING") do
        ServiceChecks.validate_timezone_contract!(fixture.reject { |key, _| key == "iana_zone" })
      end
      assert_error("TIMEZONE_INSTANT_MISMATCH") do
        ServiceChecks.validate_timezone_contract!(fixture.merge("expected_instant" => "2024-01-01T00:00:00Z"))
      end
      assert_error("TIMEZONE_SESSION_PROOF_MISSING") do
        ServiceChecks.validate_timezone_contract!(fixture.merge("mysql_session_zone" => nil))
      end
      assert_error("TIMEZONE_SERIALIZED_IANA_MISSING") do
        mutated = JSON.parse(JSON.generate(fixture))
        mutated.fetch("serialized_contract").delete("iana_zone")
        ServiceChecks.validate_timezone_contract!(mutated)
      end
    end

    def mysql_probe
      {
        "authenticated" => true,
        "select_one" => 1,
        "fresh_schema" => true,
        "flyway_version" => "1",
        "migration_sha256" => ServiceChecks::MIGRATION_SHA256,
        "utf8_round_trip" => "阶段一合成验证",
        "transaction_rolled_back" => true,
        "session_identity" => "phase01@%"
      }
    end

    def assert_docker_unavailable(service, platform)
      prior = ENV["PHASE01_DOCKER_BIN"]
      ENV["PHASE01_DOCKER_BIN"] = "/phase01/not-present/docker"
      image = ServiceChecks.image_contract(service, platform).fetch("reference")
      assert_error("SERVICE_DOCKER_UNAVAILABLE") do
        ServiceChecks.inspect_image!(service, image, expected_platform: platform)
      end
    ensure
      prior.nil? ? ENV.delete("PHASE01_DOCKER_BIN") : ENV["PHASE01_DOCKER_BIN"] = prior
    end

    def redis_probe
      {
        "ping" => true,
        "preexisting" => false,
        "value" => "synthetic",
        "ttl" => 30,
        "deleted" => true,
        "spring_round_trip" => true,
        "container_identity" => "phase01-redis-synthetic01"
      }
    end
  end
end

selector = case ARGV
           when ["--mysql"] then :mysql
           when ["--redis"] then :redis
           when ["--timezone-contract"] then :timezone
           when ["--bounded-spawn"] then :bounded_spawn
           when ["--runtime-contract"] then :runtime_contract
           else
             warn("usage: #{$PROGRAM_NAME} --mysql|--redis|--timezone-contract|--bounded-spawn|--runtime-contract")
             exit 64
           end

exit Phase01::ServiceChecksTest.new(selector).run
