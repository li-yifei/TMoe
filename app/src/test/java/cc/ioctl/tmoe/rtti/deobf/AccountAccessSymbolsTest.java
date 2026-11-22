package cc.ioctl.tmoe.rtti.deobf;

import org.junit.Assume;
import org.junit.Test;
import java.io.File;
import java.util.List;
import static org.junit.Assert.*;

public class AccountAccessSymbolsTest {
    @Test
    public void resolvesAccountAndMemberCacheSymbolsFromHostApk() throws Exception {
        String path = System.getenv("TMOE_HOST_APK");
        Assume.assumeTrue("Set TMOE_HOST_APK to verify the actual host artifact", path != null);
        HostDexIndex index = HostDexIndex.open(new File(path));
        var config = AccountAccessSymbols.config(index);
        assertEquals(config.instance.getReturnType(), config.selectedAccount.getDefiningClass());
        assertEquals(config.instance.getReturnType(), config.clientUserId.getDefiningClass());
        assertEquals("I", config.selectedAccount.getType());
        assertEquals("J", config.clientUserId.getType());
        var messages = AccountAccessSymbols.messages(index);
        assertEquals(4, messages.size());
        assertEquals(messages.get(0).getReturnType(), messages.get(1).getDefiningClass());
        assertTrue(AccountAccessSymbols.chatClass(index).startsWith("org.telegram.ui."));
        var putChats = MemberAccessSymbols.putChats(index);
        var onlineText = MemberAccessSymbols.onlineText(index);
        var profileChatId = MemberAccessSymbols.profileChatId(index);
        assertEquals("J", profileChatId.getType());
        assertNotNull(index.uniqueMethod(m -> m.getDefiningClass().equals(
                "Lorg/telegram/tgnet/ConnectionsManager;") && m.getName().equals("getInstance")
                && m.getParameterTypes().equals(List.of("I"))));
        assertNotNull(index.uniqueMethod(m -> m.getDefiningClass().equals(
                "Lorg/telegram/tgnet/ConnectionsManager;") && m.getName().equals("sendRequest")
                && m.getParameterTypes().equals(List.of("Lorg/telegram/tgnet/TLObject;",
                "Lorg/telegram/tgnet/RequestDelegate;"))));
        System.out.println("Member export: " + putChats + ", " + onlineText + ", " + profileChatId);
        System.out.println("UserConfig: " + config.instance + ", " + config.selectedAccount
                + ", " + config.clientUserId);
        System.out.println("MessagesController: " + messages);
        System.out.println("ChatActivity: " + AccountAccessSymbols.chatClass(index));
    }
}
