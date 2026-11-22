package cc.ioctl.tmoe.rtti.deobf;

import org.junit.Assume;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.*;

public class InteractionHookSymbolsTest {
    @Test
    public void resolvesInteractionsFromInstalledApkWhenProvided() throws Exception {
        String path = System.getenv("TMOE_HOST_APK");
        Assume.assumeTrue("Set TMOE_HOST_APK to verify the actual host artifact", path != null);
        HostDexIndex index = HostDexIndex.open(new File(path));
        var command = InteractionHookSymbols.command(index);
        assertEquals(4, command.getParameterTypes().size());
        var spoilers = InteractionHookSymbols.spoilers(index);
        assertEquals(2, spoilers.size());
        var copying = InteractionHookSymbols.copyWithoutForwarding(index);
        assertEquals(3, copying.size());
        var topics = InteractionHookSymbols.topics(index);
        assertEquals("Z", topics.isTopic.getType());
        assertEquals("presentFragment", topics.present.getName());
        System.out.println("SendCommand: " + command);
        System.out.println("ProhibitSpoilers: " + spoilers);
        System.out.println("AntiAntiCopy: " + copying);
        System.out.println("ViewTopicAsMsgByDefault: " + topics.present + ", "
                + topics.topicsClass + ", " + topics.chatClass + ", " + topics.isTopic);
    }
}
