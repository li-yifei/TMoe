package cc.ioctl.tmoe.rtti.deobf;

import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.iface.instruction.Instruction;
import org.jf.dexlib2.iface.instruction.ReferenceInstruction;
import org.jf.dexlib2.iface.reference.Reference;
import org.jf.dexlib2.iface.reference.MethodReference;
import org.jf.dexlib2.iface.reference.StringReference;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import cc.ioctl.tmoe.util.HostInfo;
import cc.ioctl.tmoe.util.Initiator;
import cc.ioctl.tmoe.util.dex.DexMethodDescriptor;

/** Structural lookup for optimized hosts. Ambiguous matches fail before a hook is installed. */
public final class HostDexIndex {
    private static WeakReference<HostDexIndex> cached = new WeakReference<>(null);
    public final List<ClassDef> classes = new ArrayList<>();

    private HostDexIndex() {}

    public static HostDexIndex open(File apk) throws IOException {
        HostDexIndex index = new HostDexIndex();
        index.add(apk);
        return index;
    }

    private void add(File apk) throws IOException {
        var container = DexFileFactory.loadDexContainer(apk, Opcodes.getDefault());
        for (String name : container.getDexEntryNames()) {
            classes.addAll(container.getEntry(name).getDexFile().getClasses());
        }
    }

    public static synchronized HostDexIndex get() throws IOException {
        HostDexIndex index = cached.get();
        if (index == null) {
            var info = HostInfo.getApplication().getApplicationInfo();
            index = open(new File(info.sourceDir));
            if (info.splitSourceDirs != null) {
                for (String split : info.splitSourceDirs) index.add(new File(split));
            }
            cached = new WeakReference<>(index);
        }
        return index;
    }

    public List<Method> findMethods(Predicate<Method> predicate) {
        List<Method> result = new ArrayList<>();
        for (ClassDef cls : classes) {
            for (Method method : cls.getMethods()) {
                if (predicate.test(method)) result.add(method);
            }
        }
        return result;
    }

    public Method uniqueMethod(Predicate<Method> predicate) {
        return unique(findMethods(predicate));
    }

    public static <T> T unique(List<T> matches) {
        if (matches.size() != 1) {
            throw new IllegalStateException("Expected one host symbol, found " + matches.size()
                    + ": " + matches);
        }
        return matches.get(0);
    }

    public static boolean references(Method method, Predicate<Reference> predicate) {
        if (method.getImplementation() == null) return false;
        for (Instruction instruction : method.getImplementation().getInstructions()) {
            if (instruction instanceof ReferenceInstruction
                    && predicate.test(((ReferenceInstruction) instruction).getReference())) return true;
        }
        return false;
    }

    public static boolean hasString(Method method, String value) {
        return references(method, r -> r instanceof StringReference
                && ((StringReference) r).getString().equals(value));
    }

    public static java.lang.reflect.Method resolve(MethodReference method)
            throws ReflectiveOperationException {
        java.lang.reflect.Method resolved = new DexMethodDescriptor(method.toString())
                .getMethodInstance(Initiator.getHostClassLoader());
        resolved.setAccessible(true);
        return resolved;
    }
}
