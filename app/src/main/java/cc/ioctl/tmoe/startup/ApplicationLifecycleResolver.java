package cc.ioctl.tmoe.startup;

import java.lang.reflect.Method;

/** Manifest entry points retain their names even when R8 renames their superclasses. */
final class ApplicationLifecycleResolver {
    private ApplicationLifecycleResolver() {}

    static Method resolveOnCreate(ClassLoader loader) throws ReflectiveOperationException {
        return resolveOnCreate(loader, new String[]{
                "org.telegram.messenger.ApplicationLoaderImpl",
                "org.telegram.messenger.ApplicationLoader",
                "org.thunderdog.challegram.BaseApplication"
        });
    }

    static Method resolveOnCreate(ClassLoader loader, String[] candidates)
            throws ReflectiveOperationException {
        for (String name : candidates) {
            try {
                // getMethod returns the actual declaring superclass for inherited lifecycle methods.
                return loader.loadClass(name).getMethod("onCreate");
            } catch (ClassNotFoundException ignored) {
            }
        }
        throw new ClassNotFoundException("Unable to resolve host Application.onCreate");
    }
}
