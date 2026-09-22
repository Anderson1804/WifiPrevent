package hev.htproxy;

/**
 * JNI contract required by hev-socks5-tunnel 2.17.1.
 * The package and method names must match the native registration table.
 */
public final class TProxyService {
    private TProxyService() {}

    static {
        System.loadLibrary("hev-socks5-tunnel");
    }

    public static native boolean TProxyStartService(String configPath, int fd);
    public static native boolean TProxyStopService();
    public static native boolean TProxyIsRunning();
    public static native long[] TProxyGetStats();
}
