package cc.ioctl.tmoe.rtti.deobf;

import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.util.List;

import static org.junit.Assert.*;

public class AdditionalUiHookSymbolsTest {
    @Test
    public void resolvesUiHooksFromInstalledApkWhenProvided() throws Exception {
        String path = System.getenv("TMOE_HOST_APK");
        Assume.assumeTrue("Set TMOE_HOST_APK to verify the actual host artifact", path != null);
        HostDexIndex index = HostDexIndex.open(new File(path));
        var video = AdditionalUiHookSymbols.maybePlayVisibleVideo(index);
        assertEquals("Z", video.getReturnType());
        assertTrue(video.getParameterTypes().isEmpty());
        var blur = AdditionalUiHookSymbols.canBlurChat(index);
        assertEquals("Z", blur.getReturnType());
        var camera = AdditionalUiHookSymbols.cameraMode(index);
        assertEquals(List.of("Z", "Z"), camera.setter.getParameterTypes());
        assertEquals("Z", camera.hasRecordVideo.getType());
        var greetings = AdditionalUiHookSymbols.greetings(index);
        assertEquals(2, greetings.hiddenViewFields.size());
        System.out.println("KeepVideoMuted: " + video);
        System.out.println("ForceBlurChatAvailable: " + blur);
        System.out.println("DisableInstantCamera: " + camera.setter + ", " + camera.hasRecordVideo);
        System.out.println("ProhibitChatGreetings: " + greetings.className + ", " + greetings.hiddenViewFields);
    }
}
