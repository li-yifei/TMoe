package cc.ioctl.tmoe.startup;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ApplicationLifecycleResolverTest {
    public static class RenamedApplication {
        public void onCreate() {}
    }

    public static class ManifestApplication extends RenamedApplication {}

    public static class LegacyApplication {
        public void onCreate() {}
    }

    @Test
    public void resolvesInheritedLifecycleFromManifestEntry() throws Exception {
        assertEquals(RenamedApplication.class,
                ApplicationLifecycleResolver.resolveOnCreate(getClass().getClassLoader(),
                        new String[]{ManifestApplication.class.getName()}).getDeclaringClass());
    }

    @Test
    public void fallsBackForOlderClientsWithoutStandaloneEntry() throws Exception {
        assertEquals(LegacyApplication.class,
                ApplicationLifecycleResolver.resolveOnCreate(getClass().getClassLoader(),
                        new String[]{"missing.ApplicationLoaderImpl", LegacyApplication.class.getName()})
                        .getDeclaringClass());
    }

    @Test(expected = ClassNotFoundException.class)
    public void rejectsAnUnknownHost() throws Exception {
        ApplicationLifecycleResolver.resolveOnCreate(getClass().getClassLoader(),
                new String[]{"missing.Application"});
    }
}
