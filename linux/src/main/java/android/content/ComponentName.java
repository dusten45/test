package android.content;
public final class ComponentName {
    private final String packageName,className;
    public ComponentName(String packageName,String className) { this.packageName=packageName; this.className=className; }
    public ComponentName(Context context,Class<?> type) { this(context.getPackageName(),type.getName()); }
    public String getPackageName() { return packageName; }
    public String getClassName() { return className; }
}
