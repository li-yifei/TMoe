package cc.ioctl.tmoe.rtti.deobf;

import org.junit.Assume;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class EmojiChannelResolverTest {
    @Test
    public void resolvesOptimizedEmojiAndChannelOperationsInHostApk() throws Exception {
        String path = System.getenv("TMOE_HOST_APK");
        Assume.assumeTrue("Set TMOE_HOST_APK to run the shipped-artifact check", path != null);
        HostDexIndex index = HostDexIndex.open(new File(path));
        var emoji = EmojiChannelResolver.systemEmojiReplacement(index);
        assertEquals("Ljava/lang/CharSequence;", emoji.getReturnType());
        assertTrue(emoji.getImplementation() != null);
        var next = EmojiChannelResolver.nextUnreadChannel(index);
        assertEquals("Lorg/telegram/tgnet/TLRPC$Dialog;", next.getReturnType());
        assertEquals(2, EmojiChannelResolver.channelDrawMethods(index, next).size());
    }
}
