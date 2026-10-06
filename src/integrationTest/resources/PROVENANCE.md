# Fixture provenance

Fixtures marked "upstream" are copied unmodified from https://github.com/apache/commons-compress (`src/test/resources`), licensed under the Apache License 2.0, the same licence as this project.

| Fixture | Source | Notes |
| --- | --- | --- |
| `archives/upstream-bla.tar.lzma` | upstream `bla.tar.lzma` | unmodified |
| `archives/archive.tar.lzma` | generated | `lzma -c` of `archives/archive.tar` |
| `compression/compress.txt.lzma` | generated | `lzma -c` of the decompressed `compression/compress.txt.xz` |
| `archives/upstream-bla.tar.lz4` | upstream `bla.tar.lz4` | unmodified; framed LZ4 |
| `archives/upstream-bla.tar.block_lz4` | upstream `bla.tar.block_lz4` | unmodified; block LZ4 |
| `archives/archive.tar.lz4` | generated | `FramedLZ4CompressorOutputStream` (Commons Compress 1.28.0) over `archives/archive.tar` |
| `compression/compress.txt.lz4` | generated | `FramedLZ4CompressorOutputStream` over the decompressed `compress.txt.xz` |
| `compression/compress.txt.block_lz4` | generated | `BlockLZ4CompressorOutputStream` over the decompressed `compress.txt.xz` |
| `archives/upstream-bla.tar.sz` | upstream `bla.tar.sz` | unmodified; framed Snappy |
| `compression/compress.txt.sz` | generated | `FramedSnappyCompressorOutputStream` (Commons Compress 1.28.0) over the decompressed `compress.txt.xz` |
| `archives/upstream-bla.tar.Z` | upstream `bla.tar.Z` | unmodified; Unix compress |
| `compression/lorem.deflate64` | derived from upstream `lorem-ipsum-deflate64.zip` | the raw compressed bytes of the zip's single entry `lorem-ipsum.txt` (method 9, Deflate64), taken after its local file header; uncompressed length 144060 bytes |
| `compression/upstream-brotli.testdata.compressed` | upstream `brotli.testdata.compressed` | unmodified |
| `compression/upstream-brotli.testdata.uncompressed` | upstream `brotli.testdata.uncompressed` | unmodified; expected output of the file above |
| `archives/upstream-bla.tar.br` | upstream `bla.tar.br` | unmodified; Brotli-compressed tar |
| `archives/upstream-bla.arj` | upstream `bla.arj` | unmodified |
| `archives/upstream-bla.dump` | upstream `bla.dump` | unmodified; entries: root, `lost+found/`, `test1.xml`, `test2.xml` |
| `archives/traversal.tar.Z` | generated | a tar with one entry named `../escape.txt`, compressed by a minimal compress-compatible LZW encoder (block mode, 16 bits); verified with `uncompress -c` and `tar tvf` |
| `archives/escaping-symlink.tar.Z` | generated | a tar with one symlink `link -> ../outside`, compressed the same way |
