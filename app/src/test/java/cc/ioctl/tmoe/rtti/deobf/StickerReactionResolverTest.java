package cc.ioctl.tmoe.rtti.deobf;

import org.junit.Assume;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.*;

public class StickerReactionResolverTest {
    @Test
    public void resolvesStickerDateAndReactionOperationsFromHostApk() throws Exception {
        String path = System.getenv("TMOE_HOST_APK");
        Assume.assumeTrue("Set TMOE_HOST_APK to run the shipped-artifact check", path != null);
        HostDexIndex index = HostDexIndex.open(new File(path));
        assertEquals("Z", StickerReactionResolver.premiumSticker(index).getReturnType());
        assertEquals("Ljava/util/ArrayList;", StickerReactionResolver.recentStickers(index).getReturnType());
        assertEquals("Ljava/lang/String;", StickerReactionResolver.dateOnline(index).getReturnType());
        var reactions = StickerReactionResolver.reactions(index);
        assertEquals(11, reactions.select.getParameterTypes().size());
        assertEquals(reactions.select.getParameterTypes().get(2), reactions.setMessage.getDefiningClass());
        assertEquals(reactions.setMessage.getDefiningClass(), reactions.containerType.getDefiningClass());
        assertEquals("I", reactions.containerType.getType());
        assertEquals("onAttachedToWindow", reactions.onAttached.getName());
        assertEquals("didReceivedNotification", reactions.notification.getName());
    }
}
