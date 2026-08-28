package gq.yozakura.bridge;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the tiny subclass used to preserve a client-provided EntityRenderer.
 * It intentionally uses only the Java class-file format so the standalone
 * bridge does not assume Lunar exposes a particular ASM version.
 */
final class RuntimeEntityRendererHookGenerator {
    private static final int ACC_PUBLIC = 0x0001;
    private static final int ACC_FINAL = 0x0010;
    private static final int ACC_SUPER = 0x0020;

    private RuntimeEntityRendererHookGenerator() {
    }

    static byte[] generate(String binaryName, Class<?> runtimeRendererType) throws IOException {
        return generate(binaryName, runtimeRendererType, RuntimeEntityRendererHookCallbacks.class);
    }

    static byte[] generate(String binaryName, Class<?> runtimeRendererType, Class<?> callbackType) throws IOException {
        String hookInternalName = binaryName.replace('.', '/');
        String rendererInternalName = runtimeRendererType.getName().replace('.', '/');
        String bridgeInternalName = callbackType.getName().replace('.', '/');

        ConstantPool pool = new ConstantPool();
        int hookClass = pool.classInfo(hookInternalName);
        int rendererClass = pool.classInfo(rendererInternalName);
        int throwableClass = pool.classInfo("java/lang/Throwable");
        int codeAttribute = pool.utf8("Code");

        int renderWorldName = pool.utf8("renderWorld");
        int renderWorldDescriptor = pool.utf8("(FJ)V");
        int getMouseOverName = pool.utf8("getMouseOver");
        int getMouseOverDescriptor = pool.utf8("(F)V");
        int updateCameraAndRenderName = pool.utf8("updateCameraAndRender");
        int updateCameraAndRenderDescriptor = pool.utf8("(FJ)V");

        int superRenderWorld = pool.methodRef(rendererInternalName, "renderWorld", "(FJ)V");
        int superGetMouseOver = pool.methodRef(rendererInternalName, "getMouseOver", "(F)V");
        int superUpdateCameraAndRender = pool.methodRef(rendererInternalName, "updateCameraAndRender", "(FJ)V");
        int beginRenderWorld = pool.methodRef(bridgeInternalName, "beginRuntimeRenderWorld", "(F)V");
        int finishRenderWorld = pool.methodRef(bridgeInternalName, "finishRuntimeRenderWorld", "(Ljava/lang/Object;F)V");
        int abortRenderWorld = pool.methodRef(bridgeInternalName, "abortRuntimeRenderWorld", "()V");
        int dispatchMouseOver = pool.methodRef(bridgeInternalName, "dispatchRuntimeMouseOver", "(F)V");
        int beginRuntimeFrame = pool.methodRef(bridgeInternalName, "beginRuntimeFrame", "(F)Ljava/lang/Object;");
        int finishRuntimeFrame = pool.methodRef(bridgeInternalName, "finishRuntimeFrame",
                "(Ljava/lang/Object;Ljava/lang/Object;F)V");
        int abortRuntimeFrame = pool.methodRef(bridgeInternalName, "abortRuntimeFrame", "(Ljava/lang/Object;)V");

        ByteArrayOutputStream bytes = new ByteArrayOutputStream(256);
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeInt(0xCAFEBABE);
        output.writeShort(0);
        // Version 49 avoids a StackMapTable dependency while remaining valid on Java 8.
        output.writeShort(49);
        pool.write(output);
        output.writeShort(ACC_PUBLIC | ACC_FINAL | ACC_SUPER);
        output.writeShort(hookClass);
        output.writeShort(rendererClass);
        output.writeShort(0);
        output.writeShort(0);
        output.writeShort(3);
        writeMethod(output, codeAttribute, ACC_PUBLIC, renderWorldName, renderWorldDescriptor,
                renderWorldCode(beginRenderWorld, superRenderWorld, finishRenderWorld, abortRenderWorld),
                4, 5, throwableClass, 4, 10, 16);
        writeMethod(output, codeAttribute, ACC_PUBLIC, getMouseOverName, getMouseOverDescriptor,
                mouseOverCode(superGetMouseOver, dispatchMouseOver), 2, 2, 0, 0, 0, 0);
        writeMethod(output, codeAttribute, ACC_PUBLIC, updateCameraAndRenderName, updateCameraAndRenderDescriptor,
                updateCameraAndRenderCode(beginRuntimeFrame, superUpdateCameraAndRender,
                        finishRuntimeFrame, abortRuntimeFrame),
                4, 6, throwableClass, 6, 20, 20);
        output.writeShort(0);
        output.flush();
        return bytes.toByteArray();
    }

    private static byte[] renderWorldCode(int beginRenderWorld, int superRenderWorld,
                                           int finishRenderWorld, int abortRenderWorld) {
        ByteArrayOutputStream code = new ByteArrayOutputStream(24);
        code.write(0x23); // fload_1
        writeInvokeStatic(code, beginRenderWorld);
        code.write(0x2A); // aload_0
        code.write(0x23); // fload_1
        code.write(0x20); // lload_2
        writeInvokeSpecial(code, superRenderWorld);
        code.write(0x2A); // aload_0
        code.write(0x23); // fload_1
        writeInvokeStatic(code, finishRenderWorld);
        code.write(0xB1); // return
        code.write(0x3A); // astore 4
        code.write(0x04);
        writeInvokeStatic(code, abortRenderWorld);
        code.write(0x19); // aload 4
        code.write(0x04);
        code.write(0xBF); // athrow
        return code.toByteArray();
    }

    private static byte[] mouseOverCode(int superGetMouseOver, int dispatchMouseOver) {
        ByteArrayOutputStream code = new ByteArrayOutputStream(10);
        code.write(0x2A); // aload_0
        code.write(0x23); // fload_1
        writeInvokeSpecial(code, superGetMouseOver);
        code.write(0x23); // fload_1
        writeInvokeStatic(code, dispatchMouseOver);
        code.write(0xB1); // return
        return code.toByteArray();
    }

    private static byte[] updateCameraAndRenderCode(int beginRuntimeFrame, int superUpdateCameraAndRender,
                                                    int finishRuntimeFrame, int abortRuntimeFrame) {
        ByteArrayOutputStream code = new ByteArrayOutputStream(32);
        code.write(0x23); // fload_1
        writeInvokeStatic(code, beginRuntimeFrame);
        code.write(0x3A); // astore 4
        code.write(0x04);
        code.write(0x2A); // aload_0
        code.write(0x23); // fload_1
        code.write(0x20); // lload_2
        writeInvokeSpecial(code, superUpdateCameraAndRender);
        code.write(0x2A); // aload_0
        code.write(0x19); // aload 4
        code.write(0x04);
        code.write(0x23); // fload_1
        writeInvokeStatic(code, finishRuntimeFrame);
        code.write(0xB1); // return
        code.write(0x3A); // astore 5
        code.write(0x05);
        code.write(0x19); // aload 4
        code.write(0x04);
        writeInvokeStatic(code, abortRuntimeFrame);
        code.write(0x19); // aload 5
        code.write(0x05);
        code.write(0xBF); // athrow
        return code.toByteArray();
    }

    private static void writeMethod(DataOutputStream output, int codeAttribute, int access,
                                    int name, int descriptor, byte[] code, int maxStack, int maxLocals,
                                    int catchType, int tryStart, int tryEnd, int handler) throws IOException {
        output.writeShort(access);
        output.writeShort(name);
        output.writeShort(descriptor);
        output.writeShort(1);
        output.writeShort(codeAttribute);
        int exceptionTableLength = catchType == 0 ? 0 : 1;
        output.writeInt(12 + code.length + exceptionTableLength * 8);
        output.writeShort(maxStack);
        output.writeShort(maxLocals);
        output.writeInt(code.length);
        output.write(code);
        output.writeShort(exceptionTableLength);
        if (exceptionTableLength != 0) {
            output.writeShort(tryStart);
            output.writeShort(tryEnd);
            output.writeShort(handler);
            output.writeShort(catchType);
        }
        output.writeShort(0);
    }

    private static void writeInvokeStatic(ByteArrayOutputStream output, int methodRef) {
        output.write(0xB8);
        writeUnsignedShort(output, methodRef);
    }

    private static void writeInvokeSpecial(ByteArrayOutputStream output, int methodRef) {
        output.write(0xB7);
        writeUnsignedShort(output, methodRef);
    }

    private static void writeUnsignedShort(ByteArrayOutputStream output, int value) {
        output.write((value >>> 8) & 0xFF);
        output.write(value & 0xFF);
    }

    private static final class ConstantPool {
        private final List<Entry> entries = new ArrayList<Entry>();
        private final Map<String, Integer> indexes = new HashMap<String, Integer>();

        private ConstantPool() {
            entries.add(null);
        }

        private int utf8(String value) {
            return add("U:" + value, new Utf8Entry(value));
        }

        private int classInfo(String internalName) {
            return add("C:" + internalName, new ShortEntry(7, utf8(internalName)));
        }

        private int methodRef(String owner, String name, String descriptor) {
            int ownerIndex = classInfo(owner);
            int nameAndType = add("N:" + name + ':' + descriptor,
                    new PairEntry(12, utf8(name), utf8(descriptor)));
            return add("M:" + owner + ':' + name + ':' + descriptor,
                    new PairEntry(10, ownerIndex, nameAndType));
        }

        private int add(String key, Entry entry) {
            Integer existing = indexes.get(key);
            if (existing != null) {
                return existing.intValue();
            }
            int index = entries.size();
            entries.add(entry);
            indexes.put(key, Integer.valueOf(index));
            return index;
        }

        private void write(DataOutputStream output) throws IOException {
            output.writeShort(entries.size());
            for (int index = 1; index < entries.size(); index++) {
                entries.get(index).write(output);
            }
        }
    }

    private interface Entry {
        void write(DataOutputStream output) throws IOException;
    }

    private static final class Utf8Entry implements Entry {
        private final byte[] value;

        private Utf8Entry(String value) {
            try {
                this.value = value.getBytes("UTF-8");
            } catch (java.io.UnsupportedEncodingException exception) {
                throw new IllegalStateException("UTF-8 is unavailable", exception);
            }
        }

        @Override
        public void write(DataOutputStream output) throws IOException {
            output.writeByte(1);
            output.writeShort(value.length);
            output.write(value);
        }
    }

    private static final class ShortEntry implements Entry {
        private final int tag;
        private final int value;

        private ShortEntry(int tag, int value) {
            this.tag = tag;
            this.value = value;
        }

        @Override
        public void write(DataOutputStream output) throws IOException {
            output.writeByte(tag);
            output.writeShort(value);
        }
    }

    private static final class PairEntry implements Entry {
        private final int tag;
        private final int first;
        private final int second;

        private PairEntry(int tag, int first, int second) {
            this.tag = tag;
            this.first = first;
            this.second = second;
        }

        @Override
        public void write(DataOutputStream output) throws IOException {
            output.writeByte(tag);
            output.writeShort(first);
            output.writeShort(second);
        }
    }
}
