package cc.ioctl.tmoe.rtti;

import android.content.Context;

import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.Opcode;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.iface.instruction.Instruction;
import org.jf.dexlib2.immutable.ImmutableClassDef;
import org.jf.dexlib2.immutable.ImmutableDexFile;
import org.jf.dexlib2.immutable.ImmutableField;
import org.jf.dexlib2.immutable.ImmutableMethod;
import org.jf.dexlib2.immutable.ImmutableMethodImplementation;
import org.jf.dexlib2.immutable.ImmutableMethodParameter;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction10x;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction11x;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction21c;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction22c;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction35c;
import org.jf.dexlib2.immutable.reference.ImmutableFieldReference;
import org.jf.dexlib2.immutable.reference.ImmutableMethodReference;
import org.jf.dexlib2.immutable.reference.ImmutableTypeReference;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import cc.ioctl.tmoe.BuildConfig;
import cc.ioctl.tmoe.util.dex.DexFieldDescriptor;
import cc.ioctl.tmoe.util.dex.DexMethodDescriptor;
import cc.ioctl.tmoe.util.HostInfo;

public class ProxyFragmentImplDexCreator {

    private static final int ACC_CONSTRUCTOR = 0x00010000;
    private static final String PROXY_FRAGMENT_IMPL_CLASS_NAME = "cc.ioctl.tmoe.dynamic.ProxyFragmentImpl";
    private static final String PROXY_FRAGMENT_IMPL_TYPE = "L" + PROXY_FRAGMENT_IMPL_CLASS_NAME.replace(".", "/") + ";";
    private static final String RTTI_HANDLER_TYPE = "L" + ProxyFragmentRttiHandler.class.getName().replace(".", "/") + ";";
    private static final String OBJECT_TYPE = "Ljava/lang/Object;";
    private static final String VIEW_GROUP_TYPE = "Landroid/view/ViewGroup;";

    private ProxyFragmentImplDexCreator() {
        throw new AssertionError("no instance for you!");
    }

    public static File createProxyFragmentImplDex(Class<?> baseFragmentClass, Class<?> actionBar, Class<?> actionBarLayout)
            throws ReflectiveOperationException, IOException {
        String hostAppVersionName = HostInfo.getVersionName();
        String pluginVersionName = BuildConfig.VERSION_NAME;
        String hashCode = String.valueOf(Objects.hash(baseFragmentClass, actionBar, actionBarLayout,
                hostAppVersionName, pluginVersionName, HostInfo.getVersionCode(), BuildConfig.VERSION_CODE, BuildConfig.BUILD_UUID));
        String dexName = "ProxyFragmentImpl_" + hostAppVersionName + "_" + pluginVersionName + "_" + hashCode + ".dex";
        Context ctx = HostInfo.getApplication();
        File dexTmpDir = new File(ctx.getFilesDir(), "tmoe_dyn_dex");
        if (!dexTmpDir.exists()) {
            dexTmpDir.mkdirs();
        }
        // remove old dex if exists
        File[] oldDexFileNames = dexTmpDir.listFiles();
        if (oldDexFileNames != null) {
            for (File f : oldDexFileNames) {
                if ((f.getName().startsWith("ProxyFragmentImpl_") && f.getName().endsWith(".dex"))
                        && !f.getName().equals(dexName)) {
                    f.delete();
                }
            }
        }
        // check if dex file exists
        File dexFile = new File(dexTmpDir, dexName);
        if (dexFile.exists() && dexFile.length() > 0) {
            return dexFile;
        }
        ImmutableDexFile proxyDex = generateProxyDex(baseFragmentClass, actionBar, actionBarLayout);
        dexFile.createNewFile();
        DexFileFactory.writeDexFile(dexFile.getAbsolutePath(), proxyDex);
        dexFile.setReadOnly();
        return dexFile;
    }

    // Kept separate from the on-device cache so host API compatibility can be tested on the JVM.
    static ImmutableDexFile generateProxyDex(Class<?> baseFragmentClass, Class<?> actionBar,
                                             Class<?> actionBarLayout) throws NoSuchMethodException {
        String sigBaseFragment = DexFieldDescriptor.getTypeSig(baseFragmentClass);
        ArrayList<org.jf.dexlib2.iface.Method> declaredMethods = new ArrayList<>();
        for (Method m : IProxyFragmentObject.class.getDeclaredMethods()) {
            if (!m.getName().endsWith("$super")) {
                continue;
            }
            String name = m.getName().substring(0, m.getName().length() - "$super".length());
            Method host = findHostMethod(baseFragmentClass, actionBar, actionBarLayout, name, m, true);
            if (host == null) {
                throw new NoSuchMethodException("Missing imported method in host " + baseFragmentClass + ": " + m);
            }
            declaredMethods.add(generateProxySuperStubMethod(sigBaseFragment,
                    new DexMethodDescriptor(host), new DexMethodDescriptor(m), actionBar, actionBarLayout));
        }
        for (Method m : ProxyFragmentRttiHandler.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(m.getModifiers()) || Modifier.isStatic(m.getModifiers())
                    || !m.getName().endsWith("$dispatcher")) {
                continue;
            }
            String name = m.getName().substring(0, m.getName().length() - "$dispatcher".length());
            Method host = findHostMethod(baseFragmentClass, actionBar, actionBarLayout, name, m, false);
            if (host != null) {
                // Use the actual host descriptor for the override, including covariant return types.
                declaredMethods.add(generateProxyDispatcherMethod(sigBaseFragment,
                        new DexMethodDescriptor(host), actionBar, actionBarLayout));
            }
            // An absent or incompatible callback is inherited from the host. In particular,
            // finishFragment(boolean):boolean must retain the host's success/failure result.
        }
        declaredMethods.addAll(generateProxyClassConstructor(sigBaseFragment));
        ImmutableField handlerField = new ImmutableField(PROXY_FRAGMENT_IMPL_TYPE, "h",
                RTTI_HANDLER_TYPE, Modifier.PRIVATE, null, null, null);
        ImmutableClassDef classDef = new ImmutableClassDef(PROXY_FRAGMENT_IMPL_TYPE, Modifier.PUBLIC,
                sigBaseFragment, Collections.singletonList(DexMethodDescriptor.getTypeSig(IProxyFragmentObject.class)),
                "ProxyFragmentImpl.dexlib2", null, Collections.singletonList(handlerField), declaredMethods);
        return new ImmutableDexFile(Opcodes.forDexVersion(35), Collections.singletonList(classDef));
    }

    private static String bridgeType(Class<?> type, Class<?> base, Class<?> actionBar, Class<?> layout) {
        if (type == base) return OBJECT_TYPE;
        if (type == actionBar || type == layout) return VIEW_GROUP_TYPE;
        return DexMethodDescriptor.getTypeSig(type);
    }

    private static Method findHostMethod(Class<?> base, Class<?> actionBar, Class<?> layout,
                                          String name, Method bridge, boolean superCall) {
        for (Class<?> owner = base; owner != null; owner = owner.getSuperclass()) {
            for (Method host : owner.getDeclaredMethods()) {
                int modifiers = host.getModifiers();
                if (!host.getName().equals(name) || Modifier.isStatic(modifiers)
                        || !(Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers))
                        || (!superCall && Modifier.isFinal(modifiers))) {
                    continue;
                }
                Class<?>[] params = host.getParameterTypes();
                Class<?>[] bridgeParams = bridge.getParameterTypes();
                if (params.length != bridgeParams.length) continue;
                boolean matches = true;
                for (int i = 0; i < params.length; i++) {
                    matches &= bridgeType(params[i], base, actionBar, layout)
                            .equals(DexMethodDescriptor.getTypeSig(bridgeParams[i]));
                }
                // A void super bridge may discard a host return value (Telegram 12.x finishFragment).
                boolean returnMatches = superCall && bridge.getReturnType() == void.class
                        || bridgeType(host.getReturnType(), base, actionBar, layout)
                        .equals(DexMethodDescriptor.getTypeSig(bridge.getReturnType()));
                if (matches && returnMatches) return host;
            }
        }
        return null;
    }

    private static ArrayList<ImmutableMethod> generateProxyClassConstructor(String hostBaseFragmentTypeSig) {
        // smali for constructor
        // .method public constructor <init>(Lcc/ioctl/tmoe/rtti/ProxyFragmentRttiHandler;Landroid/os/Bundle;)V
        //     .registers 3
        //     .parameter "handler"
        //     .parameter "args"
        //     .prologue
        //     invoke-direct {p0, p2}, LBaseFragment;-><init>(Landroid/os/Bundle;)V
        //     iput-object p1, p0, Lcc/ioctl/tmoe/dynamic/ProxyFragmentImpl;->h:Lcc/ioctl/tmoe/rtti/ProxyFragmentRttiHandler;
        //     return-void
        // .end method
        ArrayList<ImmutableMethod> constructors = new ArrayList<>();
        ArrayList<Instruction> insCtor = new ArrayList<>();
        // invoke-direct {p0, p2}, LBaseFragment;-><init>(Landroid/os/Bundle;)V
        insCtor.add(new ImmutableInstruction35c(Opcode.INVOKE_DIRECT, 2, 0, 2, 0, 0, 0,
                referenceMethod(hostBaseFragmentTypeSig, "<init>", "(Landroid/os/Bundle;)V")));
        // iput-object p1, p0, LProxyFragmentImpl;->h:Lcc/ioctl/tmoe/rtti/ProxyFragmentRttiHandler;
        insCtor.add(new ImmutableInstruction22c(Opcode.IPUT_OBJECT, 1, 0,
                referenceField(PROXY_FRAGMENT_IMPL_TYPE, "h", RTTI_HANDLER_TYPE)));
        // return-void
        insCtor.add(new ImmutableInstruction10x(Opcode.RETURN_VOID));
        ImmutableMethodImplementation ctorMethodImpl = new ImmutableMethodImplementation(3, insCtor, null, null);
        ImmutableMethod ctorMethod = new ImmutableMethod(PROXY_FRAGMENT_IMPL_TYPE, "<init>", Arrays.asList(
                new ImmutableMethodParameter(RTTI_HANDLER_TYPE, null, "handler"),
                new ImmutableMethodParameter("Landroid/os/Bundle;", null, "args")),
                "V", Modifier.PUBLIC | ACC_CONSTRUCTOR, null, null, ctorMethodImpl);
        constructors.add(ctorMethod);
        return constructors;
    }

    private static ImmutableMethod generateProxySuperStubMethod(String hostBaseFragmentTypeSig, DexMethodDescriptor targetMethodDescriptor,
                                                                DexMethodDescriptor bridgeMethodDescriptor, Class<?> kActionBar, Class<?> kActionBarLayout) {
        String methodName = targetMethodDescriptor.name;
        String generatedMethodName = methodName + "$super";
        List<String> parameterTypeList = targetMethodDescriptor.getParameterTypes();
        String parameterTypeSig = targetMethodDescriptor.signature;
        String returnType = targetMethodDescriptor.getReturnType();
        int parameterCount = parameterTypeList.size();
        // replace host obfuscated class name with ViewGroup or Object
        String returnTypeMitigated = bridgeMethodDescriptor.getReturnType();
        List<String> parameterTypeListMitigated = new ArrayList<>();
        for (String parameterType : parameterTypeList) {
            parameterTypeListMitigated.add(parameterType.replace(DexMethodDescriptor.getTypeSig(kActionBar), VIEW_GROUP_TYPE)
                    .replace(DexMethodDescriptor.getTypeSig(kActionBarLayout), VIEW_GROUP_TYPE)
                    .replace(hostBaseFragmentTypeSig, OBJECT_TYPE));
        }
        // smali for method, no local variable
        // .method public ${generatedMethodName}(${parameterTypeSigMitigated})${returnTypeMitigated}
        // .registers 5
        // .prologue
        // invoke-super {p0, p1, p2, p3}, ${hostBaseFragmentTypeSig}->${methodName}(${parameterTypeSig})${returnType}
        // #if returnType == "V"
        // return-void
        // #elseif returnType.startsWith("L") || returnType.startsWith("[")
        // move-result-object v0
        // return-object v0
        // #else
        // move-result v0
        // return v0
        // #endif
        // .end method
        List<Instruction> insSuperStub = new ArrayList<>();
        // check cast if the parameter type is mitigated, make ART verifier happy
        for (int i = 0; i < parameterCount; i++) {
            String parameterTypeMitigated = parameterTypeListMitigated.get(i);
            String parameterType = parameterTypeList.get(i);
            if (!parameterTypeMitigated.equals(parameterType)) {
                ImmutableTypeReference typeRef = new ImmutableTypeReference(parameterType);
                // p0 is this, p1 is the first parameter, p2 is the second parameter, p3 is the third parameter
                insSuperStub.add(new ImmutableInstruction21c(Opcode.CHECK_CAST, i + 1, typeRef));
            }
        }
        insSuperStub.add(new ImmutableInstruction35c(Opcode.INVOKE_SUPER, parameterCount + 1, 0, 1, 2, 3, 4,
                referenceMethod(hostBaseFragmentTypeSig, methodName, parameterTypeSig)));
        if (returnTypeMitigated.equals("V")) {
            insSuperStub.add(new ImmutableInstruction10x(Opcode.RETURN_VOID));
        } else if (returnType.startsWith("L") || returnType.startsWith("[")) {
            insSuperStub.add(new ImmutableInstruction11x(Opcode.MOVE_RESULT_OBJECT, 0));
            insSuperStub.add(new ImmutableInstruction11x(Opcode.RETURN_OBJECT, 0));
        } else {
            insSuperStub.add(new ImmutableInstruction11x(Opcode.MOVE_RESULT, 0));
            insSuperStub.add(new ImmutableInstruction11x(Opcode.RETURN, 0));
        }
        ArrayList<ImmutableMethodParameter> parameters = new ArrayList<>();
        for (int i = 0; i < parameterCount; i++) {
            parameters.add(new ImmutableMethodParameter(parameterTypeListMitigated.get(i), null, "p" + (i + 1)));
        }
        ImmutableMethodImplementation methodImpl = new ImmutableMethodImplementation(parameterCount + 1, insSuperStub, null, null);
        return new ImmutableMethod(PROXY_FRAGMENT_IMPL_TYPE, generatedMethodName, parameters,
                returnTypeMitigated, Modifier.PUBLIC, null, null, methodImpl);
    }

    private static ImmutableMethod generateProxyDispatcherMethod(String hostBaseFragmentTypeSig, DexMethodDescriptor targetMethodDescriptor,
                                                                 Class<?> kActionBar, Class<?> kActionBarLayout) {
        String methodName = targetMethodDescriptor.name;
        String dispatchMethodName = methodName + "$dispatcher";
        List<String> parameterTypeList = targetMethodDescriptor.getParameterTypes();
        String parameterTypeSig = targetMethodDescriptor.signature;
        String returnType = targetMethodDescriptor.getReturnType();
        int parameterCount = parameterTypeList.size(); // including this
        // replace host obfuscatable class name with ViewGroup or Object
        String returnTypeMitigated = returnType.replace(DexMethodDescriptor.getTypeSig(kActionBar), VIEW_GROUP_TYPE)
                .replace(DexMethodDescriptor.getTypeSig(kActionBarLayout), VIEW_GROUP_TYPE)
                .replace(hostBaseFragmentTypeSig, OBJECT_TYPE);
        String parameterTypeSigMitigated = parameterTypeSig.replace(DexMethodDescriptor.getTypeSig(kActionBar), VIEW_GROUP_TYPE)
                .replace(DexMethodDescriptor.getTypeSig(kActionBarLayout), VIEW_GROUP_TYPE)
                .replace(hostBaseFragmentTypeSig, OBJECT_TYPE);
        List<String> parameterTypeListMitigated = new ArrayList<>();
        for (String parameterType : parameterTypeList) {
            parameterTypeListMitigated.add(parameterType.replace(DexMethodDescriptor.getTypeSig(kActionBar), VIEW_GROUP_TYPE)
                    .replace(DexMethodDescriptor.getTypeSig(kActionBarLayout), VIEW_GROUP_TYPE)
                    .replace(hostBaseFragmentTypeSig, OBJECT_TYPE));
        }
        // smali for super stub method
        // .method public ${methodName}(${parameterTypeSig})${returnType}
        // .locals 1
        // .prologue
        // iget-object v0, p0, Lcc/ioctl/tmoe/dynamic/ProxyFragmentImpl;->h:Lcc/ioctl/tmoe/rtti/ProxyFragmentRttiHandler;
        // invoke-virtual {v0, p0, p1, p2}, Lcc/ioctl/tmoe/rtti/ProxyFragmentRttiHandler;->${dispatchMethodName}(${parameterTypeSigMitigated})${returnTypeMitigated}
        // #if returnTypeMitigated == "V"
        // return-void
        // #elseif returnTypeMitigated.startsWith("L") || returnTypeMitigated.startsWith("[")
        // move-result-object v0
        // return-object v0
        // #else
        // move-result v0
        // return v0
        // #endif
        // .end method
        ArrayList<ImmutableInstruction> insStub = new ArrayList<>();
        int localVarCount = 1;
        insStub.add(new ImmutableInstruction22c(Opcode.IGET_OBJECT, 0, localVarCount + 0,
                referenceField(PROXY_FRAGMENT_IMPL_TYPE, "h", RTTI_HANDLER_TYPE)));
        insStub.add(new ImmutableInstruction35c(Opcode.INVOKE_VIRTUAL, parameterCount + 1,
                0, localVarCount + 1, localVarCount + 2, localVarCount + 3, localVarCount + 4,
                referenceMethod(RTTI_HANDLER_TYPE, dispatchMethodName, parameterTypeSigMitigated)));
        if (returnTypeMitigated.equals("V")) {
            insStub.add(new ImmutableInstruction10x(Opcode.RETURN_VOID));
        } else if (returnTypeMitigated.startsWith("L") || returnTypeMitigated.startsWith("[")) {
            insStub.add(new ImmutableInstruction11x(Opcode.MOVE_RESULT_OBJECT, 0));
            // check cast if the return type is mitigated, make ART verifier happy
            // result object is in v0
            if (!returnTypeMitigated.equals(returnType)) {
                insStub.add(new ImmutableInstruction21c(Opcode.CHECK_CAST, 0, new ImmutableTypeReference(returnType)));
            }
            insStub.add(new ImmutableInstruction11x(Opcode.RETURN_OBJECT, 0));
        } else {
            insStub.add(new ImmutableInstruction11x(Opcode.MOVE_RESULT, 0));
            insStub.add(new ImmutableInstruction11x(Opcode.RETURN, 0));
        }
        ArrayList<ImmutableMethodParameter> parameters = new ArrayList<>();
        for (int i = 0; i < parameterCount; i++) {
            parameters.add(new ImmutableMethodParameter(parameterTypeList.get(i), null, "p" + (i + 1)));
        }
        ImmutableMethodImplementation methodImpl = new ImmutableMethodImplementation(localVarCount + parameterCount + 1, insStub, null, null);
        return new ImmutableMethod(PROXY_FRAGMENT_IMPL_TYPE, methodName, parameters, returnType,
                Modifier.PUBLIC, null, null, methodImpl);
    }

    private static ImmutableMethodReference referenceMethod(String declaringClass, String name, String descriptor) {
        return referenceMethod(new DexMethodDescriptor(declaringClass, name, descriptor));
    }

    private static ImmutableMethodReference referenceMethod(DexMethodDescriptor md) {
        return new ImmutableMethodReference(md.declaringClass, md.name, md.getParameterTypes(), md.getReturnType());
    }

    public static ImmutableFieldReference referenceField(String declaringClass, String name, String type) {
        return new ImmutableFieldReference(declaringClass, name, type);
    }
}
