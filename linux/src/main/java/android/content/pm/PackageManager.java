package android.content.pm;

/** No installed-app enumeration or external Android services exist in this port. */
public class PackageManager {
    public static final int PERMISSION_GRANTED = 0, PERMISSION_DENIED = -1;
    public static final int GET_SIGNATURES = 64;
    public PackageInfo getPackageInfo(String name, int flags) throws NameNotFoundException {
        if (!"com.Paladog.KorGG".equals(name)) { throw new NameNotFoundException(name); }
        PackageInfo info = new PackageInfo();
        info.packageName = name;
        info.versionCode = 26;
        info.versionName = "2.2.0-linux";
        return info;
    }
    public ApplicationInfo getApplicationInfo(String name, int flags) throws NameNotFoundException {
        getPackageInfo(name, flags);
        ApplicationInfo info = new ApplicationInfo(); info.packageName = name; return info;
    }
    public CharSequence getApplicationLabel(ApplicationInfo info) { return "Paladog"; }
    public static class NameNotFoundException extends Exception {
        public NameNotFoundException() { }
        public NameNotFoundException(String message) { super(message); }
    }
}
