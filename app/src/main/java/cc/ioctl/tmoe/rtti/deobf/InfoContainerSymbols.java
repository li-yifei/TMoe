package cc.ioctl.tmoe.rtti.deobf;

import org.jf.dexlib2.Opcode;
import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.iface.instruction.*;
import org.jf.dexlib2.iface.reference.*;
import java.util.*;
import java.util.function.Predicate;

/** Anchors the read-only group information UI to its retained TL fields and adapter branches. */
public final class InfoContainerSymbols {
    public final Method profileMenu, editCreate, editUpdate, usersCreate, bind, enabled;
    public final FieldReference profileChat, editVisible, editChat, adminCell, blockCell, logCell;
    public final FieldReference usersChat, adapterOwner, holderView;
    public final FieldReference settingsSection;
    public final List<FieldReference> hiddenRows;

    public InfoContainerSymbols(HostDexIndex index) {
        ProfileMenuSymbols profile = new ProfileMenuSymbols(index);
        profileMenu = profile.createMenu;
        profileChat = profile.chat;
        editVisible = firstField(profileMenu, Opcode.IPUT_BOOLEAN, f -> true);
        editUpdate = index.uniqueMethod(m -> m.getParameterTypes().equals(List.of("Z", "Z"))
                && HostDexIndex.hasString(m, "DiscussionInfoShort")
                && HostDexIndex.hasString(m, "ChannelAdministrators"));
        String editor = editUpdate.getDefiningClass();
        editCreate = createView(index, editor);
        settingsSection = sectionHiddenWithContainer(editCreate);
        editChat = uniqueField(index, editor, "Lorg/telegram/tgnet/TLRPC$Chat;");
        Method valueGetter = index.uniqueMethod(m -> m.getName().equals("getValueTextView")
                && HostDexIndex.references(editUpdate, r -> r instanceof MethodReference
                    && ((MethodReference) r).getDefiningClass().equals(m.getDefiningClass())));
        String cell = valueGetter.getDefiningClass();
        adminCell = precedingField(editUpdate, r -> r.toString().equals(
                "Lorg/telegram/tgnet/TLRPC$ChatFull;->admins_count:I"), cell);
        blockCell = precedingField(editUpdate,
                r -> r instanceof StringReference && ((StringReference) r).getString().equals("%d/%d"), cell);
        logCell = firstVisibilityReceiver(editUpdate, cell);

        Method constructor = index.uniqueMethod(m -> m.getName().equals("<init>")
                && m.getParameterTypes().equals(List.of("Landroid/os/Bundle;"))
                && HostDexIndex.hasString(m, "transfer") && HostDexIndex.hasString(m, "open_search")
                && HostDexIndex.hasString(m, "selectType"));
        String users = constructor.getDefiningClass();
        usersCreate = createView(index, users);
        usersChat = uniqueField(index, users, "Lorg/telegram/tgnet/TLRPC$Chat;");
        bind = index.uniqueMethod(m -> HostDexIndex.hasString(m, "ChannelAddException")
                && HostDexIndex.hasString(m, "BroadcastGroupConvert")
                && m.getParameterTypes().size() == 2);
        String adapter = bind.getDefiningClass(), holder = bind.getParameterTypes().get(0).toString();
        adapterOwner = uniqueField(index, adapter, users);
        holderView = uniqueField(index, holder, "Landroid/view/View;");
        enabled = index.uniqueMethod(m -> m.getDefiningClass().equals(adapter)
                && m.getReturnType().equals("Z") && m.getParameterTypes().equals(List.of(holder)));
        Method viewType = index.uniqueMethod(m -> m.getDefiningClass().equals(adapter)
                && m.getReturnType().equals("I") && m.getParameterTypes().equals(List.of("I")));
        List<FieldReference> dividers = fieldsReturning(viewType, users, 3);
        if (dividers.size() != 3) throw new IllegalStateException("Unknown member-list divider layout");
        hiddenRows = new ArrayList<>(List.of(dividers.get(0), dividers.get(2)));
        hiddenRows.add(HostDexIndex.unique(fieldsReturning(viewType, users, 6)));
    }

    private static Method createView(HostDexIndex index, String owner) {
        return index.uniqueMethod(m -> m.getDefiningClass().equals(owner) && m.getName().equals("createView")
                && m.getParameterTypes().equals(List.of("Landroid/content/Context;")));
    }

    private static FieldReference uniqueField(HostDexIndex index, String owner, String type) {
        List<FieldReference> fields = new ArrayList<>();
        for (var cls : index.classes) if (cls.getType().equals(owner))
            for (var field : cls.getFields()) if (field.getType().equals(type)) fields.add(field);
        return HostDexIndex.unique(fields);
    }

    private static FieldReference firstField(Method method, Opcode opcode, Predicate<FieldReference> test) {
        for (Instruction instruction : method.getImplementation().getInstructions())
            if (instruction.getOpcode() == opcode && instruction instanceof ReferenceInstruction) {
                var ref = ((ReferenceInstruction) instruction).getReference();
                if (ref instanceof FieldReference && test.test((FieldReference) ref)) return (FieldReference) ref;
            }
        throw new IllegalStateException("Missing field in " + method);
    }

    private static FieldReference precedingField(Method method, Predicate<Reference> anchor, String type) {
        FieldReference last = null;
        Set<FieldReference> matches = new LinkedHashSet<>();
        for (Instruction instruction : method.getImplementation().getInstructions()) {
            if (!(instruction instanceof ReferenceInstruction)) continue;
            Reference ref = ((ReferenceInstruction) instruction).getReference();
            if (ref instanceof FieldReference && instruction.getOpcode() == Opcode.IGET_OBJECT
                    && ((FieldReference) ref).getType().equals(type)) last = (FieldReference) ref;
            if (anchor.test(ref) && last != null) matches.add(last);
        }
        return HostDexIndex.unique(new ArrayList<>(matches));
    }

    private static FieldReference firstVisibilityReceiver(Method method, String type) {
        Map<Integer, FieldReference> registers = new HashMap<>();
        for (Instruction instruction : method.getImplementation().getInstructions()) {
            if (instruction.getOpcode() == Opcode.IGET_OBJECT) {
                var field = (FieldReference) ((ReferenceInstruction) instruction).getReference();
                registers.put(((TwoRegisterInstruction) instruction).getRegisterA(), field);
            } else if (instruction instanceof ReferenceInstruction && instruction instanceof FiveRegisterInstruction
                    && ((ReferenceInstruction) instruction).getReference().toString().equals(
                        "Landroid/view/View;->setVisibility(I)V")) {
                FieldReference field = registers.get(((FiveRegisterInstruction) instruction).getRegisterC());
                if (field != null && field.getType().equals(type)) return field;
            }
        }
        throw new IllegalStateException("Missing log cell visibility");
    }

    private static FieldReference sectionHiddenWithContainer(Method method) {
        List<Instruction> code = new ArrayList<>();
        method.getImplementation().getInstructions().forEach(code::add);
        List<FieldReference> matches = new ArrayList<>();
        for (int i = 0; i + 3 < code.size(); i++) {
            if (code.get(i).getOpcode() != Opcode.IGET_OBJECT
                    || code.get(i + 2).getOpcode() != Opcode.IGET_OBJECT) continue;
            FieldReference container = (FieldReference) ((ReferenceInstruction) code.get(i)).getReference();
            FieldReference section = (FieldReference) ((ReferenceInstruction) code.get(i + 2)).getReference();
            if (!container.getType().equals("Landroid/widget/LinearLayout;")
                    || !section.getDefiningClass().equals(method.getDefiningClass())) continue;
            if (isVisibilityCall(code.get(i + 1)) && isVisibilityCall(code.get(i + 3))) matches.add(section);
        }
        return HostDexIndex.unique(matches);
    }

    private static boolean isVisibilityCall(Instruction instruction) {
        return instruction instanceof ReferenceInstruction && ((ReferenceInstruction) instruction)
                .getReference().toString().equals("Landroid/view/View;->setVisibility(I)V");
    }

    /** Follow the equality arm of each row comparison to its constant adapter view type. */
    private static List<FieldReference> fieldsReturning(Method method, String owner, int value) {
        List<Instruction> instructions = new ArrayList<>();
        Map<Integer, Integer> offsets = new HashMap<>();
        List<Integer> addresses = new ArrayList<>();
        int offset = 0;
        for (Instruction i : method.getImplementation().getInstructions()) {
            offsets.put(offset, instructions.size()); addresses.add(offset); instructions.add(i);
            offset += i.getCodeUnits();
        }
        List<FieldReference> fields = new ArrayList<>();
        for (int i = 0; i + 1 < instructions.size(); i++) {
            Instruction instruction = instructions.get(i), branch = instructions.get(i + 1);
            if (instruction.getOpcode() != Opcode.IGET
                    || !(branch instanceof OffsetInstruction)) continue;
            FieldReference field = (FieldReference) ((ReferenceInstruction) instruction).getReference();
            if (!field.getDefiningClass().equals(owner)) continue;
            int cursor;
            if (branch.getOpcode() == Opcode.IF_EQ)
                cursor = offsets.get(addresses.get(i + 1) + ((OffsetInstruction) branch).getCodeOffset());
            else if (branch.getOpcode() == Opcode.IF_NE) cursor = i + 2;
            else continue;
            for (int steps = 0; steps < 3; steps++) {
                Instruction target = instructions.get(cursor);
                if (target.getOpcode() == Opcode.GOTO || target.getOpcode() == Opcode.GOTO_16
                        || target.getOpcode() == Opcode.GOTO_32) {
                    cursor = offsets.get(addresses.get(cursor) + ((OffsetInstruction) target).getCodeOffset());
                } else {
                    if (target instanceof NarrowLiteralInstruction
                            && ((NarrowLiteralInstruction) target).getNarrowLiteral() == value
                            && instructions.get(cursor + 1).getOpcode() == Opcode.RETURN) fields.add(field);
                    break;
                }
            }
        }
        return fields;
    }
}
