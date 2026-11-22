package cc.ioctl.tmoe.rtti.deobf;

import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HistoricalMenuSymbolsTest {
    @Test
    public void resolvesOptimizedMessageMenuAndSenderActions() throws Exception {
        String path = System.getenv("TMOE_HOST_APK");
        Assume.assumeTrue(path != null);
        HistoricalMenuSymbols s = new HistoricalMenuSymbols(HostDexIndex.open(new File(path)));
        assertEquals(s.createMenu.getDefiningClass(), s.selectedMessage.getDefiningClass());
        assertEquals(s.selectedMessage.getType(), s.messageOwner.getDefiningClass());
        assertEquals(List.of("I"), s.processOption.getParameterTypes());
        assertEquals(List.of("Z"), s.showSearchList.getParameterTypes());
        assertTrue(s.searchSender.getParameterTypes().contains("Lorg/telegram/tgnet/TLRPC$User;"));
        assertTrue(s.searchSender.getParameterTypes().contains("Lorg/telegram/tgnet/TLRPC$Chat;"));
        assertEquals(s.groupedMessage.getReturnType(), s.groupMessages.getDefiningClass());
        assertEquals("Ljava/util/ArrayList;", s.groupMessages.getType());
        assertEquals("J", s.forward.getParameterTypes().get(5));
    }
}
