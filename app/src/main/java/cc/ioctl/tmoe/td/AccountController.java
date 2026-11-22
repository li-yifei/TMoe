package cc.ioctl.tmoe.td;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.io.IOException;
import java.util.List;

import cc.ioctl.tmoe.rtti.deobf.AccountAccessSymbols;
import cc.ioctl.tmoe.rtti.deobf.HostDexIndex;
import cc.ioctl.tmoe.util.Initiator;
import cc.ioctl.tmoe.util.Utils;

/**
 * Static helper class for accessing the Telegram account status.
 *
 * @author cinit
 */
public class AccountController {
    private AccountController() {
        throw new AssertionError("no instance for you");
    }

    /**
     * Get the user id for the current active account slot.
     *
     * @param slot the account slot, non-negative
     * @return the user id, or 0 if the slot is not logged in, or exception occurs
     * @throws IllegalArgumentException if the slot is invalid
     */
    public static long getUserIdForSlot(int slot) {
        if (slot < 0 || slot > 32767) {
            throw new IllegalArgumentException("invalid slot: " + slot);
        }
        try {
            ConfigAccess access = config();
            return access.userId.getLong(access.instance.invoke(null, slot));
        } catch (ReflectiveOperationException | IOException | IllegalStateException e) {
            Utils.loge(e);
        }
        return 0;
    }

    /**
     * Get the current active account slot.
     * Notice that Telegram DOES support multiple accounts and the ongoing transaction may NOT always be the active one.
     * AVOID USE THIS METHOD OR USE WITH CAUTION.
     *
     * @return the current active account slot, or -1 if exception occurs
     * @see #getUserIdForSlot(int)
     */
    public static int getCurrentActiveSlot() {
        try {
            return config().selected.getInt(null);
        } catch (ReflectiveOperationException | IOException | IllegalStateException e) {
            Utils.loge(e);
        }
        return -1;
    }

    /**
     * Get current active account user id.
     * Notice that Telegram DOES support multiple accounts and the ongoing transaction may NOT always be the active one.
     * AVOID USING THIS METHOD OR USE WITH CAUTION.
     *
     * @return the current active user id, or 0 if not logged in, or exception occurs
     */
    public static long getCurrentActiveUserId() {
        int slot = getCurrentActiveSlot();
        if (slot < 0) {
            return 0;
        }
        return getUserIdForSlot(slot);
    }

    /**
     * Test if the current active account is logged in.
     *
     * @return false if the current active account is not logged in, or exception occurs
     */
    public static boolean isCurrentUserLoggedIn() {
        return getCurrentActiveUserId() != 0;
    }

    private static ConfigAccess configAccess;
    private static Method[] messageAccess;

    private static final class ConfigAccess {
        final Method instance;
        final Field selected, userId;

        ConfigAccess(Method instance, Field selected, Field userId) {
            this.instance = instance;
            this.selected = selected;
            this.userId = userId;
            instance.setAccessible(true);
            selected.setAccessible(true);
            userId.setAccessible(true);
        }
    }

    private static synchronized ConfigAccess config() throws ReflectiveOperationException, IOException {
        if (configAccess != null) return configAccess;
        try {
            Class<?> cls = Initiator.loadClass("org.telegram.messenger.UserConfig");
            configAccess = new ConfigAccess(cls.getDeclaredMethod("getInstance", int.class),
                    cls.getDeclaredField("selectedAccount"), cls.getDeclaredField("clientUserId"));
        } catch (ClassNotFoundException | NoSuchMethodException | NoSuchFieldException e) {
            AccountAccessSymbols.Config symbols = AccountAccessSymbols.config(HostDexIndex.get());
            Method instance = HostDexIndex.resolve(symbols.instance);
            Class<?> cls = instance.getDeclaringClass();
            configAccess = new ConfigAccess(instance,
                    cls.getDeclaredField(symbols.selectedAccount.getName()),
                    cls.getDeclaredField(symbols.clientUserId.getName()));
        }
        return configAccess;
    }

    private static synchronized Method[] messages() throws ReflectiveOperationException, IOException {
        if (messageAccess != null) return messageAccess;
        Method[] resolved;
        try {
            Class<?> cls = Initiator.loadClass("org.telegram.messenger.MessagesController");
            resolved = new Method[] {cls.getDeclaredMethod("getInstance", int.class),
                    cls.getDeclaredMethod("getChat", Long.class), cls.getDeclaredMethod("getUser", Long.class),
                    cls.getDeclaredMethod("getInputChannel", long.class)};
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            List<org.jf.dexlib2.iface.Method> symbols = AccountAccessSymbols.messages(HostDexIndex.get());
            resolved = new Method[symbols.size()];
            for (int i = 0; i < resolved.length; i++) resolved[i] = HostDexIndex.resolve(symbols.get(i));
        }
        for (Method method : resolved) method.setAccessible(true);
        messageAccess = resolved;
        return resolved;
    }

    public static Object getCachedChat(int slot, long id) throws ReflectiveOperationException, IOException {
        Method[] access = messages();
        return access[1].invoke(access[0].invoke(null, slot), id);
    }

    public static Object getCachedUser(int slot, long id) throws ReflectiveOperationException, IOException {
        Method[] access = messages();
        return access[2].invoke(access[0].invoke(null, slot), id);
    }

    public static Object getInputChannel(int slot, long id) throws ReflectiveOperationException, IOException {
        Method[] access = messages();
        return access[3].invoke(access[0].invoke(null, slot), id);
    }

    public static class NoUserLoginException extends Exception {
        public NoUserLoginException() {
            super("no user login");
        }

        public NoUserLoginException(String msg) {
            super(msg);
        }

        public NoUserLoginException(Throwable cause) {
            super(cause);
        }

        public NoUserLoginException(String msg, Throwable cause) {
            super(msg, cause);
        }
    }
}
