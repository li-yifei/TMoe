package cc.ioctl.tmoe.rtti.deobf;

import org.junit.Assume;
import org.junit.Test;
import org.jf.dexlib2.AccessFlags;
import java.io.File;
import java.util.List;
import static org.junit.Assert.*;

public class MemberHistorySymbolsTest {
    @Test
    public void resolvesHistoryAndMentionSymbolsFromHostApk() throws Exception {
        String path = System.getenv("TMOE_HOST_APK");
        Assume.assumeTrue("Set TMOE_HOST_APK to verify the actual host artifact", path != null);
        HostDexIndex index = HostDexIndex.open(new File(path));
        var history = MemberHistorySymbols.history(index);
        assertEquals(history.setCount.getDefiningClass(), history.dialogId.getDefiningClass());
        assertEquals("J", history.dialogId.getType());
        var mentions = MemberHistorySymbols.mentions(index);
        assertEquals("I", mentions.start.getType());
        assertEquals("I", mentions.length.getType());
        assertNotEquals(mentions.start, mentions.length);
        String sparse = mentions.showUsers.getParameterTypes().stream().map(Object::toString)
                .filter(p -> !p.equals("Z") && !p.equals("Ljava/util/ArrayList;")).findFirst().orElseThrow();
        var put = index.uniqueMethod(m -> m.getDefiningClass().equals(sparse)
                && !AccessFlags.STATIC.isSet(m.getAccessFlags()) && m.getReturnType().equals("V")
                && (m.getParameterTypes().equals(List.of("J", "Ljava/lang/Object;"))
                || m.getParameterTypes().equals(List.of("Ljava/lang/Object;", "J"))));
        assertNotNull(index.uniqueMethod(m -> m.getDefiningClass().equals(sparse)
                && m.getName().equals("<init>") && m.getParameterTypes().equals(List.of("I"))));
        System.out.println("Historic groups: " + history.setCount + ", " + history.dialogId);
        System.out.println("Mentions: " + mentions.search + ", " + mentions.showUsers + ", " + put);
        System.out.println("Mention fields: " + mentions.info + ", " + mentions.chat + ", "
                + mentions.parent + ", " + mentions.start + ", " + mentions.length);
    }
}
