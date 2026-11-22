package cc.ioctl.tmoe.rtti.deobf;

import org.junit.Assume;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;

public class PrivacyReactionResolverTest {
    @Test
    public void resolvesReactionServiceUserAndPhoneHeaderFromHostApk() throws Exception {
        String path = System.getenv("TMOE_HOST_APK");
        Assume.assumeTrue("Set TMOE_HOST_APK to run the shipped-artifact check", path != null);
        HostDexIndex index = HostDexIndex.open(new File(path));
        assertEquals("Z", PrivacyReactionResolver.doubleTapEnabled(index).getReturnType());
        assertEquals("Z", PrivacyReactionResolver.isService(index).getReturnType());
        assertEquals("Lorg/telegram/tgnet/TLRPC$User;", PrivacyReactionResolver.getUser(index).getReturnType());
        var header = PrivacyReactionResolver.phoneHeader(index);
        assertEquals(header.update.getDefiningClass(), header.subtitle.getDefiningClass());
        assertEquals("Landroid/widget/TextView;", header.subtitle.getType());
    }
}
