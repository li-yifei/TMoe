package cc.ioctl.tmoe.rtti.deobf;

import org.jf.dexlib2.Opcode;
import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.iface.instruction.Instruction;
import org.jf.dexlib2.iface.instruction.ReferenceInstruction;
import org.jf.dexlib2.iface.instruction.TwoRegisterInstruction;
import org.jf.dexlib2.iface.reference.FieldReference;
import org.jf.dexlib2.iface.reference.MethodReference;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Resolves the SettingsActivity callbacks after R8 staticization and argument removal. */
public final class SettingsDexResolver {
    private SettingsDexResolver() {}

    public static final class Symbols {
        public final Method factory, fillItems, onClick;
        public final FieldReference itemId;

        Symbols(Method factory, Method fillItems, Method onClick, FieldReference itemId) {
            this.factory = factory;
            this.fillItems = fillItems;
            this.onClick = onClick;
            this.itemId = itemId;
        }
    }

    public static Symbols resolve(HostDexIndex index) {
        Method fill = index.uniqueMethod(m -> m.getDefiningClass().startsWith("Lorg/telegram/ui/")
                && m.getReturnType().equals("V")
                && m.getParameterTypes().contains("Ljava/util/ArrayList;")
                && HostDexIndex.hasString(m, "PREMIUM_GRACE")
                && HostDexIndex.hasString(m, "VALIDATE_PHONE_NUMBER"));
        List<String> factoryParameters = Arrays.asList("I", "I", "I", "I",
                "Ljava/lang/CharSequence;", "Ljava/lang/CharSequence;", "Ljava/lang/CharSequence;");
        Method factory = index.uniqueMethod(m -> Modifier.isStatic(m.getAccessFlags())
                && m.getParameterTypes().equals(factoryParameters)
                && HostDexIndex.references(fill, r -> r instanceof MethodReference && r.equals(m)));
        Method click = index.uniqueMethod(m -> m.getDefiningClass().equals(fill.getDefiningClass())
                && m.getReturnType().equals("V")
                && m.getParameterTypes().contains(factory.getReturnType())
                && HostDexIndex.hasString(m, "settings"));
        // Factory.of's first argument is the row ID; follow its write into UItem.
        int idRegister = factory.getImplementation().getRegisterCount() - factoryParameters.size();
        List<FieldReference> ids = new ArrayList<>();
        for (Instruction instruction : factory.getImplementation().getInstructions()) {
            if (instruction.getOpcode() == Opcode.IPUT
                    && ((TwoRegisterInstruction) instruction).getRegisterA() == idRegister) {
                FieldReference field = (FieldReference) ((ReferenceInstruction) instruction).getReference();
                if (field.getDefiningClass().equals(factory.getReturnType())) ids.add(field);
            }
        }
        return new Symbols(factory, fill, click, HostDexIndex.unique(ids));
    }
}
