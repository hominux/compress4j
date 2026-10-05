# Security Policy

## Supported versions

Security fixes are released for the latest `5.x` release. Older majors are not maintained.

| Version | Supported |
|---------|-----------|
| 5.x     | Yes       |
| < 5.0   | No        |

## Reporting a vulnerability

Please **do not open a public issue** for a security problem.

Report it through [GitHub's private vulnerability reporting](https://github.com/hominux/compress4j/security/advisories/new).
If that is not available to you, email the maintainers listed in `build.gradle.kts`.

Include, as far as you can:

- the affected version,
- an archive or test case that reproduces the problem,
- what an attacker gains.

We aim to acknowledge a report within 5 working days, and to ship a fix or a mitigation plan within 30 days of
confirming it. You will be credited in the advisory unless you prefer otherwise.

## Handling untrusted archives

Compress4J extracts what an archive tells it to. When the archive comes from an untrusted source:

- **Path traversal** is rejected: entry paths are resolved canonically against the output directory, so `../` entries
  and writes through a symlink that points outside the output directory both fail with `UnsafeEntryException`.
- **Escaping symlinks** are rejected by default with `UnsafeEntryException`. Set
  `escapingSymlinkPolicy(EscapingSymlinkPolicy.ALLOW)` to extract them as-is, or `RELATIVIZE_ABSOLUTE` to rewrite
  absolute targets under the output directory and still reject targets that escape it.
- **Decompression bombs** are bounded by default. Every reader (tar, zip, 7z, ar, cpio, arj, dump and `Decompressor`)
  enforces an expansion ratio of 100, checked once it has produced 1 MiB, and extraction stops after 1,000,000
  extracted entries (counted after `stripComponents` and the filter; unsupported and filtered-out entries are not
  counted). Entry and total sizes are not bounded by default. For untrusted input set `maxEntrySize` and
  `maxTotalSize`, for example `limits(ExtractionLimits.defaults().withMaxTotalSize(1024L * 1024 * 1024))`. Raise
  `maxRatio` for legitimate highly compressible data, or use `limits(ExtractionLimits.noLimits())` for trusted input
  only. Breaching a limit throws `LimitExceededException`.
- **Corrupt input** fails with `IOException`, never a parser `RuntimeException`. `.xz` and `.lzma` decoders read with
  at most 256 MiB of memory unless `memoryLimitKiB` says otherwise. Pack200 is never detected, and its decoding is not
  bounded by the limits; select it explicitly only for trusted input.
- **Security failures cannot be suppressed.** `UnsafeEntryException` (traversal, escaping symlinks) and
  `LimitExceededException` both extend `UnsafeInputException`, which no error handler can suppress.

```java
try (var extractor = TarArchiveExtractor.builder(in)
        .maxEntries(10_000)
        .maxEntrySize(100L * 1024 * 1024)
        .maxTotalSize(1024L * 1024 * 1024)
        .build()) {
    extractor.extract(outputDir);
}
```
