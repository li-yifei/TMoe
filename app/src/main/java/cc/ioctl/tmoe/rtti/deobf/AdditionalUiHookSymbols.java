package cc.ioctl.tmoe.rtti.deobf;

import org.jf.dexlib2.AccessFlags;
import org.jf.dexlib2.Opcode;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.iface.instruction.Instruction;
import org.jf.dexlib2.iface.instruction.NarrowLiteralInstruction;
import org.jf.dexlib2.iface.instruction.ReferenceInstruction;
import org.jf.dexlib2.iface.reference.FieldReference;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Resolve UI hooks from persisted settings, retained Android methods, and protocol types. */
public final class AdditionalUiHookSymbols {
    private static final String ENTER_VIEW = "Lorg/telegram/ui/Components/ChatActivityEnterView;";
    private static final String DOCUMENT = "Lorg/telegram/tgnet/TLRPC$Document;";

    private AdditionalUiHookSymbols() {}

    private static Method noSoundHintSetter(HostDexIndex index) {
        return index.uniqueMethod(m -> AccessFlags.STATIC.isSet(m.getAccessFlags())
                && m.getReturnType().equals("V") && m.getParameterTypes().equals(List.of("Z"))
                && HostDexIndex.hasString(m, "noSoundHintShowed"));
    }

    public static Method maybePlayVisibleVideo(HostDexIndex index) {
        Method setter = noSoundHintSetter(index);
        return index.uniqueMethod(m -> m.getDefiningClass().startsWith("Lorg/telegram/ui/")
                && !AccessFlags.STATIC.isSet(m.getAccessFlags())
                && m.getReturnType().equals("Z") && m.getParameterTypes().isEmpty()
                && HostDexIndex.references(m, r -> r.toString().equals(setter.toString())));
    }

    public static Method canBlurChat(HostDexIndex index) {
        String sharedConfig = noSoundHintSetter(index).getDefiningClass();
        return index.uniqueMethod(m -> m.getDefiningClass().equals(sharedConfig)
                && AccessFlags.STATIC.isSet(m.getAccessFlags())
                && m.getReturnType().equals("Z") && m.getParameterTypes().isEmpty()
                && HostDexIndex.references(m,
                r -> r.toString().equals("Landroid/os/Build$VERSION;->SDK_INT:I"))
                && hasLiteral(m, 31));
    }

    private static boolean hasLiteral(Method method, int value) {
        if (method.getImplementation() == null) return false;
        for (Instruction instruction : method.getImplementation().getInstructions()) {
            if (instruction instanceof NarrowLiteralInstruction
                    && ((NarrowLiteralInstruction) instruction).getNarrowLiteral() == value) return true;
        }
        return false;
    }

    public static final class CameraMode {
        public final Method setter;
        public final FieldReference hasRecordVideo;

        private CameraMode(Method setter, FieldReference hasRecordVideo) {
            this.setter = setter;
            this.hasRecordVideo = hasRecordVideo;
        }
    }

    public static CameraMode cameraMode(HostDexIndex index) {
        Method setter = index.uniqueMethod(m -> m.getDefiningClass().equals(ENTER_VIEW)
                && m.getParameterTypes().equals(List.of("Z", "Z")) && m.getReturnType().equals("V")
                && HostDexIndex.hasString(m, "currentModeVideoChannel")
                && HostDexIndex.hasString(m, "currentModeVideo")
                && HostDexIndex.references(m, r -> r.toString().equals(
                "Landroid/content/SharedPreferences$Editor;->putBoolean(Ljava/lang/String;Z)"
                        + "Landroid/content/SharedPreferences$Editor;")));
        Method checkRoundVideo = index.uniqueMethod(m -> m.getDefiningClass().equals(ENTER_VIEW)
                && m.getParameterTypes().isEmpty() && m.getReturnType().equals("V")
                && HostDexIndex.hasString(m, "currentModeVideoChannel")
                && HostDexIndex.hasString(m, "currentModeVideo")
                && HostDexIndex.references(m, r -> r.toString().equals(setter.toString())));
        // checkRoundVideo begins by returning when hasRecordVideo has already been initialized.
        Instruction first = checkRoundVideo.getImplementation().getInstructions().iterator().next();
        if (first.getOpcode() != Opcode.IGET_BOOLEAN || !(first instanceof ReferenceInstruction)) {
            throw new IllegalStateException("Unknown checkRoundVideo entry: " + checkRoundVideo);
        }
        FieldReference field = (FieldReference) ((ReferenceInstruction) first).getReference();
        if (!field.getDefiningClass().equals(ENTER_VIEW) || !field.getType().equals("Z")) {
            throw new IllegalStateException("Unknown recording capability field: " + field);
        }
        return new CameraMode(setter, field);
    }

    public static final class Greetings {
        public final String className;
        public final List<String> hiddenViewFields;

        private Greetings(String className, List<String> hiddenViewFields) {
            this.className = className;
            this.hiddenViewFields = hiddenViewFields;
        }
    }

    public static Greetings greetings(HostDexIndex index) {
        List<ClassDef> matches = new ArrayList<>();
        for (ClassDef cls : index.classes) {
            if (!"Landroid/widget/LinearLayout;".equals(cls.getSuperclass())) continue;
            boolean documentSetter = false, pathSetter = false, measures = false;
            for (Method m : cls.getMethods()) {
                if (m.getName().equals("setSticker") && m.getReturnType().equals("V")) {
                    documentSetter |= m.getParameterTypes().equals(List.of(DOCUMENT));
                    pathSetter |= m.getParameterTypes().equals(List.of("Ljava/lang/String;"));
                }
                measures |= m.getName().equals("onMeasure")
                        && m.getParameterTypes().equals(List.of("I", "I"));
            }
            if (documentSetter && pathSetter && measures) matches.add(cls);
        }
        ClassDef cls = HostDexIndex.unique(matches);
        Method measure = index.uniqueMethod(m -> m.getDefiningClass().equals(cls.getType())
                && m.getName().equals("onMeasure") && m.getParameterTypes().equals(List.of("I", "I")));
        Set<String> fields = new LinkedHashSet<>();
        boolean textView = false;
        for (Instruction instruction : measure.getImplementation().getInstructions()) {
            if (instruction.getOpcode() != Opcode.IGET_OBJECT) continue;
            var ref = ((ReferenceInstruction) instruction).getReference();
            if (!(ref instanceof FieldReference)) continue;
            FieldReference field = (FieldReference) ref;
            if (!field.getDefiningClass().equals(cls.getType())) continue;
            fields.add(field.getName());
            textView |= field.getType().equals("Landroid/widget/TextView;");
        }
        if (fields.size() != 2 || !textView) {
            throw new IllegalStateException("Unknown greetings measurement fields: " + fields);
        }
        String type = cls.getType();
        return new Greetings(type.substring(1, type.length() - 1).replace('/', '.'),
                new ArrayList<>(fields));
    }
}
