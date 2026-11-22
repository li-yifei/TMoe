package cc.ioctl.tmoe.rtti.deobf;

import org.junit.Assume;
import org.junit.Test;
import java.io.File;
import static org.junit.Assert.*;

public class ReloadMenuSymbolsTest {
    @Test
    public void resolvesHostReloadActionAndMenuFromInstalledArtifact() throws Exception {
        String apk = System.getenv("TMOE_HOST_APK");
        Assume.assumeTrue(apk != null);
        ReloadMenuSymbols s = new ReloadMenuSymbols(HostDexIndex.open(new File(apk)));
        assertEquals(s.createView.getDefiningClass(), s.jumpToDate.getDefiningClass());
        assertEquals(s.addSubItem.getDefiningClass(), s.closeSubMenu.getDefiningClass());
        assertEquals("I", s.jumpToDate.getParameterTypes().get(0));
    }
}
