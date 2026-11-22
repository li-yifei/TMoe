package cc.ioctl.tmoe.rtti.deobf;

import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.iface.reference.MethodReference;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

public final class ThemeDexResolver {
    private ThemeDexResolver() {}

    public static final class Symbols {
        public final Method keyIndex;
        public final MethodReference color;
        Symbols(Method keyIndex, MethodReference color) {
            this.keyIndex = keyIndex;
            this.color = color;
        }
    }

    public static Symbols resolve(HostDexIndex index) {
        Method keys = index.uniqueMethod(m -> m.getReturnType().equals("Landroid/util/SparseArray;")
                && m.getParameterTypes().isEmpty()
                && HostDexIndex.hasString(m, "windowBackgroundWhite")
                && HostDexIndex.hasString(m, "windowBackgroundWhiteBlackText"));
        Method keyIndex = index.uniqueMethod(m -> m.getDefiningClass().equals(keys.getDefiningClass())
                && Modifier.isStatic(m.getAccessFlags()) && m.getReturnType().equals("I")
                && m.getParameterTypes().size() == 1
                && m.getParameterTypes().get(0).equals("Ljava/lang/String;"));
        Method lastFragment = index.uniqueMethod(m ->
                m.getDefiningClass().equals("Lorg/telegram/ui/ActionBar/ActionBarLayout;")
                        && m.getName().equals("getLastFragment") && m.getParameterTypes().isEmpty());
        Method themedColor = index.uniqueMethod(m -> m.getDefiningClass().equals(lastFragment.getReturnType())
                && m.getName().equals("getThemedColor") && m.getReturnType().equals("I"));
        List<MethodReference> calls = new ArrayList<>();
        HostDexIndex.references(themedColor, r -> {
            if (r instanceof MethodReference) {
                MethodReference method = (MethodReference) r;
                if (method.getReturnType().equals("I") && method.getParameterTypes().size() == 2
                        && method.getParameterTypes().get(0).equals("I")) calls.add(method);
            }
            return false;
        });
        return new Symbols(keyIndex, HostDexIndex.unique(calls));
    }
}
