package cc.ioctl.tmoe.rtti.deobf;

import org.jf.dexlib2.AccessFlags;
import org.jf.dexlib2.Opcode;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.iface.instruction.Instruction;
import org.jf.dexlib2.iface.instruction.OneRegisterInstruction;
import org.jf.dexlib2.iface.instruction.TwoRegisterInstruction;
import org.jf.dexlib2.iface.instruction.ReferenceInstruction;
import org.jf.dexlib2.iface.reference.FieldReference;

import java.util.ArrayList;
import java.util.List;

public final class MemberHistorySymbols {
    private MemberHistorySymbols() {}

    public static final class History {
        public final Method setCount;
        public final FieldReference dialogId;

        private History(Method setCount, FieldReference dialogId) {
            this.setCount = setCount;
            this.dialogId = dialogId;
        }
    }

    public static History history(HostDexIndex index) {
        Method update = index.uniqueMethod(m -> m.getReturnType().equals("V")
                && m.getParameterTypes().equals(List.of("Z"))
                && HostDexIndex.references(m, r -> r.toString().equals(
                "Lorg/telegram/tgnet/TLRPC$User;->bot_has_main_app:Z"))
                && HostDexIndex.references(m, r -> r.toString().equals(
                "Lorg/telegram/tgnet/TLRPC$User;->bot_can_edit:Z")));
        String owner = update.getDefiningClass();
        Method setCount = index.uniqueMethod(m -> m.getDefiningClass().equals(owner)
                && m.getName().equals("setCommonGroupsCount")
                && m.getParameterTypes().equals(List.of("I")) && m.getReturnType().equals("V")
                && HostDexIndex.references(m, r -> r.equals(update)));
        Method constructor = index.uniqueMethod(m -> m.getDefiningClass().equals(owner)
                && m.getName().equals("<init>") && m.getParameterTypes().size() > 2
                && m.getParameterTypes().subList(0, 2).equals(List.of("Landroid/content/Context;", "J")));
        int parameterWords = 1;
        for (CharSequence parameter : constructor.getParameterTypes()) {
            parameterWords += parameter.equals("J") || parameter.equals("D") ? 2 : 1;
        }
        int dialogRegister = constructor.getImplementation().getRegisterCount() - parameterWords + 2;
        List<FieldReference> fields = new ArrayList<>();
        int copiedRegister = -1;
        for (Instruction insn : constructor.getImplementation().getInstructions()) {
            Opcode opcode = insn.getOpcode();
            if (opcode == Opcode.IPUT_WIDE) {
                FieldReference field = (FieldReference) ((ReferenceInstruction) insn).getReference();
                int value = ((TwoRegisterInstruction) insn).getRegisterA();
                if ((value == dialogRegister || value == copiedRegister)
                        && field.getDefiningClass().equals(owner) && !fields.contains(field)) fields.add(field);
            }
            // R8 moves the constructor's dialog parameter immediately before its field assignment.
            copiedRegister = -1;
            if (opcode == Opcode.MOVE_WIDE || opcode == Opcode.MOVE_WIDE_FROM16
                    || opcode == Opcode.MOVE_WIDE_16) {
                TwoRegisterInstruction move = (TwoRegisterInstruction) insn;
                if (move.getRegisterB() == dialogRegister) copiedRegister = move.getRegisterA();
            }
        }
        return new History(setCount, HostDexIndex.unique(fields));
    }

    public static final class Mentions {
        public final Method search, showUsers, currentChat;
        public final FieldReference info, chat, parent, start, length;

        private Mentions(Method search, Method showUsers, Method currentChat, FieldReference info,
                         FieldReference chat, FieldReference parent, FieldReference start, FieldReference length) {
            this.search = search;
            this.showUsers = showUsers;
            this.currentChat = currentChat;
            this.info = info;
            this.chat = chat;
            this.parent = parent;
            this.start = start;
            this.length = length;
        }
    }

    public static Mentions mentions(HostDexIndex index) {
        Method search = index.uniqueMethod(m -> m.getReturnType().equals("V")
                && !AccessFlags.STATIC.isSet(m.getAccessFlags())
                && m.getParameterTypes().equals(List.of(
                "Ljava/lang/CharSequence;", "I", "Ljava/util/ArrayList;", "Z", "Z"))
                && HostDexIndex.references(m, r -> r.toString().equals(
                "Lorg/telegram/tgnet/TLRPC$ChatFull;->participants:Lorg/telegram/tgnet/TLRPC$ChatParticipants;")));
        String owner = search.getDefiningClass();
        Method show = index.uniqueMethod(m -> m.getDefiningClass().equals(owner)
                && m.getReturnType().equals("V") && m.getParameterTypes().size() == 3
                && m.getParameterTypes().contains("Ljava/util/ArrayList;")
                && m.getParameterTypes().contains("Z")
                && HostDexIndex.references(m, r -> r.toString().equals(
                "Lorg/telegram/tgnet/TLRPC$User;->bot:Z"))
                && HostDexIndex.references(m, r -> r.toString().equals(
                "Ljava/util/Iterator;->remove()V")));
        String chatOwner = "L" + AccountAccessSymbols.chatClass(index).replace('.', '/') + ";";
        Method currentChat = index.uniqueMethod(m -> m.getDefiningClass().equals(chatOwner)
                && m.getParameterTypes().isEmpty()
                && m.getReturnType().equals("Lorg/telegram/tgnet/TLRPC$Chat;"));
        List<Instruction> code = new ArrayList<>();
        search.getImplementation().getInstructions().forEach(code::add);
        List<List<FieldReference>> pairs = new ArrayList<>();
        for (int i = 1; i + 2 < code.size(); i++) {
            Instruction call = code.get(i);
            if (!(call instanceof ReferenceInstruction) || !((ReferenceInstruction) call)
                    .getReference().toString().equals("Ljava/lang/StringBuilder;->length()I")) continue;
            Instruction before = code.get(i - 1), result = code.get(i + 1), after = code.get(i + 2);
            if (before.getOpcode() != Opcode.IPUT || after.getOpcode() != Opcode.IPUT
                    || result.getOpcode() != Opcode.MOVE_RESULT) continue;
            if (((OneRegisterInstruction) result).getRegisterA()
                    != ((TwoRegisterInstruction) after).getRegisterA()) continue;
            FieldReference start = (FieldReference) ((ReferenceInstruction) before).getReference();
            FieldReference length = (FieldReference) ((ReferenceInstruction) after).getReference();
            if (start.getDefiningClass().equals(owner) && length.getDefiningClass().equals(owner)
                    && !start.equals(length)) {
                List<FieldReference> pair = List.of(start, length);
                if (!pairs.contains(pair)) pairs.add(pair);
            }
        }
        List<FieldReference> range = HostDexIndex.unique(pairs);
        return new Mentions(search, show, currentChat,
                fieldByType(index, owner, "Lorg/telegram/tgnet/TLRPC$ChatFull;"),
                fieldByType(index, owner, "Lorg/telegram/tgnet/TLRPC$Chat;"),
                fieldByType(index, owner, chatOwner), range.get(0), range.get(1));
    }

    private static FieldReference fieldByType(HostDexIndex index, String owner, String type) {
        List<FieldReference> matches = new ArrayList<>();
        for (ClassDef cls : index.classes) {
            if (cls.getType().equals(owner)) {
                for (var field : cls.getFields()) if (field.getType().equals(type)) matches.add(field);
            }
        }
        return HostDexIndex.unique(matches);
    }
}
