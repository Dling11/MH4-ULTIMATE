# the native library calls these by name
-keep class org.mh4u.port.Native { *; }
-keep class org.mh4u.port.Storage { *; }
-keep class org.mh4u.port.Keyboard { *; }
-keep class org.mh4u.port.MH4UInput { *; }
# Zstandard ZIP is explicitly rejected by GameImageImporter; don't bundle a native decoder.
-dontwarn com.github.luben.zstd.ZstdInputStream
