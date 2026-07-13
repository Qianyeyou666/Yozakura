package gq.yozakura.bridge;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

public final class VanillaRemapClassLoader extends URLClassLoader {
    private static final String MAPPINGS = "/yozakura/notch-srg.srg";
    private static final String SRG_MCP_MAPPINGS = "/yozakura/srg-mcp.srg";
    private final Remapper remapper;
    private final Map<Class<?>, Class<?>> runtimeEntityRendererHooks = new IdentityHashMap<Class<?>, Class<?>>();
    private int runtimeEntityRendererHookSequence;

    public VanillaRemapClassLoader(URL[] urls, ClassLoader parent) throws IOException {
        this(urls, parent, false);
    }

    public VanillaRemapClassLoader(URL[] urls, ClassLoader parent, boolean keepMinecraftClassNames) throws IOException {
        super(urls, parent);
        this.remapper = Remapper.load(keepMinecraftClassNames);
    }

    /**
     * Defines one subclass of the live client renderer in this loader. The
     * generated methods use invokespecial so the client renderer's own override
     * remains in the call path rather than being replaced by a vanilla wrapper.
     */
    public synchronized Class<?> defineRuntimeEntityRendererHook(Class<?> runtimeRendererType) {
        if (runtimeRendererType == null) {
            throw new IllegalArgumentException("Cannot generate an EntityRenderer hook for null");
        }
        Class<?> cached = runtimeEntityRendererHooks.get(runtimeRendererType);
        if (cached != null) {
            return cached;
        }
        validateRuntimeEntityRendererType(runtimeRendererType);
        String generatedName = "gq.yozakura.bridge.generated.RuntimeEntityRendererHook$"
                + (++runtimeEntityRendererHookSequence);
        try {
            byte[] bytes = RuntimeEntityRendererHookGenerator.generate(generatedName, runtimeRendererType);
            Class<?> generated = defineClass(generatedName, bytes, 0, bytes.length);
            runtimeEntityRendererHooks.put(runtimeRendererType, generated);
            return generated;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to generate runtime EntityRenderer hook for "
                    + runtimeRendererType.getName(), exception);
        } catch (LinkageError error) {
            throw new IllegalStateException("Unable to define runtime EntityRenderer hook for "
                    + runtimeRendererType.getName(), error);
        }
    }

    private void validateRuntimeEntityRendererType(Class<?> runtimeRendererType) {
        int modifiers = runtimeRendererType.getModifiers();
        if (runtimeRendererType.isInterface() || runtimeRendererType.isArray()
                || runtimeRendererType.isPrimitive() || Modifier.isFinal(modifiers)) {
            throw new IllegalArgumentException("Runtime EntityRenderer type is not subclassable: "
                    + runtimeRendererType.getName());
        }
        if (!Modifier.isPublic(modifiers)) {
            throw new IllegalArgumentException("Runtime EntityRenderer type is not public: "
                    + runtimeRendererType.getName());
        }
        try {
            Class<?> visibleType = Class.forName(runtimeRendererType.getName(), false, this);
            if (visibleType != runtimeRendererType) {
                throw new IllegalArgumentException("Runtime EntityRenderer type is not visible from the remap loader: "
                        + runtimeRendererType.getName());
            }
        } catch (ClassNotFoundException exception) {
            throw new IllegalArgumentException("Runtime EntityRenderer type is not visible from the remap loader: "
                    + runtimeRendererType.getName(), exception);
        }
        validateOverridableMethod(runtimeRendererType, "renderWorld", Float.TYPE, Long.TYPE);
        validateOverridableMethod(runtimeRendererType, "getMouseOver", Float.TYPE);
    }

    private static void validateOverridableMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        try {
            Method method = type.getMethod(name, parameterTypes);
            int modifiers = method.getModifiers();
            if (!Modifier.isPublic(modifiers) || Modifier.isStatic(modifiers) || Modifier.isFinal(modifiers)) {
                throw new IllegalArgumentException("Runtime EntityRenderer method is not overridable: "
                        + type.getName() + '.' + name);
            }
        } catch (NoSuchMethodException exception) {
            throw new IllegalArgumentException("Runtime EntityRenderer method is unavailable: "
                    + type.getName() + '.' + name, exception);
        }
    }

    @Override
    protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        Class<?> loaded = findLoadedClass(name);
        if (loaded == null && shouldLoadChildFirst(name)) {
            try {
                loaded = findClass(name);
            } catch (ClassNotFoundException ignored) {
            }
        }
        if (loaded == null) {
            loaded = super.loadClass(name, false);
        }
        if (resolve) {
            resolveClass(loaded);
        }
        return loaded;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        String path = name.replace('.', '/') + ".class";
        URL resource = findResource(path);
        if (resource == null) {
            throw new ClassNotFoundException(name);
        }
        try {
            byte[] bytes = readAll(resource.openStream());
            bytes = remapper.remapClass(bytes);
            return defineClass(name, bytes, 0, bytes.length);
        } catch (IOException exception) {
            throw new ClassNotFoundException(name, exception);
        }
    }

    private static boolean shouldLoadChildFirst(String name) {
        return name.startsWith("gq.yozakura.")
                && !name.equals("gq.yozakura.YozakuraBootstrap")
                && !name.startsWith("gq.yozakura.bridge.VanillaRemapClassLoader");
    }

    private static byte[] readAll(InputStream input) throws IOException {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        } finally {
            input.close();
        }
    }

    private static final class Remapper {
        private static final Map<String, String> FORGE_EVENT_SHIMS = createForgeEventShims();
        private final Map<String, String> classes = new HashMap<String, String>();
        private final Map<String, String> fields = new HashMap<String, String>();
        private final Map<String, String> fieldsByName = new HashMap<String, String>();
        private final Map<String, String> fieldsToNamed = new HashMap<String, String>();
        private final Map<String, String> fieldsToNamedByName = new HashMap<String, String>();
        private final Map<String, String> methods = new HashMap<String, String>();
        private final Map<String, String> methodsByNameDesc = new HashMap<String, String>();
        private final Map<String, String> methodsToNamed = new HashMap<String, String>();
        private final Map<String, String> methodsToNamedByNameDesc = new HashMap<String, String>();
        private final Set<String> conflictingFields = new HashSet<String>();
        private final Set<String> conflictingMethods = new HashSet<String>();
        private final Set<String> conflictingNamedFields = new HashSet<String>();
        private final Set<String> conflictingNamedMethods = new HashSet<String>();
        private final boolean keepMinecraftClassNames;

        private Remapper(boolean keepMinecraftClassNames) {
            this.keepMinecraftClassNames = keepMinecraftClassNames;
        }

        private static Remapper load(boolean keepMinecraftClassNames) throws IOException {
            Remapper remapper = new Remapper(keepMinecraftClassNames);
            InputStream input = VanillaRemapClassLoader.class.getResourceAsStream(MAPPINGS);
            if (input == null) {
                throw new IOException("Missing mapping resource: " + MAPPINGS);
            }
            DataInputStream data = new DataInputStream(input);
            try {
                while (true) {
                    String line;
                    try {
                        line = data.readLine();
                    } catch (IOException exception) {
                        throw exception;
                    }
                    if (line == null) {
                        break;
                    }
                    remapper.parseLine(line.trim());
                }
            } finally {
                data.close();
            }
            for (String key : remapper.conflictingMethods) {
                remapper.methodsByNameDesc.remove(key);
            }
            for (String key : remapper.conflictingFields) {
                remapper.fieldsByName.remove(key);
            }
            if (keepMinecraftClassNames) {
                remapper.loadSrgToMcp();
                for (String key : remapper.conflictingNamedFields) {
                    remapper.fieldsToNamedByName.remove(key);
                }
                for (String key : remapper.conflictingNamedMethods) {
                    remapper.methodsToNamedByNameDesc.remove(key);
                }
            }
            return remapper;
        }

        private void parseLine(String line) {
            if (line.length() == 0 || line.startsWith("#")) {
                return;
            }
            String[] parts = line.split(" ");
            if (parts.length >= 3 && "CL:".equals(parts[0])) {
                classes.put(parts[2], parts[1]);
                return;
            }
            if (parts.length >= 3 && "FD:".equals(parts[0])) {
                String obfOwnerName = parts[1];
                String srgOwnerName = parts[2];
                String obfName = simpleName(obfOwnerName);
                String srgName = simpleName(srgOwnerName);
                fields.put(srgOwnerName, obfName);
                String previous = fieldsByName.put(srgName, obfName);
                if (previous != null && !previous.equals(obfName)) {
                    conflictingFields.add(srgName);
                }
                return;
            }
            if (parts.length >= 5 && "MD:".equals(parts[0])) {
                String obfOwnerName = parts[1];
                String obfDescriptor = parts[2];
                String srgOwnerName = parts[3];
                String srgDescriptor = parts[4];
                String obfName = simpleName(obfOwnerName);
                methods.put(srgOwnerName + " " + srgDescriptor, obfName);
                String nameDesc = simpleName(srgOwnerName) + " " + srgDescriptor;
                String previous = methodsByNameDesc.put(nameDesc, obfName);
                if (previous != null && !previous.equals(obfName)) {
                    conflictingMethods.add(nameDesc);
                }
                if (obfDescriptor != null) {
                    // Keeps the local variable used; parsing validates both descriptors exist.
                }
            }
        }

        private void loadSrgToMcp() throws IOException {
            InputStream input = VanillaRemapClassLoader.class.getResourceAsStream(SRG_MCP_MAPPINGS);
            if (input == null) {
                return;
            }
            DataInputStream data = new DataInputStream(input);
            try {
                while (true) {
                    String line = data.readLine();
                    if (line == null) {
                        break;
                    }
                    parseSrgToMcpLine(line.trim());
                }
            } finally {
                data.close();
            }
        }

        private void parseSrgToMcpLine(String line) {
            if (line.length() == 0 || line.startsWith("#")) {
                return;
            }
            String[] parts = line.split(" ");
            if (parts.length >= 3 && "FD:".equals(parts[0])) {
                String namedName = simpleName(parts[2]);
                fieldsToNamed.put(parts[1], namedName);
                putFallback(fieldsToNamedByName, conflictingNamedFields, simpleName(parts[1]), namedName);
                return;
            }
            if (parts.length >= 5 && "MD:".equals(parts[0])) {
                String namedName = simpleName(parts[3]);
                methodsToNamed.put(parts[1] + " " + parts[2], namedName);
                putFallback(methodsToNamedByNameDesc, conflictingNamedMethods, simpleName(parts[1]) + " " + parts[2], namedName);
            }
        }

        private byte[] remapClass(byte[] bytes) throws IOException {
            ConstantPool pool = ConstantPool.read(bytes);
            Map<Integer, String> replacements = new HashMap<Integer, String>();

            for (int i = 1; i < pool.entries.length; i++) {
                CpInfo entry = pool.entries[i];
                if (entry != null && entry.tag == 1) {
                    String text = entry.utf8;
                    String mapped = remapText(text);
                    if (!text.equals(mapped)) {
                        replacements.put(Integer.valueOf(i), mapped);
                    }
                }
            }

            for (int i = 1; i < pool.entries.length; i++) {
                CpInfo entry = pool.entries[i];
                if (entry == null || (entry.tag != 9 && entry.tag != 10 && entry.tag != 11)) {
                    continue;
                }
                String owner = pool.className(entry.classIndex);
                CpInfo nameAndType = pool.entries[entry.nameAndTypeIndex];
                String name = pool.utf8(nameAndType.nameIndex);
                String descriptor = pool.utf8(nameAndType.descriptorIndex);
                String mappedName = entry.tag == 9
                        ? mapFieldName(owner, name)
                        : mapMethodName(owner, name, descriptor);
                if (mappedName != null && !mappedName.equals(name)) {
                    replacements.put(Integer.valueOf(nameAndType.nameIndex), mappedName);
                }
            }
            remapMemberDefinitions(bytes, pool, replacements);

            return pool.write(bytes, replacements);
        }

        private void remapMemberDefinitions(byte[] bytes, ConstantPool pool, Map<Integer, String> replacements)
                throws IOException {
            int offset = pool.end;
            offset += 2;
            String owner = pool.className(readUnsignedShort(bytes, offset));
            offset += 2;
            offset += 2;

            int interfaces = readUnsignedShort(bytes, offset);
            offset += 2 + interfaces * 2;

            int fields = readUnsignedShort(bytes, offset);
            offset += 2;
            for (int i = 0; i < fields; i++) {
                offset = remapMemberDefinition(bytes, pool, replacements, offset, owner, true);
            }

            int methods = readUnsignedShort(bytes, offset);
            offset += 2;
            for (int i = 0; i < methods; i++) {
                offset = remapMemberDefinition(bytes, pool, replacements, offset, owner, false);
            }
        }

        private int remapMemberDefinition(byte[] bytes, ConstantPool pool, Map<Integer, String> replacements,
                                          int offset, String owner, boolean field) throws IOException {
            offset += 2;
            int nameIndex = readUnsignedShort(bytes, offset);
            offset += 2;
            int descriptorIndex = readUnsignedShort(bytes, offset);
            offset += 2;

            String name = pool.utf8(nameIndex);
            String descriptor = pool.utf8(descriptorIndex);
            String mappedName = field ? mapFieldName(owner, name) : mapMethodName(owner, name, descriptor);
            if (mappedName != null && !mappedName.equals(name)) {
                replacements.put(Integer.valueOf(nameIndex), mappedName);
            }

            int attributes = readUnsignedShort(bytes, offset);
            offset += 2;
            for (int i = 0; i < attributes; i++) {
                offset += 2;
                int length = readInt(bytes, offset);
                offset += 4 + length;
                if (offset > bytes.length) {
                    throw new IOException("Invalid class member attributes");
                }
            }
            return offset;
        }

        private String mapFieldName(String owner, String name) {
            if (isForgeEventShimOwner(owner)) {
                return null;
            }
            if (keepMinecraftClassNames) {
                String exact = fieldsToNamed.get(owner + "/" + name);
                if (exact != null) {
                    return exact;
                }
                if (isSrgFieldName(name) && !conflictingNamedFields.contains(name)) {
                    return fieldsToNamedByName.get(name);
                }
                return null;
            }
            if (!isMappedMinecraftOwner(owner) && !isSrgFieldName(name)) {
                return null;
            }
            if (isMappedMinecraftOwner(owner)) {
                String exact = fields.get(owner + "/" + name);
                if (exact != null) {
                    return exact;
                }
            }
            if (!conflictingFields.contains(name)) {
                return fieldsByName.get(name);
            }
            return null;
        }

        private String mapMethodName(String owner, String name, String descriptor) {
            if (isForgeEventShimOwner(owner)) {
                return null;
            }
            if (keepMinecraftClassNames) {
                String exact = methodsToNamed.get(owner + "/" + name + " " + descriptor);
                if (exact != null) {
                    return exact;
                }
                String nameDesc = name + " " + descriptor;
                if (isSrgMethodName(name) && !conflictingNamedMethods.contains(nameDesc)) {
                    return methodsToNamedByNameDesc.get(nameDesc);
                }
                return null;
            }
            if (!isMappedMinecraftOwner(owner) && !isSrgMethodName(name)) {
                return null;
            }
            if (isMappedMinecraftOwner(owner)) {
                String exact = methods.get(owner + "/" + name + " " + descriptor);
                if (exact != null) {
                    return exact;
                }
            }
            String nameDesc = name + " " + descriptor;
            if (!conflictingMethods.contains(nameDesc)) {
                return methodsByNameDesc.get(nameDesc);
            }
            return null;
        }

        private static boolean isSrgFieldName(String name) {
            return name != null && name.startsWith("field_");
        }

        private static boolean isSrgMethodName(String name) {
            return name != null && name.startsWith("func_");
        }

        private static boolean isMappedMinecraftOwner(String owner) {
            return owner != null
                    && (owner.startsWith("net/minecraft/")
                    || owner.startsWith("net/minecraftforge/")
                    || owner.startsWith("cpw/mods/fml/"));
        }

        private static boolean isForgeEventShimOwner(String owner) {
            return owner != null
                    && (FORGE_EVENT_SHIMS.containsKey(owner)
                    || owner.startsWith("gq/yozakura/bridge/forge/"));
        }

        private static void putFallback(Map<String, String> map, Set<String> conflicts, String key, String value) {
            String previous = map.put(key, value);
            if (previous != null && !previous.equals(value)) {
                conflicts.add(key);
            }
        }

        private String remapText(String text) {
            if (text == null || text.length() == 0) {
                return text;
            }
            text = remapForgeEventShims(text);
            if (keepMinecraftClassNames) {
                return text;
            }
            String direct = classes.get(text);
            if (direct != null) {
                return direct;
            }
            return remapClassTokens(text);
        }

        private static Map<String, String> createForgeEventShims() {
            Map<String, String> map = new HashMap<String, String>();
            putShim(map, "net/minecraftforge/fml/common/eventhandler/SubscribeEvent", "SubscribeEvent");
            putShim(map, "net/minecraftforge/fml/common/eventhandler/EventPriority", "EventPriority");
            putShim(map, "net/minecraftforge/fml/common/eventhandler/Event", "Event");
            putShim(map, "net/minecraftforge/fml/common/gameevent/TickEvent", "TickEvent");
            putShim(map, "net/minecraftforge/fml/common/gameevent/TickEvent$Phase", "TickEvent$Phase");
            putShim(map, "net/minecraftforge/fml/common/gameevent/TickEvent$ClientTickEvent", "TickEvent$ClientTickEvent");
            putShim(map, "net/minecraftforge/fml/common/gameevent/TickEvent$PlayerTickEvent", "TickEvent$PlayerTickEvent");
            putShim(map, "net/minecraftforge/fml/common/gameevent/TickEvent$RenderTickEvent", "TickEvent$RenderTickEvent");
            putShim(map, "net/minecraftforge/fml/common/gameevent/InputEvent", "InputEvent");
            putShim(map, "net/minecraftforge/fml/common/gameevent/InputEvent$KeyInputEvent", "InputEvent$KeyInputEvent");
            putShim(map, "net/minecraftforge/fml/common/network/FMLNetworkEvent", "FMLNetworkEvent");
            putShim(map, "net/minecraftforge/fml/common/network/FMLNetworkEvent$ClientDisconnectionFromServerEvent",
                    "FMLNetworkEvent$ClientDisconnectionFromServerEvent");
            putShim(map, "net/minecraftforge/client/event/MouseEvent", "MouseEvent");
            putShim(map, "net/minecraftforge/client/event/RenderGameOverlayEvent", "RenderGameOverlayEvent");
            putShim(map, "net/minecraftforge/client/event/RenderGameOverlayEvent$Text", "RenderGameOverlayEvent$Text");
            putShim(map, "net/minecraftforge/client/event/RenderWorldLastEvent", "RenderWorldLastEvent");
            putShim(map, "net/minecraftforge/client/event/RenderLivingEvent", "RenderLivingEvent");
            putShim(map, "net/minecraftforge/client/event/RenderLivingEvent$Pre", "RenderLivingEvent$Pre");
            putShim(map, "net/minecraftforge/client/event/RenderLivingEvent$Post", "RenderLivingEvent$Post");
            putShim(map, "net/minecraftforge/client/event/RenderLivingEvent$Specials", "RenderLivingEvent$Specials");
            putShim(map, "net/minecraftforge/client/event/RenderLivingEvent$Specials$Pre", "RenderLivingEvent$Specials$Pre");
            putShim(map, "net/minecraftforge/client/event/RenderLivingEvent$Specials$Post", "RenderLivingEvent$Specials$Post");
            putShim(map, "net/minecraftforge/client/event/RenderPlayerEvent", "RenderPlayerEvent");
            putShim(map, "net/minecraftforge/client/event/RenderPlayerEvent$Pre", "RenderPlayerEvent$Pre");
            putShim(map, "net/minecraftforge/client/event/RenderPlayerEvent$Post", "RenderPlayerEvent$Post");
            putShim(map, "net/minecraftforge/event/entity/living/LivingEvent", "LivingEvent");
            putShim(map, "net/minecraftforge/event/entity/living/LivingEvent$LivingUpdateEvent",
                    "LivingEvent$LivingUpdateEvent");
            putShim(map, "net/minecraftforge/event/entity/player/AttackEntityEvent", "AttackEntityEvent");
            return map;
        }

        private static void putShim(Map<String, String> map, String forgeName, String shimName) {
            map.put(forgeName, "gq/yozakura/bridge/forge/" + shimName);
        }

        private static String remapForgeEventShims(String text) {
            if (text.indexOf("net/minecraftforge/") < 0) {
                return text;
            }
            StringBuilder result = new StringBuilder(text.length());
            int index = 0;
            boolean changed = false;
            while (index < text.length()) {
                int start = text.indexOf("net/minecraftforge/", index);
                if (start < 0) {
                    result.append(text, index, text.length());
                    break;
                }
                result.append(text, index, start);
                int end = start;
                while (end < text.length() && isInternalNameChar(text.charAt(end))) {
                    end++;
                }
                String owner = text.substring(start, end);
                String mapped = FORGE_EVENT_SHIMS.get(owner);
                if (mapped != null) {
                    result.append(mapped);
                    changed = true;
                } else {
                    result.append(owner);
                }
                index = end;
            }
            return changed ? result.toString() : text;
        }

        private String remapClassTokens(String text) {
            if (text.indexOf("net/minecraft/") < 0
                    && text.indexOf("net/minecraftforge/") < 0
                    && text.indexOf("cpw/mods/fml/") < 0) {
                return text;
            }

            StringBuilder result = new StringBuilder(text.length());
            int index = 0;
            boolean changed = false;
            while (index < text.length()) {
                int start = findNextClassToken(text, index);
                if (start < 0) {
                    result.append(text, index, text.length());
                    break;
                }
                result.append(text, index, start);
                int end = start;
                while (end < text.length() && isInternalNameChar(text.charAt(end))) {
                    end++;
                }
                String owner = text.substring(start, end);
                String mapped = classes.get(owner);
                if (mapped != null) {
                    result.append(mapped);
                    changed = true;
                } else {
                    result.append(owner);
                }
                index = end;
            }
            return changed ? result.toString() : text;
        }

        private static int findNextClassToken(String text, int fromIndex) {
            int best = -1;
            int minecraft = text.indexOf("net/minecraft/", fromIndex);
            if (minecraft >= 0) {
                best = minecraft;
            }
            int forge = text.indexOf("net/minecraftforge/", fromIndex);
            if (forge >= 0 && (best < 0 || forge < best)) {
                best = forge;
            }
            int fml = text.indexOf("cpw/mods/fml/", fromIndex);
            if (fml >= 0 && (best < 0 || fml < best)) {
                best = fml;
            }
            return best;
        }

        private static boolean isInternalNameChar(char value) {
            return (value >= 'a' && value <= 'z')
                    || (value >= 'A' && value <= 'Z')
                    || (value >= '0' && value <= '9')
                    || value == '_'
                    || value == '$'
                    || value == '/';
        }

        private static String simpleName(String ownerName) {
            int slash = ownerName.lastIndexOf('/');
            return slash >= 0 ? ownerName.substring(slash + 1) : ownerName;
        }
    }

    private static final class ConstantPool {
        private final CpInfo[] entries;
        private final int start;
        private final int end;

        private ConstantPool(CpInfo[] entries, int start, int end) {
            this.entries = entries;
            this.start = start;
            this.end = end;
        }

        private static ConstantPool read(byte[] bytes) throws IOException {
            if (readInt(bytes, 0) != 0xCAFEBABE) {
                throw new IOException("Invalid class file");
            }
            int count = readUnsignedShort(bytes, 8);
            CpInfo[] entries = new CpInfo[count];
            int offset = 10;
            for (int i = 1; i < count; i++) {
                CpInfo entry = new CpInfo();
                entry.tag = bytes[offset++] & 0xFF;
                switch (entry.tag) {
                    case 1:
                        int length = readUnsignedShort(bytes, offset);
                        offset += 2;
                        entry.utf8 = new String(bytes, offset, length, "UTF-8");
                        entry.raw = copy(bytes, offset, length);
                        offset += length;
                        break;
                    case 3:
                    case 4:
                        entry.raw = copy(bytes, offset, 4);
                        offset += 4;
                        break;
                    case 5:
                    case 6:
                        entry.raw = copy(bytes, offset, 8);
                        offset += 8;
                        entries[i] = entry;
                        i++;
                        continue;
                    case 7:
                    case 8:
                    case 16:
                        entry.raw = copy(bytes, offset, 2);
                        if (entry.tag == 7) {
                            entry.nameIndex = readUnsignedShort(bytes, offset);
                        }
                        offset += 2;
                        break;
                    case 9:
                    case 10:
                    case 11:
                        entry.classIndex = readUnsignedShort(bytes, offset);
                        entry.nameAndTypeIndex = readUnsignedShort(bytes, offset + 2);
                        entry.raw = copy(bytes, offset, 4);
                        offset += 4;
                        break;
                    case 12:
                        entry.nameIndex = readUnsignedShort(bytes, offset);
                        entry.descriptorIndex = readUnsignedShort(bytes, offset + 2);
                        entry.raw = copy(bytes, offset, 4);
                        offset += 4;
                        break;
                    case 15:
                        entry.raw = copy(bytes, offset, 3);
                        offset += 3;
                        break;
                    case 18:
                        entry.raw = copy(bytes, offset, 4);
                        offset += 4;
                        break;
                    default:
                        throw new IOException("Unsupported constant pool tag: " + entry.tag);
                }
                entries[i] = entry;
            }
            return new ConstantPool(entries, 10, offset);
        }

        private String utf8(int index) {
            CpInfo entry = entries[index];
            return entry == null ? null : entry.utf8;
        }

        private String className(int index) {
            CpInfo entry = entries[index];
            return entry == null ? null : utf8(entry.nameIndex);
        }

        private byte[] write(byte[] original, Map<Integer, String> replacements) throws IOException {
            if (replacements.isEmpty()) {
                return original;
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream(original.length + 256);
            output.write(original, 0, start);
            for (int i = 1; i < entries.length; i++) {
                CpInfo entry = entries[i];
                if (entry == null) {
                    continue;
                }
                output.write(entry.tag);
                if (entry.tag == 1) {
                    String replacement = replacements.get(Integer.valueOf(i));
                    byte[] data = replacement == null ? entry.raw : replacement.getBytes("UTF-8");
                    writeShort(output, data.length);
                    output.write(data);
                } else {
                    output.write(entry.raw);
                }
                if (entry.tag == 5 || entry.tag == 6) {
                    i++;
                }
            }
            output.write(original, end, original.length - end);
            return output.toByteArray();
        }
    }

    private static final class CpInfo {
        private int tag;
        private byte[] raw;
        private String utf8;
        private int classIndex;
        private int nameAndTypeIndex;
        private int nameIndex;
        private int descriptorIndex;
    }

    private static byte[] copy(byte[] source, int offset, int length) {
        byte[] out = new byte[length];
        System.arraycopy(source, offset, out, 0, length);
        return out;
    }

    private static int readUnsignedShort(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 8) | (bytes[offset + 1] & 0xFF);
    }

    private static int readInt(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 24)
                | ((bytes[offset + 1] & 0xFF) << 16)
                | ((bytes[offset + 2] & 0xFF) << 8)
                | (bytes[offset + 3] & 0xFF);
    }

    private static void writeShort(ByteArrayOutputStream output, int value) {
        output.write((value >>> 8) & 0xFF);
        output.write(value & 0xFF);
    }
}
