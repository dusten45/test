import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/** Load and reflect every game/engine class with JVM bytecode verification.
 * This detects invalid dex2jar translations and missing class signatures. It is
 * deliberately not a claim that all game methods have executed successfully.
 */
public final class VerifyClasses {
    public static void main(String[] arguments) throws Exception {
        int verified = 0;
        List<String> failures = new ArrayList<>();
        try (JarFile jar = new JarFile(arguments[0])) {
            for (JarEntry entry : Collections.list(jar.entries())) {
                String name = entry.getName();
                if (!name.endsWith(".class") || !(name.startsWith("com/my/")
                        || name.startsWith("com/Paladog/")
                        || name.startsWith("org/cocos2d/"))) {
                    continue;
                }
                try {
                    Class<?> type = Class.forName(name.substring(0, name.length() - 6)
                            .replace('/', '.'), false, ClassLoader.getSystemClassLoader());
                    type.getDeclaredMethods();
                    type.getDeclaredFields();
                    type.getDeclaredConstructors();
                    verified++;
                } catch (Throwable failure) {
                    failures.add(name + ": " + failure);
                }
            }
        }
        System.out.println("Verified " + verified + " game/engine classes; failures "
                + failures.size());
        failures.forEach(System.err::println);
        if (!failures.isEmpty()) {
            System.exit(1);
        }
    }
}
