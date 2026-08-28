package gq.yozakura.tools;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.commons.RemappingClassAdapter;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

/**
 * Applies only the class/package rename that the test-compatible ZKM build omits.
 * Method and field names are deliberately untouched so Forge, JNI and reflective
 * member contracts remain under the audited ZKM exclusion policy.
 */
public final class ConstrainedClassRenamer {
    private static final String TARGET_PACKAGE = "n/";
    private static final Set<String> STABLE_CLASSES = new HashSet<String>(Arrays.asList(
            "gq/yozakura/k/A",
            "gq/yozakura/k/B",
            "gq/yozakura/k/vendor/tech/skidonion/obfuscator/inline/C",
            "gq/yozakura/k/vendor/tech/skidonion/obfuscator/inline/Inline",
            "gq/yozakura/module/Module"
    ));

    private ConstrainedClassRenamer() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            throw new IllegalArgumentException(
                    "Usage: ConstrainedClassRenamer <input.jar> <output.jar> <mapping.txt>");
        }
        File input = new File(args[0]);
        File output = new File(args[1]);
        File mapping = new File(args[2]);
        if (!input.isFile()) {
            throw new IOException("Input JAR was not found: " + input);
        }
        if (sameFile(input, output)) {
            throw new IOException("Input and output JARs must be different files");
        }

        Map<String, byte[]> entries = readEntries(input);
        Set<String> classNames = classNames(entries);
        Map<String, String> mappings = buildMappings(classNames);
        if (mappings.size() < 50) {
            throw new IOException("Constrained rename surface is unexpectedly small: " + mappings.size());
        }
        rejectReflectiveClassNameStrings(entries, mappings);
        validateMappings(classNames, mappings);

        File outputParent = output.getAbsoluteFile().getParentFile();
        File mappingParent = mapping.getAbsoluteFile().getParentFile();
        if (outputParent != null) {
            Files.createDirectories(outputParent.toPath());
        }
        if (mappingParent != null) {
            Files.createDirectories(mappingParent.toPath());
        }

        File temporaryOutput = new File(output.getAbsolutePath() + ".tmp");
        writeRemappedJar(entries, mappings, temporaryOutput);
        Files.move(temporaryOutput.toPath(), output.toPath(),
                StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        writeMapping(mapping, mappings);
        System.out.println("Constrained class renaming complete: " + mappings.size() + " classes -> n/**");
    }

    private static Map<String, byte[]> readEntries(File input) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<String, byte[]>();
        JarFile jar = new JarFile(input, false);
        try {
            java.util.Enumeration<JarEntry> enumeration = jar.entries();
            while (enumeration.hasMoreElements()) {
                JarEntry entry = enumeration.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                if (entries.containsKey(entry.getName())) {
                    throw new IOException("Duplicate input JAR entry: " + entry.getName());
                }
                InputStream stream = jar.getInputStream(entry);
                try {
                    entries.put(entry.getName(), readFully(stream));
                } finally {
                    stream.close();
                }
            }
        } finally {
            jar.close();
        }
        return entries;
    }

    private static Set<String> classNames(Map<String, byte[]> entries) {
        Set<String> names = new HashSet<String>();
        for (String entry : entries.keySet()) {
            if (entry.endsWith(".class")) {
                names.add(entry.substring(0, entry.length() - ".class".length()));
            }
        }
        return names;
    }

    private static Map<String, String> buildMappings(Set<String> classNames) {
        List<String> eligible = new ArrayList<String>();
        for (String internalName : classNames) {
            if (isEligible(internalName)) {
                eligible.add(internalName);
            }
        }
        Collections.sort(eligible);

        Map<String, String> familyTargets = new HashMap<String, String>();
        Map<String, String> mappings = new LinkedHashMap<String, String>();
        int nextName = 0;
        for (String internalName : eligible) {
            if (internalName.equals("gq/yozakura/module/Module$BindMode")) {
                mappings.put(internalName, "gq/yozakura/module/Module$a");
                continue;
            }
            String outer = outerInternalName(internalName);
            if (isEligible(outer) && classNames.contains(outer)) {
                String targetOuter = familyTargets.get(outer);
                if (targetOuter == null) {
                    targetOuter = TARGET_PACKAGE + shortName(nextName++);
                    familyTargets.put(outer, targetOuter);
                }
                String suffix = internalName.substring(outer.length());
                mappings.put(internalName, targetOuter + suffix);
            } else {
                mappings.put(internalName, TARGET_PACKAGE + shortName(nextName++));
            }
        }
        return mappings;
    }

    private static boolean isEligible(String internalName) {
        if (!(internalName.startsWith("gq/yozakura/k/")
                || internalName.startsWith("gq/yozakura/module/"))) {
            return false;
        }
        for (String stable : STABLE_CLASSES) {
            if (internalName.equals(stable) || internalName.startsWith(stable + "$")) {
                return internalName.equals("gq/yozakura/module/Module$BindMode");
            }
        }
        return true;
    }

    private static String outerInternalName(String internalName) {
        int separator = internalName.indexOf('$');
        return separator < 0 ? internalName : internalName.substring(0, separator);
    }

    private static String shortName(int value) {
        String alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        StringBuilder name = new StringBuilder();
        int current = value;
        do {
            name.append(alphabet.charAt(current % alphabet.length()));
            current = current / alphabet.length() - 1;
        } while (current >= 0);
        return name.reverse().toString();
    }

    private static void rejectReflectiveClassNameStrings(Map<String, byte[]> entries,
                                                          final Map<String, String> mappings) {
        final Set<String> forbidden = new HashSet<String>();
        for (String source : mappings.keySet()) {
            forbidden.add(source);
            forbidden.add(source.replace('/', '.'));
        }
        for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
            if (!entry.getKey().endsWith(".class")) {
                continue;
            }
            final String owner = entry.getKey();
            ClassReader reader = new ClassReader(entry.getValue());
            reader.accept(new ClassVisitor(Opcodes.ASM5) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    return new MethodVisitor(Opcodes.ASM5) {
                        @Override
                        public void visitLdcInsn(Object value) {
                            if (value instanceof String && forbidden.contains(value)) {
                                throw new IllegalStateException(
                                        "Reflective class-name string blocks constrained rename in "
                                                + owner + ": " + value);
                            }
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
    }

    private static void validateMappings(Set<String> classNames, Map<String, String> mappings)
            throws IOException {
        Set<String> targets = new HashSet<String>();
        for (Map.Entry<String, String> mapping : mappings.entrySet()) {
            String target = mapping.getValue();
            boolean stableCompanion = mapping.getKey().equals("gq/yozakura/module/Module$BindMode")
                    && target.equals("gq/yozakura/module/Module$a");
            if (!target.startsWith(TARGET_PACKAGE) && !stableCompanion) {
                throw new IOException("Rename escaped its audited namespace: " + target);
            }
            if (!targets.add(target)) {
                throw new IOException("Duplicate class rename target: " + target);
            }
            if (classNames.contains(target) && !mappings.containsKey(target)) {
                throw new IOException("Rename collides with an existing class: " + target);
            }
        }
    }

    private static void writeRemappedJar(Map<String, byte[]> entries, Map<String, String> mappings,
                                         File output) throws IOException {
        final MappingRemapper remapper = new MappingRemapper(mappings);
        Set<String> written = new HashSet<String>();
        JarOutputStream jar = new JarOutputStream(new FileOutputStream(output));
        try {
            for (Map.Entry<String, byte[]> source : entries.entrySet()) {
                String sourceName = source.getKey();
                String destinationName = sourceName;
                byte[] data = source.getValue();
                if (sourceName.endsWith(".class")) {
                    ClassReader reader = new ClassReader(data);
                    String mappedName = remapper.map(reader.getClassName());
                    destinationName = mappedName + ".class";
                    ClassWriter writer = new ClassWriter(0);
                    ClassVisitor visitor = new RemappingClassAdapter(writer, remapper);
                    reader.accept(visitor, ClassReader.EXPAND_FRAMES);
                    data = writer.toByteArray();
                } else if (isSignatureFile(sourceName)) {
                    continue;
                }
                if (!written.add(destinationName)) {
                    throw new IOException("Duplicate output JAR entry: " + destinationName);
                }
                JarEntry destination = new JarEntry(destinationName);
                destination.setTime(0L);
                jar.putNextEntry(destination);
                jar.write(data);
                jar.closeEntry();
            }
        } finally {
            jar.close();
        }
    }

    private static boolean isSignatureFile(String name) {
        String upper = name.toUpperCase(java.util.Locale.ROOT);
        return upper.startsWith("META-INF/")
                && (upper.endsWith(".SF") || upper.endsWith(".RSA") || upper.endsWith(".DSA"));
    }

    private static void writeMapping(File output, Map<String, String> mappings) throws IOException {
        List<String> lines = new ArrayList<String>();
        lines.add("# Constrained post-ZKM class mapping.");
        lines.add("# Method and field names are unchanged.");
        for (Map.Entry<String, String> mapping : mappings.entrySet()) {
            lines.add(mapping.getKey() + ".class -> " + mapping.getValue() + ".class");
        }
        Files.write(output.toPath(), lines, StandardCharsets.UTF_8);
    }

    private static boolean sameFile(File first, File second) throws IOException {
        return first.getCanonicalFile().equals(second.getCanonicalFile());
    }

    private static byte[] readFully(InputStream stream) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = stream.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static final class MappingRemapper extends Remapper {
        private final Map<String, String> mappings;

        private MappingRemapper(Map<String, String> mappings) {
            this.mappings = mappings;
        }

        @Override
        public String map(String internalName) {
            String mapped = mappings.get(internalName);
            return mapped == null ? internalName : mapped;
        }
    }
}
