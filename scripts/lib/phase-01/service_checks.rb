#!/usr/bin/env ruby
# frozen_string_literal: true

require "json"
require "base64"
require "digest"
require "securerandom"
require "tempfile"
require "zlib"

module Phase01
  # OBL-FOUND-TRACE-003: real-service verification must fail closed and must
  # never convert readiness, stale state, or an unavailable daemon into PASS.
  module ServiceChecks
    class CheckError < StandardError
      attr_reader :error_id

      def initialize(error_id, message)
        @error_id = error_id
        super(message)
      end
    end

    # Stable multi-platform source identities remain the Java harness contract.
    # Execution never uses these index digests directly: the Docker server
    # platform selects one immutable platform manifest. The manifest digest
    # cryptographically binds the config and layers while remaining portable
    # across classic and containerd-backed Docker image stores (whose `.Id`
    # fields intentionally differ).
    MYSQL_IMAGE = "mysql@sha256:b3b90af2a6552ae30c266fdb7d5dd55f3afb72404bb78d37fe8a23eb857fd3fb"
    REDIS_IMAGE = "redis@sha256:efe6e2625e4601cd7119c4fb48b1c04cf3071f8b1729ede1216ceee8bc99742d"
    SERVICE_IMAGE_CONTRACTS = {
      mysql: {
        "linux/amd64" => {
          "reference" => "mysql@sha256:1d6b6a8fcee8ff758ff151d017f5203cd06792a0e698f0a593c9dfcb14609cf0",
          "config_digest" => "sha256:bced325a4ab7aec848f4688371c7433351dcb5dba26fbcc29c67727d898ae5cb"
        }.freeze,
        "linux/arm64" => {
          "reference" => "mysql@sha256:c9be23757267a888182ff13a633118a84ce7ad360abaa0f12a9c357ddf628b61",
          "config_digest" => "sha256:5e7e005a680e75d935984d3d9390990d2a709b3ed67e92708e9e6747f1f754c9"
        }.freeze
      }.freeze,
      redis: {
        "linux/amd64" => {
          "reference" => "redis@sha256:596405c58f60e287ce0d71459202aaff26d90d08590106264a5f4cc2c73308d2",
          "config_digest" => "sha256:06e204e1b5143b5ea8a807ce8aec086d341eec73a8ad3bdfa2401f25a72ceec6"
        }.freeze,
        "linux/arm64" => {
          "reference" => "redis@sha256:33d3f152f0b7c3cca14f1995e1a2071fd25db9211de747edea148b1efab69131",
          "config_digest" => "sha256:0396eccc4928863bb29bb4097ec06aeb4bf38943ef8ad2cd4957dc4f514592bf"
        }.freeze
      }.freeze
    }.freeze
    # Compressed byte-for-byte OCI documents captured from Docker Hub's
    # official registry by immutable digest. Validating the SHA-256 of each
    # inflated document makes the index -> platform child -> config relation a
    # cryptographic contract rather than four unrelated copied constants.
    RAW_MANIFESTS_DEFLATE_BASE64 = {
      MYSQL_IMAGE => "eNrtVtmO20YQ/Bf6MStqLs6h7zDyEMsIenp6VmPzUEhK9nqx/56mLAXrxFA2sDZ5yQOPAdmsZlV1kY9VB33JNM1TtXn3WEHfDzPMZeh5/Vjh0NVpwI801kPOBQu0q9LBPU11hGkXR/pUw4i7alNBl6yp7qphvK+HPfU49DOUnsapPlUsBVSnwrUz3z7tQDV2Y7TMBBH56HVUWQqSRsgoUk6NMMnHmLQC30ivDErtQBNv2mQUjUH/t4A9dMRwwwjYUlv6w+dNWE1t6a5V4kgwU+I6JZRdCbdS7q1SG8ENm1+uVY50LBOzx6VC5iCSd0IqMhaTdEY4HSMIxUxRyGiDpqSvPW4aDiMu/e/meT9ttuvt+r7Mu0OsWZnt+qs0q7bEEcaH7bp7mH5ra77jzUvBN76+KtphbL9BX6DPhjh18OsZ9Nozjnz6lRIGq6Wsnu6qP/tAJhst+IxEPmfX8E42MgnpcqOExiSsCwoE2eCzgCZoDCljlMaKgFkwfkepwNuH/UIX7PdtwZOPt+tjn+oBy7mbi9/ro/zpw8Rt3VX7FuY8jN3i+MXNZSacDyM9dzWPQ3Wyz9L9VL7wReUb93R3i5lZOjyXjJRpZBL/Oio3oOi7OPOZsnlmuNNrrC4cfU8pCiKk5EyKUUjZCNLKRpHJZTBofEMmatskNDlSY53E4DBLDybE7LRrXkupQ/+xHz71F60uyz/U8kb9oFhjZ83R/+OIUyoZYYWKjkmQiJxlJnr0HGIeIjBDMojcCAsm6RwV5RyF4YRzynqj078ecVr/H3GvEHEYIikeAJbVgfeev2g8wRqs1pLnwxvkL1vSVrApQGSpIKBuXErZKh+tfLWIW3z9bcRxUsBYoF+6Z8c/T7xwqyF6UebdgLNbZF5iH3nSIgYfk+CYSxiNixxvOZOWzulkXcoBEC3/vmhJYEAKYEsasjr+l5n3/sXYpU/0+RnwhDvq4OeLtdXT74wbWIo=",
      "mysql@sha256:1d6b6a8fcee8ff758ff151d017f5203cd06792a0e698f0a593c9dfcb14609cf0" => "eNq9VstuW0cM3ecrDGWZ+HqG89Z3BF20KAq+RrqNXr1XdusE/vdSstOkUAF7Uy0kQZzh8JA8fHx9d3OzmHmtW/xJp3nc7xbLG/h4km5VRvz0eFCTLPBw2IyMR7tw97CTYc/jMG5xpcMWd2PX+Tg8+A+/z6Z/Vub9ro8r0/xq/97+2LPav54yZRlXZuCkOa8RUl4SqwRIGJEKKtdYe8y1huK5xBBC8sKUhBByJ2ZonEuBIrVV1MT07eF5/HIClO3UBE9n5Bt8tECY9JfznWf8b/fgrG4ODEecPqy+jIcXW//lRnAZCiumCJGj96SaU2rqUcUE1MrpFMl1D7klHxyHCkk0+SzA/P3pF0diaVBKgrP46eP/7gBWxlSJs2JDrCX0Wou38CNRd0AaLSexhW6omvQSCjCk6DpQrNr8hQOWw2thF9dj7LVQqlU4RFEGT14AjT8W86ocs930BOxr6IE7hdhJKrCQtAvspYaU6rXgB+2oLbWMjlrM0nqo3ngSKBihUlVqHHx3TalmwWL5yCUZmdQ3cK5fwM++BXDxWvhrdlSMOQW8K1I0RxQr0Y7FSTbWNHWQSQszqZCxKodcc5AkTUW9u8AP2ZWr8T6xiPgq2EqvmFqpUBynbu6cGBJD6x04AFlIfQQAtdSotoJOuKUL8CFcrWahNiG1AkR02RdOKVFRLtq6o0zIEoV6tzakmXypzfhFRClHtRKOcoHdmtGp5V6N+c4zVU2xWEATuhBi1GDsSdFCbm2/enMMrUp7ijXFYEOAWwWD2NBjrpfBB38t7Bq8Ysk+cA6oLvXCLYIHygklxGKJKSDJ1womM+5UB9bUkR1Bwx4vsPsAMXjvr9Y2GymxzdlOzeDbZG1erH1qsEotbIO5WTtqDQRFsrP8aLWxljQ2rgEzXdIHgnsGb9+/nkcw7nb74xno/H2D4P12kD1/NqT73kcecXN7hj8PhPOaJv1zwInXZ1e3kr/FarGfVsP+oDvbLY447my4v7htajpcOGjh7IrE9lsDQfdOfXSenHRJLkolst0DqyXJprYPBYPax0YDuxS5vs3sDrfnpOwn5I1uxt39X8t2O2/G7av6PCkeVU7aYD3y1pVbKJ8Als7Ax59f1Z/0YXxZ9BbO9+akFmfdKWYWX6IrgQgdyKngO+cWbNd69dF5fz/x2aP18XiYl3d3q/G4vifb6LZ3z2m73Yw04fR4t32c/9gMdv7+reaXdXg9nffT5kf7J+MvfDlh+O3Z7KuvPPyzBS/M6OD94rQZvnv6G3LIMT8=",
      "mysql@sha256:c9be23757267a888182ff13a633118a84ce7ad360abaa0f12a9c357ddf628b61" => "eNq9VsluI0cMvc9XGMpxxu0qFmvTdwxySBAELJIldaIt3bITz8D/Hkq2Mwk0gH2JLpK61CTfKz4uXz/c3CxmXuuWftRpHve7xfIGPp1OtyojfX48qJ0s6HDYjExHe+HuYSfDnsdh3NJKhy3txq7zcXjwH3+bzf5szPtdH1dm+dWe3u/s2ew/rsxYxpUFOFnOa4KYllGzOhcpFac5Sg2xFpRgP6qr1QlQdrUFlZS1QnZFq6aMufueI3J9dTyPX06AUirZDp7OyDf0aBdhpz+f33nG/34GZ3MjMBxp+rj6Mh5eYn2PBnsqgiFAaqU2iNR7Ru1aSyDns4tBe4DgsSd7qRFwR82p21eH5uo31y9EMGHO1cXz8dOn/51AIQsmoYUu3eAVz4QM6qQnSIi5htBc01QKZ/CM6NgD++JjZBLoFwRKwWthD2JycCqo0rlShBiFmysSe2GvUlznTJJKZe+4taTivDYBjOxR/QX2HHKEq8EnBq+l1kaeqwgH0wQDdaUq3QGYQuzuNbVWQ6PSQxKrgZq4BC7dhQv4sQCYzK6F35vWvVI2oWjBHKoJtxHGThk5cwDSpBorYOoAEVKhgt4oSqXU6VI6kK6ne7E7TA19NrCuNzWgXbBmH7J32KOUnilpQN+qJ2pkmBmtAVFJ0vtl4YZwNeE0ZeTYDX4S6zDV18CA/iQNX8WZSjAXrpGFoiVCXRM1LWHpXBqE7zSdWhI6dz0CHhQKlqi9Y8wSOTZfowmpQRYrYOswOfTUm1WyM3S1JaycXPOiCeHy8sFfTfXWTBQlakyizSerT46mE3LVIdh4srsUMjpANUcbCAmc5YcKeeuz8fLyfXCnLgtXq1sr11YlpWpNxEaTtExo/d1OXHfeZyTxpRmnnEL0lihIYHmAbKUbfGqXfQdem6Z9/nIewbTb7Y9noPO3DYL320H2/Lsh3fc+8kib2zP8eWg0r9ukfw408fpMddomfCivY34/rYb9QXe2XRxp3Nl4fyFuhjpcUgRBlxy0XAg9c4keW7HGaUVgtZy9FYrr0SWynaM3MB02hwF7ti6FQd4Xdkfbc1r2E/FGN+Pu/q9lvZ034/ZNe56Ujiona3CQbl2+hfwZYOlsrIWf3rSf9GF8WfUWzjTnpGRnVYWJxTLocmiNHEhCrZ1TtUUqvOl03t9PfGa0Ph4P8/LubjUe1/fNdrrt3XPibjdjm2h6vNs+zn9sBvv/h/eGX5YB34RwP23+Hf8U/EUxJwy/Pod908vDP3vwwoIO3i9Ou+GHp78B4gMu+Q==",
      REDIS_IMAGE => "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",
      "redis@sha256:596405c58f60e287ce0d71459202aaff26d90d08590106264a5f4cc2c73308d2" => "eNq9ls+O20YMxu95ioVzTCzP/+H4OYIcWhQFh+TYam3ZkLzbboJ999LyJmngAt5LfRFgWuT8vpmPHH199/CwmGgre/ws49QfhsX6wX08R/fCPX56PopGFng87nrCk76wehq4O1Df9XvcSLfHoW8ynbon++GPSfPnZDoMrd9o5lf99fZil7SfSmky9xtd4Jw5bdHFtDZJnAlia7TB1ygICCaTAAoZSOyDFaHsNc6+ckMXjG0uYnakf6Rvhaf+yxkohZg08DKT7/BZN0Kjv87vXPjfrmBOVwHdCccPmy/98XWt/5LhEnmTld9HjIWkWNtiY4m1hAjWGSHIPgh4K+gYYwoFC7INWBt7/lH6VYgrWfchxTn88vF/FxAQI9tYMFIRiNlKRauick0hsPgGNrFB8L6ScOBmiJMTyEzNAPorAdaacC/4GGqqNVQPxeRsEpAjzwX04dAWUY85x8BFGJPzlsmJyY6bh5AdwhU8uLttvPKFZtmSsy00zt7bkkyIBqQqhmAiRkEBdpJNQ9E+SSLWqBA2QNfO8do32cK9BDSIoRTvAJtrzTQLjnJzpXLB1irE0pp6pTopZLSBQ3EU9BiIS0QK8UpAyXczve54zcZIiyEkSw2NU+ujGK4FDZMVtXmNMecAysw5SgImDzqfiOwVunf3QheMmbwEa2upOaipldEw6yRVEWQiRSvqn1Y9WeN9sjZmPYJaWyD07to23/tVn7/N8xOH4XCaQacf458O+44P9KeSHlrrqcfdcsafuorTto7yV4cjbWepe07h24Q+jJvucJRBL4YT9oNO5lfZmibdlUAPmHXi2JQgK7gHi+zAJYackssERbL1XlwDNC1yTNmblAtU07Tp09uWHXA/HwpL7XFYn8b+716W067f38ynUfAkfM52xqWlgaWJn4xZ+7z2/peb+aM89a+39ILUUk7nrooLzVsCjkZ7XDKCXnbgBbEkALxZdDo8jjQr2p5Ox2m9Wm360/ax6nW8X41qw2l1Obzlrq8jjs/LOdjpW+/fCrG+bNZNlsdx92+QM8Wrcc4wv19wblZ5+v4ts4AudHFxvt7fvfwDAQqWbA==",
      "redis@sha256:33d3f152f0b7c3cca14f1995e1a2071fd25db9211de747edea148b1efab69131" => "eNq9lttuIzcMhu/3KQL3ctcT6kRRfo5FL1oUBUVR9rQ+YcZJm13k3Uvb2W4LF0humhvDQ4jU92vIX/P1w93dYpaN7vhHnebxsF+s7vync3SnbeTPT0e1yIKPx+0ofLIF94/7NhxkHMYdr3XY8X7sOp+GR/fxt9nyL8ly2PdxbZlf7entxa5p/yplyW1c2wbnzHnDPuEKQkEVkVg8EYZafak1QskqgKw11h6oxKCduHlpsaTcJPbkYiq+9m+F5/HLGQhjdhZ4vpBv+ckOwqI/X9Zc+d+u4JJuAoYTTx/XX8bjy17/JcPV7AEKUXfFa/YZgmSIhAYc1ceUS0gspUKH4FigaHUuVXDsfIvpe+kXIQFcDAjlEn7+9L8LYOToXYHONdsfLdSrMbeqikAxMyhRii4Yv1cm5wBYC3DpQpHlRoAtSO8FTxkTkOauFBGjR4Mq7GNOSmgLeglMlLNgyth7zwjVnhtWQoeNb+DJ5/di7w4U0VEJoWRrjxSwJG8d4wukAkZuzV9EtATlXKVhry360NBDwqQ37N4TuJLgvQRgtYPNmZ30QsDiNGGREGxcsZScux17TeRyaBCUJAZCUBt4Zd9J8EZAebezjz1alwNoTzGiCWDwKTtWaLUwNNMC0mpKOUdrcmzWUEhNAimxiLudWv9e6L5mkUQhxdxBUhKf1HwnEECytugiWaiVKFWxIDQm1mhOlKMDcvF2Xr0DvMLb7y8X/+T9/nC6gM7f7V8Ou6Ed5HcjPfQ+ysjb5QV/HirPmzrpHwNPsrlInXYYH+mbRx+m9XA46t6uhhOPe/PmF+GWqMONRClm8601Mx2fmc0mOUuvnIlsNFDAl2CD43vLyMmcScWmGaRkR64LvG3bPe8ur6VpHXm/Ok3jn6Mu5+24ezVfJuWTtnO2B49LoCWkzwCrUFYBf3o1f9LH8eWeXog1lS9KjXLswdm7S6DqNJvVdk82+1yQiF8tOh8eJrko2pxOx3l1f78eT5uHahfy7n6yRpzvr69vuR3rxNPT8hIcbNUPb4VYXQ/rVZaHaftPkDPFS+ucYX694rxa5fHvr5kFDXFIi/MF/+H5L5sukhE="
    }.freeze
    SUPPORTED_SERVICE_PLATFORMS = %w[linux/amd64 linux/arm64].freeze
    MIGRATION_SHA256 = "fcea0ad774f8b0e245484c435ce951e0b4337b8ef837d959e2a7b184058e08a9"
    OWNER_LABEL = "com.ycsopen.phase01.owner=engineering-verification-foundation"
    RUN_ID_PATTERN = /\A[a-z0-9][a-z0-9-]{7,47}\z/
    NAME_PATTERN = /\Aphase01-(?:mysql|redis|net)-[a-z0-9][a-z0-9-]{7,47}\z/
    WAIT_ATTEMPTS = 90
    WAIT_INTERVAL = 1
    COMMAND_TIMEOUT = 120
    IMAGE_PULL_TIMEOUT = 600
    COMMAND_OUTPUT_LIMIT = 1_048_576
    COMMAND_TERM_GRACE = 0.5
    COMMAND_JOIN_GRACE = 1.0
    ROOT = File.expand_path("../../..", __dir__)
    MIGRATION_PATH = File.join(ROOT, "core/src/main/resources/db/migration/V1__init_schema.sql")

    module_function

    def image_contract(service, platform)
      contract = SERVICE_IMAGE_CONTRACTS.fetch(service.to_sym, {}).fetch(platform, nil)
      unless contract
        raise CheckError.new("SERVICE_IMAGE_PLATFORM_MISMATCH", "unsupported service image platform: #{platform}")
      end
      validate_manifest_contract!(service).fetch(platform)
    end

    def stable_image_reference(service)
      service.to_sym == :mysql ? MYSQL_IMAGE : REDIS_IMAGE
    end

    def raw_manifest_document!(reference, raw_overrides = {})
      raw = raw_overrides.fetch(reference) do
        encoded = RAW_MANIFESTS_DEFLATE_BASE64.fetch(reference)
        Zlib::Inflate.inflate(Base64.strict_decode64(encoded))
      end
      expected_digest = reference.split("@", 2).fetch(1)
      unless "sha256:#{Digest::SHA256.hexdigest(raw)}" == expected_digest
        raise CheckError.new("SERVICE_MANIFEST_DIGEST_MISMATCH", "raw manifest bytes do not match #{reference}")
      end
      JSON.parse(raw)
    rescue KeyError, JSON::ParserError, Zlib::Error, ArgumentError
      raise CheckError.new("SERVICE_MANIFEST_CONTRACT_MALFORMED", "immutable OCI manifest contract could not be decoded")
    end

    def validate_manifest_contract!(service, raw_overrides: {})
      service = service.to_sym
      index_reference = stable_image_reference(service)
      index = raw_manifest_document!(index_reference, raw_overrides)
      unless index["schemaVersion"] == 2 && index["mediaType"] == "application/vnd.oci.image.index.v1+json" &&
             index["manifests"].is_a?(Array)
        raise CheckError.new("SERVICE_IMAGE_INDEX_INVALID", "#{service} immutable source is not an OCI image index")
      end
      SERVICE_IMAGE_CONTRACTS.fetch(service).to_h do |platform, expected|
        os, architecture = platform.split("/", 2)
        descriptor = index.fetch("manifests").find do |candidate|
          candidate.dig("platform", "os") == os && candidate.dig("platform", "architecture") == architecture
        end
        child_digest = expected.fetch("reference").split("@", 2).fetch(1)
        unless descriptor && descriptor["digest"] == child_digest &&
               descriptor["mediaType"] == "application/vnd.oci.image.manifest.v1+json"
          raise CheckError.new("SERVICE_IMAGE_INDEX_CHILD_MISMATCH", "#{index_reference} does not contain #{platform} #{child_digest}")
        end
        child = raw_manifest_document!(expected.fetch("reference"), raw_overrides)
        unless child["schemaVersion"] == 2 && child["mediaType"] == descriptor["mediaType"] &&
               child.dig("config", "digest") == expected.fetch("config_digest")
          raise CheckError.new("SERVICE_IMAGE_CHILD_CONFIG_MISMATCH", "#{platform} child manifest does not bind its approved config")
        end
        [platform, expected.merge(
          "index_reference" => index_reference,
          "index_digest" => index_reference.split("@", 2).fetch(1),
          "platform" => platform,
          "index_descriptor_size" => descriptor.fetch("size")
        ).freeze]
      end.freeze
    rescue KeyError
      raise CheckError.new("SERVICE_MANIFEST_CONTRACT_MALFORMED", "immutable OCI manifest contract is missing a required field")
    end

    def validate_image_reference!(service, reference, platform:)
      expected = image_contract(service, platform).fetch("reference")
      unless reference == expected && reference.match?(/\A(?:mysql|redis)@sha256:[0-9a-f]{64}\z/)
        raise CheckError.new("SERVICE_IMAGE_NOT_PINNED", "#{service} image must equal the code-owned #{platform} digest")
      end
      true
    end

    def validate_mysql_probe!(probe)
      raise CheckError.new("MYSQL_ACCESS_DENIED", "authenticated MySQL operation did not succeed") unless probe["authenticated"]
      raise CheckError.new("MYSQL_READINESS_ONLY", "authenticated SELECT 1 did not succeed") unless probe["select_one"] == 1
      raise CheckError.new("MYSQL_STALE_STATE", "database was not fresh for this run") unless probe["fresh_schema"]
      raise CheckError.new("MYSQL_FLYWAY_V1_MISSING", "Flyway version 1 was not applied") unless probe["flyway_version"] == "1"
      unless probe["migration_sha256"] == MIGRATION_SHA256
        raise CheckError.new("MYSQL_MIGRATION_CHECKSUM_MISMATCH", "V1 migration SHA-256 does not match the code-owned value")
      end
      unless probe["utf8_round_trip"] == "阶段一合成验证"
        raise CheckError.new("MYSQL_UTF8_ROUND_TRIP_FAILED", "simplified-Chinese UTF-8 round trip failed")
      end
      unless probe["transaction_rolled_back"]
        raise CheckError.new("MYSQL_TRANSACTION_ROLLBACK_FAILED", "Spring transaction did not roll back")
      end
      unless probe["session_identity"].to_s.match?(/\Aphase01(?:_[a-z0-9]+)?@/)
        raise CheckError.new("MYSQL_SESSION_IDENTITY_MISSING", "MySQL CURRENT_USER identity was not recorded")
      end
      true
    end

    def validate_redis_probe!(probe)
      validate_redis_cli_probe!(probe)
      unless probe["spring_round_trip"]
        raise CheckError.new("REDIS_SPRING_WIRING_MISSING", "Spring StringRedisTemplate round trip did not pass")
      end
      true
    end

    def validate_redis_cli_probe!(probe)
      raise CheckError.new("REDIS_UNAVAILABLE", "Redis PING failed") unless probe["ping"]
      unless probe.key?("preexisting") && probe.key?("value") && probe.key?("ttl") && probe.key?("deleted")
        raise CheckError.new("REDIS_READINESS_ONLY", "PING alone is not Redis integration evidence")
      end
      raise CheckError.new("REDIS_CROSS_RUN_KEY_VISIBLE", "synthetic key existed before this run") if probe["preexisting"]
      raise CheckError.new("REDIS_VALUE_MISMATCH", "Redis GET did not return the synthetic value") unless probe["value"] == "synthetic"
      ttl = probe["ttl"]
      raise CheckError.new("REDIS_TTL_MISSING", "Redis TTL was absent or out of range") unless ttl.is_a?(Integer) && ttl.between?(1, 30)
      raise CheckError.new("REDIS_DELETE_FAILED", "Redis key remained after DEL") unless probe["deleted"]
      unless probe["container_identity"].to_s.start_with?("phase01-redis-")
        raise CheckError.new("REDIS_IDENTITY_MISSING", "isolated Redis container identity was not recorded")
      end
      true
    end

    def validate_timezone_contract!(contract)
      require "time"
      required = %w[schema_version host_default_zone application_zone mysql_session_zone iana_zone
                    expected_local expected_offset expected_instant]
      missing = required.reject { |key| contract.key?(key) && !contract[key].nil? && contract[key].to_s != "" }
      if missing.include?("iana_zone")
        raise CheckError.new("TIMEZONE_IANA_ZONE_MISSING", "IANA zone identity is required")
      end
      if missing.include?("mysql_session_zone")
        raise CheckError.new("TIMEZONE_SESSION_PROOF_MISSING", "MySQL session zone proof is required")
      end
      raise CheckError.new("TIMEZONE_CONTRACT_FIELD_MISSING", "missing timezone fields: #{missing.join(',')}") unless missing.empty?
      if contract["host_default_zone"] == "Asia/Shanghai"
        raise CheckError.new("TIMEZONE_HOST_DEFAULT_LEAK", "host default must deliberately differ from Asia/Shanghai")
      end
      unless contract["application_zone"] == "Asia/Shanghai" && contract["iana_zone"] == "Asia/Shanghai"
        raise CheckError.new("TIMEZONE_IANA_ZONE_MISMATCH", "application and fixture IANA zones must be Asia/Shanghai")
      end
      unless contract["mysql_session_zone"] == "Asia/Shanghai"
        raise CheckError.new("TIMEZONE_SESSION_ZONE_MISMATCH", "MySQL session zone must be Asia/Shanghai")
      end
      raise CheckError.new("TIMEZONE_OFFSET_MISMATCH", "Shanghai contract offset must be +08:00") unless contract["expected_offset"] == "+08:00"

      begin
        instant = Time.iso8601(contract["expected_instant"])
        local = Time.iso8601(contract["expected_local"])
      rescue ArgumentError
        raise CheckError.new("TIMEZONE_INSTANT_INVALID", "timezone instants must be ISO-8601")
      end
      unless instant.utc.iso8601 == "2024-02-29T16:00:00Z" && local.utc == instant.utc
        raise CheckError.new("TIMEZONE_INSTANT_MISMATCH", "local/offset and UTC instant do not represent the fixed boundary")
      end
      serialized = contract["serialized_contract"]
      unless serialized.is_a?(Hash) && serialized["iana_zone"].to_s != ""
        raise CheckError.new("TIMEZONE_SERIALIZED_IANA_MISSING", "serialized contract must retain the IANA zone")
      end
      unless serialized["iana_zone"] == contract["iana_zone"] &&
             serialized["offset"] == contract["expected_offset"] &&
             serialized["instant"] == contract["expected_instant"]
        raise CheckError.new("TIMEZONE_SERIALIZED_IDENTITY_MISMATCH", "serialized zone/offset/instant identity changed")
      end
      true
    end

    def docker_binary
      ENV.fetch("PHASE01_DOCKER_BIN", "docker")
    end

    # Run every service subprocess in its own process group. Output is drained
    # continuously but retained only up to +output_limit+. A timeout owns the
    # entire group: TERM, a bounded grace period, KILL, then bounded joins and
    # explicit pipe closure. This prevents a Docker/service helper or grandchild
    # from surviving a failed verification command or keeping a pipe open.
    def command(argv, env: {}, stdin_data: nil, timeout: COMMAND_TIMEOUT, allow_failure: false,
                output_limit: COMMAND_OUTPUT_LIMIT, term_grace: COMMAND_TERM_GRACE)
      validate_command_contract!(argv, timeout, output_limit, term_grace)
      stdin_reader, stdin_writer = IO.pipe
      stdout_reader, stdout_writer = IO.pipe
      stderr_reader, stderr_writer = IO.pipe
      pid = Process.spawn(env, *argv, in: stdin_reader, out: stdout_writer, err: stderr_writer, pgroup: true)
      close_io(stdin_reader, stdout_writer, stderr_writer)

      writer_thread = Thread.new do
        begin
          stdin_writer.write(stdin_data) if stdin_data
        rescue Errno::EPIPE, IOError
          # Child status and stderr remain authoritative when stdin closes.
        ensure
          close_io(stdin_writer)
        end
      end
      stdout_thread = bounded_reader(stdout_reader, output_limit)
      stderr_thread = bounded_reader(stderr_reader, output_limit)
      wait_thread = Thread.new { Process.wait2(pid).last }
      deadline = monotonic_now + timeout

      until wait_thread.join(0) && stdout_thread.join(0) && stderr_thread.join(0) && writer_thread.join(0)
        if monotonic_now >= deadline
          terminate_process_group(pid, term_grace)
          close_io(stdin_writer, stdout_reader, stderr_reader)
          join_threads(wait_thread, stdout_thread, stderr_thread, writer_thread)
          raise CheckError.new("SERVICE_COMMAND_TIMEOUT", "bounded service command timed out and its process group was terminated")
        end
        sleep 0.01
      end

      stdout = stdout_thread.value
      stderr = stderr_thread.value
      status = wait_thread.value
      if process_group_alive?(pid)
        terminate_process_group(pid, term_grace)
        raise CheckError.new(
          "SERVICE_COMMAND_DESCENDANT_REMAINED",
          "service command exited while a process-group descendant remained; the group was terminated"
        )
      end
      exit_code = status.exitstatus || 128 + status.termsig.to_i
      unless status.success? || allow_failure
        diagnostic = redact(stderr.empty? ? stdout : stderr)
        raise CheckError.new("SERVICE_COMMAND_FAILED", diagnostic.lines.first.to_s.strip)
      end
      [stdout, stderr, exit_code]
    rescue Errno::ENOENT
      raise CheckError.new("SERVICE_DOCKER_UNAVAILABLE", "service command executable is unavailable")
    ensure
      close_io(stdin_reader, stdin_writer, stdout_reader, stdout_writer, stderr_reader, stderr_writer)
    end

    def validate_command_contract!(argv, timeout, output_limit, term_grace)
      unless argv.is_a?(Array) && !argv.empty? && argv.first.is_a?(String) && !argv.first.empty? &&
             argv.all? { |part| part.is_a?(String) }
        raise CheckError.new("SERVICE_COMMAND_INVALID", "service command argv must contain a non-empty executable and string arguments")
      end
      unless timeout.is_a?(Numeric) && timeout.positive? && output_limit.is_a?(Integer) && output_limit.positive? &&
             term_grace.is_a?(Numeric) && term_grace >= 0
        raise CheckError.new("SERVICE_COMMAND_INVALID", "service command bounds are invalid")
      end
      true
    end

    def bounded_reader(io, output_limit)
      Thread.new do
        Thread.current.report_on_exception = false
        captured = String.new(encoding: Encoding::BINARY)
        truncated = false
        begin
          loop do
            chunk = io.readpartial(16_384)
            remaining = output_limit - captured.bytesize
            captured << chunk.byteslice(0, remaining) if remaining.positive?
            truncated ||= chunk.bytesize > [remaining, 0].max
          end
        rescue EOFError, IOError
          # EOF is the normal completion path; IOError is expected if cleanup
          # closes a pipe retained by a misbehaving descendant.
        ensure
          close_io(io)
        end
        bounded_output(captured, truncated, output_limit)
      end
    end

    def bounded_output(captured, truncated, output_limit)
      marker = "\n[OUTPUT_TRUNCATED]\n"
      if truncated && output_limit >= marker.bytesize
        captured = captured.byteslice(0, output_limit - marker.bytesize).to_s + marker
      end
      captured.force_encoding(Encoding::UTF_8).encode(
        Encoding::UTF_8, invalid: :replace, undef: :replace, replace: "?"
      )
    end

    def terminate_process_group(pid, term_grace)
      signal_process_group("TERM", pid)
      grace_deadline = monotonic_now + term_grace
      while process_group_alive?(pid) && monotonic_now < grace_deadline
        sleep 0.01
      end
      signal_process_group("KILL", pid) if process_group_alive?(pid)
    end

    def signal_process_group(signal, pid)
      Process.kill(signal, -pid)
    rescue Errno::ESRCH, Errno::EPERM
      nil
    end

    def process_group_alive?(pid)
      Process.kill(0, -pid)
      true
    rescue Errno::ESRCH
      false
    rescue Errno::EPERM
      true
    end

    def join_threads(*threads)
      threads.compact.each do |thread|
        next if thread.join(COMMAND_JOIN_GRACE)

        thread.kill
        thread.join(COMMAND_JOIN_GRACE)
      end
    end

    def close_io(*streams)
      streams.compact.each do |stream|
        stream.close unless stream.closed?
      rescue IOError
        nil
      end
    end

    def monotonic_now
      Process.clock_gettime(Process::CLOCK_MONOTONIC)
    end

    def redact(value)
      value.to_s.gsub(/(?i)(password|passwd|secret|token)(\s*[=:]\s*)\S+/, '\\1\\2[REDACTED]')
    end

    def inspect_image!(service, reference, expected_platform:)
      validate_image_reference!(service, reference, platform: expected_platform)
      contract = image_contract(service, expected_platform)
      stdout, _stderr, status = command([docker_binary, "image", "inspect", reference,
                                         "--format", "{{json .RepoDigests}}|{{.Id}}|{{.Os}}|{{.Architecture}}"],
                                        allow_failure: true)
      if !status.zero? || stdout.strip.empty?
        raise CheckError.new("SERVICE_IMAGE_UNAVAILABLE", "#{service} image digest is not available locally")
      end
      digests_json, image_id, os, architecture = stdout.strip.split("|", 4)
      digests = JSON.parse(digests_json)
      observed_reference = digests.find { |entry| entry == reference }
      observed_digest = observed_reference&.split("@", 2)&.last
      accepted_image_ids = [observed_digest, contract.fetch("config_digest")]
      unless observed_digest && accepted_image_ids.include?(image_id)
        raise CheckError.new("SERVICE_IMAGE_DIGEST_MISMATCH", "#{service} image identity does not match its pinned digest")
      end
      unless "#{os}/#{architecture}" == expected_platform
        raise CheckError.new(
          "SERVICE_IMAGE_PLATFORM_MISMATCH",
          "#{service} image must be #{expected_platform}; observed #{os}/#{architecture}"
        )
      end
      {
        "image_digest" => contract.fetch("index_digest"),
        "index_image_digest" => contract.fetch("index_digest"),
        "platform_image_digest" => observed_digest,
        "config_digest" => contract.fetch("config_digest"),
        "image_id" => image_id,
        "platform" => "#{os}/#{architecture}",
        "index_contains_platform_manifest" => true
      }
    rescue JSON::ParserError
      raise CheckError.new("SERVICE_IMAGE_IDENTITY_MALFORMED", "Docker image identity output was malformed")
    end

    def docker_server_platform!
      stdout, _stderr, status = command(
        [docker_binary, "version", "--format", "{{.Server.Os}}|{{.Server.Arch}}"],
        allow_failure: true
      )
      observed = stdout.strip.tr("|", "/")
      unless status.zero? && SUPPORTED_SERVICE_PLATFORMS.include?(observed)
        observed = "unavailable" if observed.empty?
        raise CheckError.new(
          "SERVICE_RUNNER_PLATFORM_MISMATCH",
          "Docker server must be one of #{SUPPORTED_SERVICE_PLATFORMS.join(',')}; observed #{observed}"
        )
      end
      observed
    end

    def prepare_images!(mysql_reference:, redis_reference:, platform:)
      validate_image_reference!(:mysql, mysql_reference, platform: platform)
      validate_image_reference!(:redis, redis_reference, platform: platform)
      observed_platform = docker_server_platform!
      unless observed_platform == platform
        raise CheckError.new(
          "SERVICE_RUNNER_PLATFORM_MISMATCH",
          "requested service platform #{platform} differs from Docker server #{observed_platform}"
        )
      end
      [[:mysql, mysql_reference], [:redis, redis_reference]].to_h do |service, reference|
        command(
          [docker_binary, "pull", "--platform", platform, reference],
          timeout: IMAGE_PULL_TIMEOUT
        )
        [service.to_s, inspect_image!(service, reference, expected_platform: platform)]
      end
    end

    def validate_run_id!(run_id)
      raise CheckError.new("SERVICE_RUN_ID_INVALID", "run ID is outside the safe synthetic grammar") unless RUN_ID_PATTERN.match?(run_id)
    end

    def names(service, run_id)
      validate_run_id!(run_id)
      ["phase01-#{service}-#{run_id}", "phase01-net-#{run_id}"]
    end

    def create_network!(network, run_id)
      command([docker_binary, "network", "create", "--label", OWNER_LABEL,
               "--label", "com.ycsopen.phase01.run=#{run_id}", network])
    end

    def start_service!(service, run_id:, credentials: {})
      service = service.to_sym
      raise CheckError.new("SERVICE_ARGUMENT_INVALID", "unsupported service") unless %i[mysql redis].include?(service)
      platform = docker_server_platform!
      reference = image_contract(service, platform).fetch("reference")
      identity = inspect_image!(service, reference, expected_platform: platform)
      container, network = names(service, run_id)
      create_network!(network, run_id)
      begin
        if service == :mysql
          start_mysql!(container, network, run_id, credentials, reference)
          verify_ephemeral_mounts!(container)
          wait_for_mysql!(container, credentials.fetch(:root_password))
          load_mysql_timezone_tables!(container, credentials.fetch(:root_password))
          port = published_port!(container, 3306)
        else
          start_redis!(container, network, run_id, reference)
          verify_ephemeral_mounts!(container)
          wait_for_redis!(container)
          port = published_port!(container, 6379)
        end
        container_identity = verify_container_image_identity!(container, reference, identity)
        identity.merge(
          "schema_version" => "phase01-service-v1", "status" => "READY",
          "service" => service.to_s, "run_id" => run_id,
          "container_name" => container, "network_name" => network,
          "container_image_digest" => container_identity.fetch("digest"),
          "container_config_image" => container_identity.fetch("config_image"),
          "container_platform" => container_identity.fetch("platform"),
          "host" => "127.0.0.1", "port" => port,
          "migration_sha256" => MIGRATION_SHA256
        )
      rescue StandardError
        cleanup_service!(service, run_id: run_id)
        raise
      end
    end

    def verify_container_image_identity!(container, reference, image_identity)
      stdout, _stderr, status = command(
        [docker_binary, "container", "inspect", container, "--format",
         "{{json .ImageManifestDescriptor}}|{{json .Config.Image}}|{{.Image}}"],
        allow_failure: true
      )
      unless status.zero? && !stdout.strip.empty?
        raise CheckError.new("SERVICE_CONTAINER_IMAGE_IDENTITY_MISSING", "container image descriptor is unavailable")
      end
      descriptor_json, config_image_json, image_id = stdout.strip.split("|", 3)
      descriptor = JSON.parse(descriptor_json)
      config_image = JSON.parse(config_image_json)
      descriptor_platform = "#{descriptor.dig('platform', 'os')}/#{descriptor.dig('platform', 'architecture')}"
      unless descriptor["digest"] == image_identity.fetch("platform_image_digest") &&
             descriptor_platform == image_identity.fetch("platform") && config_image == reference &&
             [image_identity.fetch("platform_image_digest"), image_identity.fetch("config_digest")].include?(image_id)
        raise CheckError.new("SERVICE_CONTAINER_IMAGE_IDENTITY_MISMATCH", "running container is not bound to the approved platform child manifest")
      end
      { "digest" => descriptor.fetch("digest"), "config_image" => config_image, "platform" => descriptor_platform }
    rescue JSON::ParserError, KeyError
      raise CheckError.new("SERVICE_CONTAINER_IMAGE_IDENTITY_MALFORMED", "container image descriptor was malformed")
    end

    def start_mysql!(container, network, run_id, credentials, reference)
      user = credentials.fetch(:user)
      password = credentials.fetch(:password)
      root_password = credentials.fetch(:root_password)
      unless user.match?(/\Aphase01_[a-z0-9]{8}\z/) && password.length >= 24 && root_password.length >= 24
        raise CheckError.new("MYSQL_TEST_CREDENTIAL_INVALID", "ephemeral MySQL credentials do not satisfy the test-only contract")
      end

      env_file = Tempfile.new(["phase01-mysql-", ".env"])
      begin
        env_file.chmod(0o600)
        env_file.write("MYSQL_DATABASE=phase01\nMYSQL_USER=#{user}\nMYSQL_PASSWORD=#{password}\nMYSQL_ROOT_PASSWORD=#{root_password}\n")
        env_file.flush
        command([docker_binary, "run", "--detach", "--rm", "--name", container, "--network", network,
                 "--label", OWNER_LABEL, "--label", "com.ycsopen.phase01.run=#{run_id}",
                 "--env-file", env_file.path, "--publish", "127.0.0.1::3306",
                 "--tmpfs", "/var/lib/mysql:rw,nosuid,nodev,noexec,size=1g", reference,
                 "--character-set-server=utf8mb4", "--collation-server=utf8mb4_0900_ai_ci"])
      ensure
        env_file.close!
      end
    end

    def start_redis!(container, network, run_id, reference)
      command([docker_binary, "run", "--detach", "--rm", "--name", container, "--network", network,
               "--label", OWNER_LABEL, "--label", "com.ycsopen.phase01.run=#{run_id}",
               "--publish", "127.0.0.1::6379", "--tmpfs", "/data:rw,nosuid,nodev,noexec,size=128m", reference,
               "redis-server", "--save", "", "--appendonly", "no"])
    end

    def wait_for_mysql!(container, root_password)
      WAIT_ATTEMPTS.times do
        logs, log_error, log_status = command([docker_binary, "logs", container],
                                               allow_failure: true, timeout: 10)
        unless log_status.zero? && (logs + log_error).include?("MySQL init process done. Ready for start up.")
          sleep WAIT_INTERVAL
          next
        end
        stdout, _stderr, status = command(
          [docker_binary, "exec", "--env", "MYSQL_PWD", container,
           "mysql", "-N", "-B", "-uroot", "-e", "SELECT 1;"],
          env: { "MYSQL_PWD" => root_password }, allow_failure: true, timeout: 10
        )
        return true if status.zero? && stdout.strip == "1"
        sleep WAIT_INTERVAL
      end
      raise CheckError.new("MYSQL_UNAVAILABLE", "authenticated MySQL SELECT 1 did not become reachable")
    end

    def wait_for_redis!(container)
      WAIT_ATTEMPTS.times do
        stdout, _stderr, status = command([docker_binary, "exec", container, "redis-cli", "PING"],
                                           allow_failure: true, timeout: 10)
        return true if status.zero? && stdout.strip == "PONG"
        sleep WAIT_INTERVAL
      end
      raise CheckError.new("REDIS_UNAVAILABLE", "Redis did not become functionally reachable")
    end

    def load_mysql_timezone_tables!(container, root_password)
      present, = mysql_cli!(container, root_password,
                            "SELECT COUNT(*) FROM mysql.time_zone_name WHERE Name='Asia/Shanghai';",
                            database: "mysql")
      return true if present.strip == "1"

      timezone_sql, _stderr, status = command(
        [docker_binary, "exec", container, "mysql_tzinfo_to_sql",
         "/usr/share/zoneinfo/Asia/Shanghai", "Asia/Shanghai"],
        allow_failure: true, timeout: COMMAND_TIMEOUT
      )
      unless status.zero? && timezone_sql.include?("INSERT INTO time_zone")
        raise CheckError.new("MYSQL_TIMEZONE_SOURCE_UNAVAILABLE", "container timezone source could not be converted")
      end
      _stdout, _stderr, import_status = command(
        [docker_binary, "exec", "--interactive", "--env", "MYSQL_PWD", container,
         "mysql", "-uroot", "mysql", "--default-character-set=utf8mb4"],
        env: { "MYSQL_PWD" => root_password }, stdin_data: timezone_sql,
        allow_failure: true, timeout: COMMAND_TIMEOUT
      )
      raise CheckError.new("MYSQL_TIMEZONE_IMPORT_FAILED", "IANA timezone tables could not be loaded") unless import_status.zero?
      true
    end

    def published_port!(container, internal_port)
      stdout, = command([docker_binary, "port", container, "#{internal_port}/tcp"])
      match = stdout.lines.map(&:strip).find { |line| line.match?(/\A127\.0\.0\.1:\d+\z/) }
      raise CheckError.new("SERVICE_PORT_IDENTITY_MISSING", "loopback-only published port was not found") unless match
      Integer(match.split(":").last, 10)
    end

    def verify_ephemeral_mounts!(container)
      stdout, = command([docker_binary, "container", "inspect", container,
                         "--format", "{{json .Mounts}}"])
      mounts = JSON.parse(stdout)
      persistent = mounts.select { |mount| %w[volume bind].include?(mount["Type"]) }
      unless persistent.empty?
        raise CheckError.new("SERVICE_PERSISTENT_MOUNT_FORBIDDEN", "service container contains a volume or bind mount")
      end
      true
    rescue JSON::ParserError
      raise CheckError.new("SERVICE_MOUNT_IDENTITY_MALFORMED", "container mount identity was malformed")
    end

    def cleanup_service!(service, run_id:)
      container, network = names(service, run_id)
      unless NAME_PATTERN.match?(container) && NAME_PATTERN.match?(network)
        raise CheckError.new("SERVICE_CLEANUP_TARGET_INVALID", "cleanup targets are outside the owned namespace")
      end
      command([docker_binary, "rm", "--force", "--volumes", container], allow_failure: true, timeout: 30)
      command([docker_binary, "network", "rm", network], allow_failure: true, timeout: 30)
      true
    end

    def assert_cleaned!(service, run_id:)
      container, network = names(service, run_id)
      _out, _err, container_status = command([docker_binary, "container", "inspect", container], allow_failure: true)
      _out, _err, network_status = command([docker_binary, "network", "inspect", network], allow_failure: true)
      unless !container_status.zero? && !network_status.zero?
        raise CheckError.new("SERVICE_CLEANUP_FAILED", "owned container or network remained after cleanup")
      end
      true
    end

    def mysql_cli!(container, root_password, sql, database: "phase01", allow_failure: false)
      command([docker_binary, "exec", "--env", "MYSQL_PWD", container,
               "mysql", "-N", "-B", "-uroot", "--default-character-set=utf8mb4", "-D", database, "-e", sql],
              env: { "MYSQL_PWD" => root_password }, allow_failure: allow_failure, timeout: 30)
    end

    def real_mysql_self_test!
      run_id = "mysql-#{SecureRandom.hex(6)}"
      credentials = {
        user: "phase01_#{SecureRandom.hex(4)}",
        password: SecureRandom.base64(24), root_password: SecureRandom.base64(24)
      }
      session = start_service!(:mysql, run_id: run_id, credentials: credentials)
      begin
        _wrong_out, _wrong_error, wrong_status = mysql_cli!(
          session.fetch("container_name"), "wrong-#{SecureRandom.hex(12)}", "SELECT 1;", allow_failure: true
        )
        raise CheckError.new("MYSQL_ACCESS_DENIED_MUTATION_FAILED", "wrong credentials unexpectedly succeeded") if wrong_status.zero?
        table_count, = mysql_cli!(session.fetch("container_name"), credentials.fetch(:root_password),
                                  "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='phase01';")
        raise CheckError.new("MYSQL_STALE_STATE", "fresh disposable database already contained tables") unless table_count.strip == "0"
        stdout, = mysql_cli!(session.fetch("container_name"), credentials.fetch(:root_password),
                             "SELECT 1, DATABASE(), CURRENT_USER(), @@version, @@character_set_connection;")
        fields = stdout.strip.split("\t")
        unless fields.length == 5 && fields[0] == "1" && fields[1] == "phase01" && fields[2].start_with?("root@") && fields[4] == "utf8mb4"
          raise CheckError.new("MYSQL_FUNCTIONAL_PROBE_FAILED", "authenticated MySQL identity probe did not match")
        end
        session.merge("functional" => true, "server_version" => fields[3], "session_identity" => fields[2])
      ensure
        cleanup_service!(:mysql, run_id: run_id)
        assert_cleaned!(:mysql, run_id: run_id)
      end
    end

    def real_redis_self_test!
      run_id = "redis-#{SecureRandom.hex(6)}"
      session = start_service!(:redis, run_id: run_id)
      key = "phase01:#{SecureRandom.hex(12)}"
      container = session.fetch("container_name")
      begin
        preexisting, = command([docker_binary, "exec", container, "redis-cli", "EXISTS", key])
        raise CheckError.new("REDIS_CROSS_RUN_KEY_VISIBLE", "random key existed before SET") unless preexisting.strip == "0"
        set, = command([docker_binary, "exec", container, "redis-cli", "SET", key, "synthetic", "EX", "30", "NX"])
        value, = command([docker_binary, "exec", container, "redis-cli", "GET", key])
        ttl, = command([docker_binary, "exec", container, "redis-cli", "TTL", key])
        deleted, = command([docker_binary, "exec", container, "redis-cli", "DEL", key])
        absent, = command([docker_binary, "exec", container, "redis-cli", "EXISTS", key])
        probe = {
          "ping" => true, "preexisting" => false, "value" => value.strip,
          "ttl" => Integer(ttl.strip, 10), "deleted" => deleted.strip == "1" && absent.strip == "0",
          "container_identity" => container
        }
        validate_redis_cli_probe!(probe)
        version, = command([docker_binary, "exec", container, "redis-cli", "INFO", "server"])
        server_version = version.lines.find { |line| line.start_with?("redis_version:") }.to_s.split(":", 2).last.to_s.strip
        raise CheckError.new("REDIS_SET_FAILED", "Redis SET did not return OK") unless set.strip == "OK"
        session.merge("functional" => true, "server_version" => server_version)
      ensure
        cleanup_service!(:redis, run_id: run_id)
        assert_cleaned!(:redis, run_id: run_id)
      end
    end

    def cli_options(argv)
      command_name = argv.shift
      options = {}
      until argv.empty?
        flag = argv.shift
        value = argv.shift
        raise CheckError.new("SERVICE_ARGUMENT_INVALID", "arguments must be flag/value pairs") unless flag&.start_with?("--") && value
        options[flag.delete_prefix("--").tr("-", "_").to_sym] = value
      end
      [command_name, options]
    end

    def run_cli(argv, out: $stdout, err: $stderr)
      command_name, options = cli_options(argv.dup)
      case command_name
      when "prepare-images"
        identities = prepare_images!(
          mysql_reference: options.fetch(:mysql_image),
          redis_reference: options.fetch(:redis_image),
          platform: options.fetch(:platform)
        )
        out.puts(JSON.generate(
                   "status" => "READY", "platform" => options.fetch(:platform),
                   "images" => identities
                 ))
      when "start"
        service = options.fetch(:service).to_sym
        run_id = options.fetch(:run_id)
        credentials = if service == :mysql
                        {
                          user: ENV.fetch("PHASE01_MYSQL_USER"),
                          password: ENV.fetch("PHASE01_MYSQL_PASSWORD"),
                          root_password: ENV.fetch("PHASE01_MYSQL_ROOT_PASSWORD")
                        }
                      else
                        {}
                      end
        out.puts(JSON.generate(start_service!(service, run_id: run_id, credentials: credentials)))
      when "stop"
        service = options.fetch(:service).to_sym
        run_id = options.fetch(:run_id)
        cleanup_service!(service, run_id: run_id)
        assert_cleaned!(service, run_id: run_id)
        out.puts(JSON.generate("status" => "CLEANED", "service" => service.to_s, "run_id" => run_id))
      else
        raise CheckError.new("SERVICE_ARGUMENT_INVALID", "expected prepare-images, start, or stop")
      end
      0
    rescue KeyError, ArgumentError => e
      err.puts(JSON.generate("status" => "FAIL", "error_id" => "SERVICE_ARGUMENT_INVALID", "diagnostic" => redact(e.message)))
      64
    rescue CheckError => e
      status = e.error_id.match?(/UNAVAILABLE|TIMEOUT|RUNNER_PLATFORM|DESCENDANT_REMAINED/) ? "BLOCKED" : "FAIL"
      err.puts(JSON.generate("status" => status, "error_id" => e.error_id, "diagnostic" => redact(e.message)))
      2
    end
  end
end

if $PROGRAM_NAME == __FILE__
  exit Phase01::ServiceChecks.run_cli(ARGV)
end
