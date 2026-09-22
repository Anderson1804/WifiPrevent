# hev-socks5-tunnel

- Version: 2.17.1
- Commit: 9a06bc6e7989da54e3d32ff701ef7a7ce4995d3a
- Source: https://github.com/heiher/hev-socks5-tunnel
- License: MIT
- Android NDK: 27.3.13750724 (r27d)
- Included ABIs: arm64-v8a and x86_64

The native libraries were built from the official release source archive with:

```text
ndk-build APP_ABI="arm64-v8a x86_64" APP_MODULES=hev-socks5-tunnel REV_ID=9a06bc6
```

SHA-256 values are recorded in `checksums.sha256`. WiFiPrevent uses the library
to convert packets from Android's authorized TUN interface into SOCKS5 traffic.
Native traffic logging is disabled in the generated configuration.
