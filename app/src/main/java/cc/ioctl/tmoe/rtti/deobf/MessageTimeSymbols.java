package cc.ioctl.tmoe.rtti.deobf;

import org.jf.dexlib2.Opcode;
import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.iface.instruction.Instruction;
import org.jf.dexlib2.iface.instruction.ReferenceInstruction;
import org.jf.dexlib2.iface.instruction.TwoRegisterInstruction;
import org.jf.dexlib2.iface.reference.FieldReference;
import org.jf.dexlib2.iface.reference.MethodReference;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Locates the timestamp measurement and its paired width assignments in optimized hosts. */
public final class MessageTimeSymbols {
    public final Method measureTime;
    public final FieldReference time, paint, width, textWidth, messageOwner;

    public MessageTimeSymbols(HostDexIndex index) {
        measureTime = index.uniqueMethod(m -> m.getReturnType().equals("V")
                && m.getParameterTypes().size() == 1
                && HostDexIndex.hasString(m, "MessageScheduledRepeatMonthlyMany"));
        String owner = measureTime.getDefiningClass();
        List<Instruction> instructions = new ArrayList<>();
        measureTime.getImplementation().getInstructions().forEach(instructions::add);
        List<FieldReference[]> matches = new ArrayList<>();
        for (int i = 0; i + 7 < instructions.size(); i++) {
            Instruction call = instructions.get(i);
            if (!(call instanceof ReferenceInstruction)) continue;
            var ref = ((ReferenceInstruction) call).getReference();
            if (!(ref instanceof MethodReference)
                    || !ref.toString().equals("Ljava/lang/Math;->ceil(D)D")) continue;
            Instruction first = instructions.get(i + 3), second = instructions.get(i + 4);
            if (first.getOpcode() != Opcode.IPUT || second.getOpcode() != Opcode.IPUT) continue;
            if (((TwoRegisterInstruction) first).getRegisterA()
                    != ((TwoRegisterInstruction) second).getRegisterA()) continue;
            FieldReference a = (FieldReference) ((ReferenceInstruction) first).getReference();
            FieldReference b = (FieldReference) ((ReferenceInstruction) second).getReference();
            if (!a.getDefiningClass().equals(owner) || !b.getDefiningClass().equals(owner)
                    || a.equals(b)) continue;
            FieldReference timeCandidate = null, paintCandidate = null;
            boolean measurement = false;
            for (int j = i - 1; j >= Math.max(0, i - 24); j--) {
                Instruction prior = instructions.get(j);
                if (!(prior instanceof ReferenceInstruction)) continue;
                var priorRef = ((ReferenceInstruction) prior).getReference();
                if (priorRef instanceof MethodReference
                        && priorRef.toString().endsWith("->measureText(Ljava/lang/CharSequence;II)F")) {
                    measurement = true;
                }
                if (priorRef instanceof FieldReference) {
                    FieldReference field = (FieldReference) priorRef;
                    if (measurement && timeCandidate == null && prior.getOpcode() == Opcode.IGET_OBJECT
                            && field.getDefiningClass().equals(owner)
                            && field.getType().equals("Ljava/lang/CharSequence;")) timeCandidate = field;
                    if (measurement && prior.getOpcode() == Opcode.SGET_OBJECT
                            && field.getType().equals("Landroid/text/TextPaint;")) {
                        paintCandidate = field;
                        break;
                    }
                }
            }
            if (timeCandidate != null && paintCandidate != null)
                matches.add(new FieldReference[]{timeCandidate, paintCandidate, a, b});
        }
        FieldReference[] fields = HostDexIndex.unique(matches);
        time = fields[0]; paint = fields[1]; width = fields[2]; textWidth = fields[3];
        Set<FieldReference> owners = new LinkedHashSet<>();
        for (Instruction instruction : instructions) {
            if (!(instruction instanceof ReferenceInstruction)) continue;
            var ref = ((ReferenceInstruction) instruction).getReference();
            if (ref instanceof FieldReference) {
                FieldReference field = (FieldReference) ref;
                if (field.getDefiningClass().equals(measureTime.getParameterTypes().get(0).toString())
                        && field.getType().equals("Lorg/telegram/tgnet/TLRPC$Message;")) owners.add(field);
            }
        }
        messageOwner = HostDexIndex.unique(new ArrayList<>(owners));
    }
}
