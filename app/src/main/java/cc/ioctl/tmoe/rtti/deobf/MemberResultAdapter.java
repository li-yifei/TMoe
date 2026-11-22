package cc.ioctl.tmoe.rtti.deobf;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;

/** Binds mention results across R8 parameter reordering and LongSparseArray renaming. */
public final class MemberResultAdapter {
    private final Method show, put;
    private final Constructor<?> constructor;
    private final int listIndex, sparseIndex, notifyIndex;
    private final boolean keyFirst;

    public MemberResultAdapter(Method show) throws ReflectiveOperationException {
        this.show = show;
        Class<?>[] parameters = show.getParameterTypes();
        if (parameters.length != 3 || show.getReturnType() != void.class) {
            throw new NoSuchMethodException("Unexpected mention result signature: " + show);
        }
        listIndex = Arrays.asList(parameters).indexOf(ArrayList.class);
        notifyIndex = Arrays.asList(parameters).indexOf(boolean.class);
        if (listIndex < 0 || notifyIndex < 0) {
            throw new NoSuchMethodException("Missing mention result arguments: " + show);
        }
        sparseIndex = 3 - listIndex - notifyIndex;
        Class<?> sparse = parameters[sparseIndex];
        constructor = sparse.getDeclaredConstructor(int.class);
        Method resolvedPut;
        try {
            resolvedPut = sparse.getDeclaredMethod("put", long.class, Object.class);
        } catch (NoSuchMethodException missingName) {
            Method[] putMethods = Arrays.stream(sparse.getDeclaredMethods())
                    .filter(m -> !Modifier.isStatic(m.getModifiers()) && m.getReturnType() == void.class
                            && (Arrays.equals(m.getParameterTypes(), new Class<?>[]{long.class, Object.class})
                            || Arrays.equals(m.getParameterTypes(), new Class<?>[]{Object.class, long.class})))
                    .toArray(Method[]::new);
            if (putMethods.length != 1) {
                throw new NoSuchMethodException("Expected one sparse put method, found " + putMethods.length);
            }
            resolvedPut = putMethods[0];
        }
        put = resolvedPut;
        keyFirst = put.getParameterTypes()[0] == long.class;
        constructor.setAccessible(true);
        put.setAccessible(true);
        show.setAccessible(true);
    }

    public void show(Object adapter, ArrayList<?> values, long[] ids) throws ReflectiveOperationException {
        if (values.size() != ids.length) throw new IllegalArgumentException("Mismatched member IDs and values");
        Object sparse = constructor.newInstance(values.size());
        for (int i = 0; i < ids.length; i++) {
            if (keyFirst) put.invoke(sparse, ids[i], values.get(i));
            else put.invoke(sparse, values.get(i), ids[i]);
        }
        Object[] args = new Object[3];
        args[listIndex] = values;
        args[sparseIndex] = sparse;
        args[notifyIndex] = true;
        show.invoke(adapter, args);
    }
}
