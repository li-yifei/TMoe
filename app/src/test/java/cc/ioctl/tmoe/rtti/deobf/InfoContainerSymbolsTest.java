package cc.ioctl.tmoe.rtti.deobf;

import org.junit.Assume;
import org.junit.Test;
import java.io.File;
import java.util.HashSet;
import java.util.List;
import static org.junit.Assert.*;

public class InfoContainerSymbolsTest {
    @Test
    public void resolvesAllThreeInformationScreensFromInstalledArtifact() throws Exception {
        String apk = System.getenv("TMOE_HOST_APK");
        Assume.assumeTrue(apk != null);
        InfoContainerSymbols s = new InfoContainerSymbols(HostDexIndex.open(new File(apk)));
        assertEquals(s.editCreate.getDefiningClass(), s.editUpdate.getDefiningClass());
        assertEquals(3, new HashSet<>(List.of(s.adminCell, s.blockCell, s.logCell)).size());
        assertEquals(3, new HashSet<>(s.hiddenRows).size());
        assertTrue(s.hiddenRows.stream().allMatch(f -> f.getType().equals("I")
                && f.getDefiningClass().equals(s.usersCreate.getDefiningClass())));
        assertEquals("Landroid/view/View;", s.holderView.getType());
        assertEquals("Z", s.editVisible.getType());
    }
}
