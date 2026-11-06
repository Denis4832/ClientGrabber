# Third-party data

`ProtocolDecoder.java` contains packet-name tables and selected field layouts
derived from [PrismarineJS/minecraft-data](https://github.com/PrismarineJS/minecraft-data).
The upstream project declares its license as MIT in its
[README license section](https://github.com/PrismarineJS/minecraft-data/blob/master/README.md#license).

The exact source URLs and SHA-256 hashes of the schemas used to generate the
tables are preserved in comments in `ProtocolDecoder.java`. The tables cover
Minecraft Java protocol versions 754 and 769–775.

No third-party executable libraries, Minecraft server binaries, worlds or
launcher components are included in the publication package. The runtime
implementation uses the Java standard library.

This notice identifies the upstream data; it does not assign an additional
license to the original ClientGrabber implementation.
