package cc.ioctl.tmoe.rtti.deobf;

import org.junit.Test;

import java.io.File;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

public class SettingsDexResolverTest {
    @Test
    public void resolvesOptimizedSettingsAndColorsFromInstalledApk() throws Exception {
        String path = System.getenv("TMOE_HOST_APK");
        assumeTrue("Set TMOE_HOST_APK to run the real host DEX test", path != null);
        HostDexIndex index = HostDexIndex.open(new File(path));
        var settings = SettingsDexResolver.resolve(index);
        assertEquals(settings.factory.getReturnType(), settings.itemId.getDefiningClass());
        assertEquals("I", settings.itemId.getType());
        assertEquals(settings.fillItems.getDefiningClass(), settings.onClick.getDefiningClass());
        assertTrue(settings.fillItems.getParameterTypes().contains("Ljava/util/ArrayList;"));
        var theme = ThemeDexResolver.resolve(index);
        assertEquals("I", theme.keyIndex.getReturnType());
        assertEquals("I", theme.color.getReturnType());
    }
}
