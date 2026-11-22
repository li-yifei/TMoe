package cc.ioctl.tmoe.hook.func;

import cc.ioctl.tmoe.base.annotation.FunctionHookEntry;
import cc.ioctl.tmoe.hook.base.CommonDynamicHook;
import cc.ioctl.tmoe.util.Initiator;
import cc.ioctl.tmoe.util.Reflex;

@FunctionHookEntry
public class EnableDebugMode extends CommonDynamicHook {
    public static final EnableDebugMode INSTANCE = new EnableDebugMode();

    private EnableDebugMode() {
    }

    @Override
    public boolean isAvailable() {
        Class<?> buildVars = Initiator.load("org.telegram.messenger.BuildVars");
        if (buildVars == null) return false;
        try {
            return buildVars.getDeclaredField("DEBUG_VERSION").getType() == boolean.class;
        } catch (NoSuchFieldException e) {
            return false;
        }
    }

    @Override
    public boolean initOnce() throws Exception {
        if (!isAvailable()) return false;
        if (isEnabledByUser()) {
            updateValue(true);
        }
        return true;
    }

    @Override
    public void setEnabledByUser(boolean enabled) {
        super.setEnabledByUser(enabled);
        if (!isAvailable()) return;
        try {
            updateValue(enabled);
        } catch (NoSuchFieldException e) {
            loge(e);
        }
    }

    private void updateValue(boolean value) throws NoSuchFieldException {
        Reflex.setStaticObject(Initiator.load("org.telegram.messenger.BuildVars"), "DEBUG_VERSION", value);
        Reflex.setStaticObjectSilently(Initiator.load("org.telegram.messenger.BuildVars"), "DEBUG_PRIVATE_VERSION", value);
    }
}
