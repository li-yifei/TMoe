package cc.ioctl.tmoe.hook.core;

import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;

import cc.ioctl.tmoe.R;
import cc.ioctl.tmoe.fragment.SettingsFragment;
import cc.ioctl.tmoe.lifecycle.Parasitics;
import cc.ioctl.tmoe.rtti.ProxyFragmentRttiHandler;
import cc.ioctl.tmoe.rtti.deobf.HostDexIndex;
import cc.ioctl.tmoe.rtti.deobf.SettingsDexResolver;
import cc.ioctl.tmoe.ui.LocaleController;
import cc.ioctl.tmoe.util.HostInfo;
import cc.ioctl.tmoe.util.Initiator;
import cc.ioctl.tmoe.util.Reflex;
import cc.ioctl.tmoe.util.Utils;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

public class SettingEntryHook implements Initializable, ProfileActivityRowHook.Callback {
    public static final SettingEntryHook INSTANCE = new SettingEntryHook();

    private SettingEntryHook() {
    }

    private static final String TMOE_SETTINGS_ROW = "TMOE_SETTINGS_ROW";
    private static final int TMOE_SETTINGS_ITEM_ID = 0x7F0E0001;
    private static final int SETTINGS_ACTIVITY_LANGUAGE_ITEM_ID = 10;
    private static final int SETTINGS_ACTIVITY_ICON_COLOR_TOP = 0xFFB07AF5;
    private static final int SETTINGS_ACTIVITY_ICON_COLOR_BOTTOM = 0xFF8C57E8;
    private static Method sSettingsCellFactoryMethod = null;
    private static boolean sSettingsActivityHooked = false;
    private static Field sSettingsItemId;

    private boolean mInitialized = false;

    @Override
    public boolean initialize() {
        if (mInitialized) {
            return true;
        }
        if (!hookSettingsActivityEntry()) {
            return false;
        }
        ProfileActivityRowHook.addCallback(this);
        mInitialized = true;
        return true;
    }

    private static void presentTMoeSettingsFragment(@NonNull Object parentFragment) {
        ProxyFragmentRttiHandler.staticPresentFragmentFrom(parentFragment, new SettingsFragment());
    }

    @Override
    public boolean onBindViewHolder(@NonNull String key, @NonNull Object holder, @NonNull Object adpater, @NonNull Object profileActivity) {
        if (!TMOE_SETTINGS_ROW.equals(key)) {
            return false;
        }
        FrameLayout textCell = (FrameLayout) Reflex.getInstanceObjectOrNull(holder, "itemView");
        if (textCell != null) {
            // color and theme is already set by Telegram, we only need to set the text and icon
            // textCell.setTextAndIcon(text, iconResId, true)
            // inject resources
            Parasitics.injectModuleResources(textCell.getContext().getResources());
            Parasitics.injectModuleResources(HostInfo.getApplication().getResources());
            String text = LocaleController.getString("TMoeSettings", R.string.TMoeSettings);
            int iconResId = R.drawable.ic_setting_hex_outline_24;
            try {
                try {
                    Reflex.invokeVirtual(textCell, "setTextAndIcon", text, iconResId, true,
                            CharSequence.class, int.class, boolean.class, void.class);
                } catch (NoSuchMethodException e) {
                    Reflex.invokeVirtual(textCell, "setTextAndIcon", text, iconResId, true,
                            String.class, int.class, boolean.class, void.class);
                }
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        } else {
            Utils.loge(new IllegalStateException("textCell is null"));
        }
        return true;
    }

    @Override
    public int getItemViewType(@NonNull String key, @NonNull Object adapter, @NonNull Object profileActivity) {
        if (TMOE_SETTINGS_ROW.equals(key)) {
            return 4;
        }
        return -1;
    }

    @Override
    public boolean onItemClicked(@NonNull String key, @NonNull Object adapter, @NonNull Object profileActivity) {
        if (TMOE_SETTINGS_ROW.equals(key)) {
            presentTMoeSettingsFragment(profileActivity);
            return false;
        }
        return false;
    }

    @Override
    public void onInsertRow(@NonNull ProfileActivityRowHook.RowManipulator manipulator, @NonNull Object profileActivity) {
        // put our fields into the list just before the language row
        int targetRow = manipulator.getRowIdForField("languageRow");
        if (targetRow <= 0) {
            // languageRow is not, not user setting, so we can't do anything
            return;
        }
        manipulator.insertRowAtPosition(TMOE_SETTINGS_ROW, targetRow);
    }

    private static boolean hookSettingsActivityEntry() {
        if (sSettingsActivityHooked) {
            return true;
        }
        try {
            Class<?> kSettingsActivity = Initiator.load("org.telegram.ui.SettingsActivity");
            if (kSettingsActivity == null) {
                // Legacy hosts predate this screen. Optimized hosts retain ActionBarLayout,
                // while SettingsActivity and its private callbacks have been renamed.
                if (Initiator.load("org.telegram.ui.ChatActivity") == null) hookOptimizedSettings();
                return true;
            }
            Class<?> kSettingCellFactory = Initiator.load("org.telegram.ui.SettingsActivity$SettingCell$Factory");
            if (kSettingCellFactory == null) {
                Utils.loge("unable to find SettingsActivity$SettingCell$Factory");
                return false;
            }
            sSettingsCellFactoryMethod = kSettingCellFactory.getDeclaredMethod(
                    "of",
                    int.class, int.class, int.class, int.class, CharSequence.class, CharSequence.class, CharSequence.class
            );
            sSettingsCellFactoryMethod.setAccessible(true);

            Method fillItems = null;
            Method onClick = null;
            for (Method m : kSettingsActivity.getDeclaredMethods()) {
                if (fillItems == null && "fillItems".equals(m.getName())
                        && m.getParameterTypes().length == 2
                        && ArrayList.class.isAssignableFrom(m.getParameterTypes()[0])) {
                    fillItems = m;
                } else if (onClick == null && "onClick".equals(m.getName())
                        && m.getParameterTypes().length == 5) {
                    onClick = m;
                }
            }
            if (fillItems == null || onClick == null) {
                Utils.loge("unable to find SettingsActivity#fillItems or #onClick");
                return false;
            }

            installSettingsHooks(fillItems, onClick, 0, 0);
            return true;
        } catch (Throwable e) {
            Utils.loge(e);
            return false;
        }
    }

    private static void hookOptimizedSettings() throws Exception {
        SettingsDexResolver.Symbols symbols = SettingsDexResolver.resolve(HostDexIndex.get());
        sSettingsCellFactoryMethod = HostDexIndex.resolve(symbols.factory);
        sSettingsCellFactoryMethod.setAccessible(true);
        sSettingsItemId = sSettingsCellFactoryMethod.getReturnType().getDeclaredField(symbols.itemId.getName());
        sSettingsItemId.setAccessible(true);
        Method fill = HostDexIndex.resolve(symbols.fillItems);
        Method click = HostDexIndex.resolve(symbols.onClick);
        int listIndex = symbols.fillItems.getParameterTypes().indexOf("Ljava/util/ArrayList;");
        int itemIndex = symbols.onClick.getParameterTypes().indexOf(symbols.factory.getReturnType());
        installSettingsHooks(fill, click, listIndex, itemIndex);
        Utils.logd("Resolved optimized settings: " + symbols.fillItems + "; " + symbols.onClick);
    }

    public static boolean hasOptimizedSettingsActivityEntry() {
        return sSettingsActivityHooked && sSettingsItemId != null;
    }

    private static void installSettingsHooks(Method fillItems, Method onClick, int listIndex, int itemIndex) {
        XC_MethodHook.Unhook fillHook = XposedBridge.hookMethod(fillItems, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (!(param.args[listIndex] instanceof ArrayList)) {
                    return;
                }
                @SuppressWarnings("unchecked")
                ArrayList<Object> items = (ArrayList<Object>) param.args[listIndex];
                if (items.isEmpty()) {
                    return;
                }
                int insertAt = findSettingsActivityLanguageItemIndex(items);
                if (insertAt < 0 || containsSettingsActivityItem(items, TMOE_SETTINGS_ITEM_ID)) {
                    return;
                }
                Object item = createSettingsActivityItem();
                if (item != null) {
                    items.add(insertAt, item);
                }
            }
        });

        try {
            XposedBridge.hookMethod(onClick, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    Object item = param.args[itemIndex];
                    if (getSettingsActivityItemId(item) == TMOE_SETTINGS_ITEM_ID) {
                        Object parent = Modifier.isStatic(onClick.getModifiers()) ? param.args[0] : param.thisObject;
                        presentTMoeSettingsFragment(parent);
                        param.setResult(null);
                    }
                }
            });
        } catch (Throwable error) {
            fillHook.unhook();
            throw error;
        }
        sSettingsActivityHooked = true;
    }

    private static Object createSettingsActivityItem() {
        if (sSettingsCellFactoryMethod == null) {
            return null;
        }
        try {
            Parasitics.injectModuleResources(HostInfo.getApplication().getResources());
            String text = LocaleController.getString("TMoeSettings", R.string.TMoeSettings);
            return sSettingsCellFactoryMethod.invoke(
                    null,
                    TMOE_SETTINGS_ITEM_ID,
                    SETTINGS_ACTIVITY_ICON_COLOR_TOP,
                    SETTINGS_ACTIVITY_ICON_COLOR_BOTTOM,
                    R.drawable.ic_setting_hex_outline_24,
                    text,
                    null,
                    null
            );
        } catch (Throwable e) {
            Utils.loge(e);
            return null;
        }
    }

    private static int findSettingsActivityLanguageItemIndex(@NonNull ArrayList<?> items) {
        for (int i = 0; i < items.size(); i++) {
            if (getSettingsActivityItemId(items.get(i)) == SETTINGS_ACTIVITY_LANGUAGE_ITEM_ID) {
                return i;
            }
        }
        return -1;
    }

    private static boolean containsSettingsActivityItem(@NonNull ArrayList<?> items, int itemId) {
        for (Object item : items) {
            if (getSettingsActivityItemId(item) == itemId) {
                return true;
            }
        }
        return false;
    }

    private static int getSettingsActivityItemId(Object item) {
        if (item != null && sSettingsItemId != null) {
            try {
                return sSettingsItemId.getInt(item);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
        Object id = item == null ? null : Reflex.getInstanceObjectOrNull(item, "id");
        return id instanceof Number ? ((Number) id).intValue() : Integer.MIN_VALUE;
    }
}
