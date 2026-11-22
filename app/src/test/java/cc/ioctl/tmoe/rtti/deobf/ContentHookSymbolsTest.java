package cc.ioctl.tmoe.rtti.deobf;

import org.jf.dexlib2.iface.Method;
import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Optional artifact test: TMOE_HOST_APK points to the APK pulled from the target device. */
public class ContentHookSymbolsTest {
    @Test
    public void resolvesOptimizedContentHooksFromInstalledArtifact() throws Exception {
        String path = System.getenv("TMOE_HOST_APK");
        Assume.assumeTrue(path != null);
        HostDexIndex index = HostDexIndex.open(new File(path));
        Method shortNumber = ContentHookSymbols.shortNumber(index);
        assertEquals(2, shortNumber.getParameterTypes().size());
        assertTrue(shortNumber.getParameterTypes().contains("I"));
        assertTrue(shortNumber.getParameterTypes().contains("[I"));
        List<Method> forwarding = ContentHookSymbols.forwarding(index);
        assertEquals(4, forwarding.size());
        assertEquals(4, forwarding.stream().map(Object::toString).distinct().count());
        assertTrue(forwarding.stream().allMatch(m -> m.getReturnType().equals("Z")));
    }
}
