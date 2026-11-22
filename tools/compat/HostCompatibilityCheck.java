package cc.ioctl.tmoe.rtti;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import cc.ioctl.tmoe.rtti.deobf.HostDexIndex;
import cc.ioctl.tmoe.rtti.deobf.SettingsDexResolver;
import cc.ioctl.tmoe.rtti.deobf.ThemeDexResolver;
import cc.ioctl.tmoe.util.Initiator;
import dalvik.system.DexClassLoader;
import org.jf.dexlib2.DexFileFactory;

/** Run in an isolated app_process; never injects hooks or opens the user's Telegram data. */
public final class HostCompatibilityCheck {
    public static void main(String[] args) {
        try {
            verify(args);
        } catch (Throwable error) {
            error.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static void verify(String[] args) throws Exception {
        ClassLoader loader = HostCompatibilityCheck.class.getClassLoader();
        Initiator.initWithHostClassLoader(loader);
        Class<?> base = loader.loadClass("org.telegram.ui.ActionBar.ActionBarLayout")
                .getMethod("getLastFragment").getReturnType();
        Class<?> actionBar = base.getDeclaredField("actionBar").getType();
        Class<?> layout = base.getDeclaredField("parentLayout").getType();
        File output = new File(args[1], "proxy.dex");
        if (output.exists() && !output.delete()) throw new IllegalStateException("Stale probe DEX");
        DexFileFactory.writeDexFile(output.getPath(),
                ProxyFragmentImplDexCreator.generateProxyDex(base, actionBar, layout));
        if (!output.setReadOnly()) throw new IllegalStateException("Cannot protect probe DEX");
        Class<?> proxy = new DexClassLoader(output.getPath(), args[1], null, loader)
                .loadClass("cc.ioctl.tmoe.dynamic.ProxyFragmentImpl");
        for (Method bridge : IProxyFragmentObject.class.getDeclaredMethods()) {
            Method emitted = proxy.getDeclaredMethod(bridge.getName(), bridge.getParameterTypes());
            if (emitted.getReturnType() != bridge.getReturnType()) throw new AssertionError(emitted);
        }
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field unsafe = unsafeClass.getDeclaredField("theUnsafe");
        unsafe.setAccessible(true);
        Object instance = unsafeClass.getMethod("allocateInstance", Class.class).invoke(unsafe.get(null), proxy);
        ((IProxyFragmentObject) instance).finishFragment$super(true);
        Object finished = base.getMethod("finishFragment", boolean.class).invoke(instance, true);
        if (!Boolean.FALSE.equals(finished)) throw new AssertionError(finished);
        System.out.println("PASS: ART loads proxy, verifies every super bridge and preserves finishFragment result");

        Class<?> lifecycle = loader.loadClass("cc.ioctl.tmoe.startup.ApplicationLifecycleResolver");
        Method resolve = lifecycle.getDeclaredMethod("resolveOnCreate", ClassLoader.class);
        resolve.setAccessible(true);
        System.out.println("PASS: application lifecycle " + resolve.invoke(null, loader));

        HostDexIndex index = HostDexIndex.open(new File(args[0]));
        SettingsDexResolver.Symbols settings = SettingsDexResolver.resolve(index);
        Method factory = HostDexIndex.resolve(settings.factory);
        Object row = factory.invoke(null, 0x7f0e0001, 0xff123456, 0xff654321, 0, "TMoe probe", null, null);
        Field id = row.getClass().getDeclaredField(settings.itemId.getName());
        id.setAccessible(true);
        if (id.getInt(row) != 0x7f0e0001) throw new AssertionError("Wrong settings ID field");
        HostDexIndex.resolve(settings.fillItems);
        HostDexIndex.resolve(settings.onClick);
        System.out.println("PASS: actual settings factory creates the expected row; callbacks resolve");
        ThemeDexResolver.Symbols theme = ThemeDexResolver.resolve(index);
        Method keyIndex = HostDexIndex.resolve(theme.keyIndex);
        Method color = HostDexIndex.resolve(theme.color);
        if (keyIndex.getReturnType() != int.class || color.getReturnType() != int.class) {
            throw new AssertionError("Theme accessors must return int");
        }
        System.out.println("PASS: theme accessors resolve; color evaluation requires the running host application");
    }
}
