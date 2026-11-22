package cc.ioctl.tmoe.rtti.deobf;

import org.junit.Assume;
import org.junit.Test;
import java.io.File;
import static org.junit.Assert.*;

public class MessageTimeSymbolsTest {
    @Test
    public void resolvesTimestampAndDistinctWidthFieldsFromHostArtifact() throws Exception {
        String apk = System.getenv("TMOE_HOST_APK");
        Assume.assumeTrue(apk != null);
        MessageTimeSymbols s = new MessageTimeSymbols(HostDexIndex.open(new File(apk)));
        assertEquals("Ljava/lang/CharSequence;", s.time.getType());
        assertEquals("Landroid/text/TextPaint;", s.paint.getType());
        assertEquals("I", s.width.getType());
        assertEquals("I", s.textWidth.getType());
        assertNotEquals(s.width, s.textWidth);
        assertEquals(s.measureTime.getParameterTypes().get(0), s.messageOwner.getDefiningClass());
    }
}
