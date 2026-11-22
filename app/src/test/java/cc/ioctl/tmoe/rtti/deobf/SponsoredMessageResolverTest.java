package cc.ioctl.tmoe.rtti.deobf;

import org.jf.dexlib2.AccessFlags;
import org.jf.dexlib2.Opcode;
import org.jf.dexlib2.immutable.ImmutableMethod;
import org.jf.dexlib2.immutable.ImmutableMethodImplementation;
import org.jf.dexlib2.immutable.ImmutableMethodParameter;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction21c;
import org.jf.dexlib2.immutable.reference.ImmutableTypeReference;
import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.util.List;

import static org.junit.Assert.*;

public class SponsoredMessageResolverTest {
    private static ImmutableMethod getter(String owner, String type, String result, int access) {
        return new ImmutableMethod(owner, "renamed", List.of(
                new ImmutableMethodParameter(type, null, null)), result, access,
                null, null, new ImmutableMethodImplementation(3, List.of(
                        new ImmutableInstruction21c(Opcode.NEW_INSTANCE, 0,
                                new ImmutableTypeReference(
                                        "Lorg/telegram/tgnet/TLRPC$TL_messages_getSponsoredMessages;"))),
                null, null));
    }

    @Test
    public void resolvesRenamedCacheAccessorAndRejectsOtherRequestProducers() {
        assertTrue(SponsoredMessageResolver.isGetter(getter(
                "Lorg/telegram/messenger/a;", "J", "Lorg/telegram/messenger/b;", AccessFlags.PUBLIC.getValue())));
        assertFalse(SponsoredMessageResolver.isGetter(getter(
                "Lorg/telegram/messenger/video/VideoAds;", "J", "V", AccessFlags.PUBLIC.getValue())));
        assertFalse(SponsoredMessageResolver.isGetter(getter(
                "Lorg/telegram/ui/a;", "J", "Ljava/lang/Object;", AccessFlags.PUBLIC.getValue())));
        assertFalse(SponsoredMessageResolver.isGetter(getter(
                "Lorg/telegram/messenger/a;", "I", "Ljava/lang/Object;", AccessFlags.PUBLIC.getValue())));
        assertFalse(SponsoredMessageResolver.isGetter(getter(
                "Lorg/telegram/messenger/a;", "J", "Ljava/lang/Object;",
                AccessFlags.PUBLIC.getValue() | AccessFlags.STATIC.getValue())));
    }

    @Test
    public void resolvesInstalledApkWhenProvided() throws Exception {
        String path = System.getenv("TMOE_HOST_APK");
        Assume.assumeTrue("Set TMOE_HOST_APK to verify the actual host artifact", path != null);
        HostDexIndex index = HostDexIndex.open(new File(path));
        var getter = SponsoredMessageResolver.findGetter(index);
        assertTrue(SponsoredMessageResolver.isGetter(getter));
        assertFalse(index.findMethods(SponsoredMessageResolver::isRequestEntry).isEmpty());
        System.out.println("Sponsored message getter: " + getter);
        System.out.println("Sponsored click request entry points: "
                + index.findMethods(SponsoredMessageResolver::isRequestEntry));
    }
}
