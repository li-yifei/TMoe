package cc.ioctl.tmoe.rtti.deobf;

import org.junit.Assume;
import org.junit.Test;
import java.io.File;
import static org.junit.Assert.*;

public class ProfileMenuSymbolsTest {
    @Test
    public void resolvesProfileMenuAndLinkedChatAction() throws Exception {
        String apk = System.getenv("TMOE_HOST_APK");
        Assume.assumeTrue(apk != null);
        ProfileMenuSymbols s = new ProfileMenuSymbols(HostDexIndex.open(new File(apk)));
        assertEquals(s.createMenu.getDefiningClass(), s.openLinkedChat.getDefiningClass());
        assertEquals("Lorg/telegram/tgnet/TLRPC$Chat;", s.chat.getType());
        assertEquals("Lorg/telegram/tgnet/TLRPC$ChatFull;", s.chatInfo.getType());
    }
}
