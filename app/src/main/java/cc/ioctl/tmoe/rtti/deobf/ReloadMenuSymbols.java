package cc.ioctl.tmoe.rtti.deobf;

import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.iface.reference.MethodReference;
import java.util.List;

/** Uses the host's date-loading action so topic IDs and request bookkeeping stay host-owned. */
public final class ReloadMenuSymbols {
    public final Method createView, jumpToDate, addSubItem, closeSubMenu;

    public ReloadMenuSymbols(HostDexIndex index) {
        Method menu = index.uniqueMethod(m -> m.getReturnType().equals("Z")
                && HostDexIndex.hasString(m, "open menu msg_id="));
        String chat = menu.getDefiningClass();
        createView = index.uniqueMethod(m -> m.getDefiningClass().equals(chat)
                && m.getName().equals("createView")
                && m.getParameterTypes().equals(List.of("Landroid/content/Context;")));
        jumpToDate = index.uniqueMethod(m -> m.getDefiningClass().equals(chat)
                && m.getReturnType().equals("V") && m.getParameterTypes().equals(List.of("I"))
                && HostDexIndex.references(m, r -> r.toString().equals(
                        "Lorg/telegram/tgnet/TLRPC$Message;->date:I"))
                && HostDexIndex.references(m, r -> r instanceof MethodReference
                    && ((MethodReference) r).getReturnType().equals("V")
                    && ((MethodReference) r).getParameterTypes().equals(List.of(
                            "J", "J", "Z", "I", "I", "I", "Z", "I", "I", "I", "I", "J", "I", "I", "Z"))));
        Method popup = index.uniqueMethod(m -> m.getName().equals("getPopupLayout")
                && m.getReturnType().equals(
                        "Lorg/telegram/ui/ActionBar/ActionBarPopupWindow$ActionBarPopupWindowLayout;"));
        String item = popup.getDefiningClass();
        addSubItem = index.uniqueMethod(m -> m.getDefiningClass().equals(item)
                && m.getParameterTypes().equals(List.of("I", "I", "Ljava/lang/CharSequence;"))
                && !m.getReturnType().equals("V"));
        closeSubMenu = index.uniqueMethod(m -> m.getDefiningClass().equals(item)
                && m.getReturnType().equals("V") && m.getParameterTypes().isEmpty()
                && HostDexIndex.references(m, r -> r instanceof MethodReference
                    && ((MethodReference) r).getName().equals("getShownSubMenuOverlay"))
                && HostDexIndex.references(m, r -> r instanceof MethodReference
                    && ((MethodReference) r).getName().equals("dismiss")));
    }
}
