package cc.ioctl.tmoe.rtti;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.ViewGroup;

import org.jf.dexlib2.Opcode;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.iface.instruction.Instruction;
import org.jf.dexlib2.iface.instruction.ReferenceInstruction;
import org.jf.dexlib2.iface.reference.MethodReference;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import cc.ioctl.tmoe.util.dex.DexMethodDescriptor;

import static org.junit.Assert.*;

public class ProxyFragmentImplDexCreatorTest {
    // Signatures from standalone 12.10.1 (7038), BaseFragment.java.
    // Shared methods deliberately live in a superclass to cover inherited host APIs too.
    public static class SharedFragment {
        public void clearViews() {}
        public ActionBar createActionBar(Context context) { return null; }
        public void finishFragment() {}
        public void removeSelfFromStack() {}
        public boolean onFragmentCreate() { return true; }
        public void onFragmentDestroy() {}
        protected void resumeDelayedFragmentAnimation() {}
        public void onResume() {}
        public void onPause() {}
        public boolean isLastFragment() { return true; }
        public Activity getParentActivity() { return null; }
        public void startActivityForResult(Intent intent, int requestCode) {}
        public void dismissCurrentDialog() {}
        public boolean dismissDialogOnPause(Dialog dialog) { return true; }
        public void onBecomeFullyVisible() {}
        public void onBecomeFullyHidden() {}
        public void onConfigurationChanged(Configuration config) {}
    }

    public interface NavigationLayout {}
    public abstract static class ActionBar extends ViewGroup {
        public ActionBar(Context context) { super(context); }
    }
    public abstract static class LegacyLayout extends ViewGroup {
        public LegacyLayout(Context context) { super(context); }
    }
    public static class ModernFragment extends SharedFragment {
        public ModernFragment(Bundle args) {}
        public void setParentFragment(ModernFragment fragment) {}
        public void setParentLayout(NavigationLayout layout) {}
        public boolean finishFragment(boolean animated) { return true; }
        // Same arity, incompatible parameter: reflection order must not pick this overload.
        public void finishFragment(int unrelated) {}
        public boolean onBackPressed(boolean invoked) { return true; }
    }
    public static class LegacyFragment extends SharedFragment {
        public LegacyFragment(Bundle args) {}
        public void setParentFragment(LegacyFragment fragment) {}
        public void setParentLayout(LegacyLayout layout) {}
        public void finishFragment(boolean animated) {}
        public boolean onBackPressed() { return true; }
    }

    // Telegram 9.1 introduced INavigationLayout while finishFragment still returned void.
    public static class NavigationFragment extends SharedFragment {
        public NavigationFragment(Bundle args) {}
        public void setParentFragment(NavigationFragment fragment) {}
        public void setParentLayout(NavigationLayout layout) {}
        public void finishFragment(boolean animated) {}
        public boolean onBackPressed() { return true; }
    }
    // Telegram 9.5.2 through 12.2: boolean finishFragment, zero-argument back callback.
    public static class BooleanFinishFragment extends SharedFragment {
        public BooleanFinishFragment(Bundle args) {}
        public void setParentFragment(BooleanFinishFragment fragment) {}
        public void setParentLayout(NavigationLayout layout) {}
        public boolean finishFragment(boolean animated) { return true; }
        public boolean onBackPressed() { return true; }
    }

    @Test public void intermediateTelegramVersionsKeepTheirBackAndFinishSignatures() throws Exception {
        for (Class<?> host : new Class<?>[]{NavigationFragment.class, BooleanFinishFragment.class}) {
            ClassDef dex = ProxyFragmentImplDexCreator.generateProxyDex(host, ActionBar.class,
                    NavigationLayout.class).getClasses().iterator().next();
            for (java.lang.reflect.Method bridge : IProxyFragmentObject.class.getDeclaredMethods()) {
                assertNotNull(bridge.toString(), find(dex, bridge.getName(),
                        new DexMethodDescriptor(bridge).signature));
            }
            assertNotNull(find(dex, "onBackPressed", "()Z"));
            assertNull(find(dex, "onBackPressed", "(Z)Z"));
            assertEquals(host == NavigationFragment.class, find(dex, "finishFragment", "(Z)V") != null);
        }
    }

    private ClassDef generate(boolean modern) throws Exception {
        return ProxyFragmentImplDexCreator.generateProxyDex(
                modern ? ModernFragment.class : LegacyFragment.class, ActionBar.class,
                modern ? NavigationLayout.class : LegacyLayout.class).getClasses().iterator().next();
    }

    private static Method find(ClassDef dex, String name, String signature) {
        for (Method method : dex.getMethods()) {
            String sig = "(" + String.join("", method.getParameterTypes()) + ")" + method.getReturnType();
            if (name.equals(method.getName()) && signature.equals(sig)) return method;
        }
        return null;
    }

    private static List<Instruction> instructions(Method method) {
        assertNotNull(method);
        assertNotNull(method.getImplementation());
        List<Instruction> result = new ArrayList<>();
        method.getImplementation().getInstructions().forEach(result::add);
        return result;
    }

    @Test public void descriptorParserPreservesParametersAfterObjectsAndArrays() {
        assertEquals(List.of("Landroid/content/Intent;", "I"),
                DexMethodDescriptor.splitParameterTypes("Landroid/content/Intent;I"));
        assertEquals(List.of("I", "[Ljava/lang/String;", "[I"),
                DexMethodDescriptor.splitParameterTypes("I[Ljava/lang/String;[I"));
        assertEquals(List.of("[[I", "Z", "Ljava/lang/Object;", "F"),
                DexMethodDescriptor.splitParameterTypes("[[IZLjava/lang/Object;F"));
    }

    @Test public void implementsEverySuperBridgeOnBothHostVersions() throws Exception {
        for (boolean modern : new boolean[]{true, false}) {
            ClassDef dex = generate(modern);
            for (java.lang.reflect.Method bridge : IProxyFragmentObject.class.getDeclaredMethods()) {
                assertNotNull(bridge.toString(), find(dex, bridge.getName(),
                        new DexMethodDescriptor(bridge).signature));
            }
        }
    }

    @Test public void modernFinishDiscardsBooleanOnlyAtVoidSuperBridge() throws Exception {
        ClassDef dex = generate(true);
        List<Instruction> code = instructions(find(dex, "finishFragment$super", "(Z)V"));
        assertEquals(Opcode.INVOKE_SUPER, code.get(0).getOpcode());
        MethodReference call = (MethodReference) ((ReferenceInstruction) code.get(0)).getReference();
        assertEquals("Z", call.getReturnType());
        assertEquals(List.of("Z"), call.getParameterTypes());
        assertEquals(Opcode.RETURN_VOID, code.get(1).getOpcode());
        // The host implementation returns its actual success result to host callers.
        assertNull(find(dex, "finishFragment", "(Z)V"));
        assertNull(find(dex, "finishFragment", "(Z)Z"));
    }

    @Test public void legacyFinishStillDispatches() throws Exception {
        ClassDef dex = generate(false);
        assertNotNull(find(dex, "finishFragment", "(Z)V"));
        assertNotNull(find(dex, "finishFragment$super", "(Z)V"));
    }

    @Test public void actionBarOverrideCastsBeforeReturning() throws Exception {
        ClassDef dex = generate(true);
        String actionBarType = DexMethodDescriptor.getTypeSig(ActionBar.class);
        List<Instruction> code = instructions(find(dex, "createActionBar",
                "(Landroid/content/Context;)" + actionBarType));
        assertEquals(Opcode.CHECK_CAST, code.get(code.size() - 2).getOpcode());
        assertEquals(Opcode.RETURN_OBJECT, code.get(code.size() - 1).getOpcode());
        MethodReference dispatcher = (MethodReference) ((ReferenceInstruction) code.get(1)).getReference();
        assertEquals("Landroid/view/ViewGroup;", dispatcher.getReturnType());
    }

    @Test public void backCallbackMatchesEachHostVersion() throws Exception {
        assertNotNull(find(generate(true), "onBackPressed", "(Z)Z"));
        assertNull(find(generate(true), "onBackPressed", "()Z"));
        assertNotNull(find(generate(false), "onBackPressed", "()Z"));
        assertNull(find(generate(false), "onBackPressed", "(Z)Z"));
    }

    @Test public void navigationInterfaceParameterIsCastBeforeSuperCall() throws Exception {
        List<Instruction> code = instructions(find(generate(true), "setParentLayout$super",
                "(Landroid/view/ViewGroup;)V"));
        assertEquals(Opcode.CHECK_CAST, code.get(0).getOpcode());
        assertEquals(Opcode.INVOKE_SUPER, code.get(1).getOpcode());
        MethodReference call = (MethodReference) ((ReferenceInstruction) code.get(1)).getReference();
        assertEquals(List.of(DexMethodDescriptor.getTypeSig(NavigationLayout.class)), call.getParameterTypes());
    }
}
