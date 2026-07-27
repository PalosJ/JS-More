package com.palos.jsmore.compat.jurassicsaga;

import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Proves the original Jurassic Saga breeding and periodic item-egg bytecode
 * before the matching Mixins are allowed to transform it.
 */
public final class JurassicSagaBreedingCompatibilityGate {
    public static final String ANIMAL_BASE_INTERNAL_NAME =
            "jp/jurassicsaga/server/animal/entity/obj/bases/JSAnimalBase";
    public static final String DATA_HOLDER_INTERNAL_NAME =
            "jp/jurassicsaga/server/animal/entity/obj/bases/JSEntityDataHolder";
    public static final String OSTRICH_INTERNAL_NAME =
            "jp/jurassicsaga/server/animal/entity/misc/misc_extant/OstrichEntity";
    public static final String ALLIGATOR_INTERNAL_NAME =
            "jp/jurassicsaga/server/animal/entity/misc/misc_extant/AlligatorEntity";
    public static final String REED_FROG_INTERNAL_NAME =
            "jp/jurassicsaga/server/animal/entity/misc/misc_extant/ReedFrogEntity";
    public static final String BASILISK_INTERNAL_NAME =
            "jp/jurassicsaga/server/animal/entity/misc/misc_extant/BasiliskEntity";

    private static final String ENTITY_PREFIX =
            "jp/jurassicsaga/server/animal/entity/";
    private static final String ANIMAL_DEFINITION_PREFIX =
            "jp/jurassicsaga/server/animal/animals/";
    private static final String ANIMAL_ROOT =
            "jp/jurassicsaga/server/animal/";
    private static final String ENTITY_ATTRIBUTE_PROPERTIES_INTERNAL_NAME =
            "collinvht/travelers/server/animal/obj/attributes/EntityAttributeProperties";
    private static final String APPLY_TRAVELERS_PROPERTIES_DESCRIPTOR =
            "(Lcollinvht/travelers/server/animal/obj/attributes/"
                    + "EntityAttributeProperties;"
                    + "Lcollinvht/travelers/server/animal/obj/attributes/"
                    + "EntityBaseProperties;)V";
    private static final String MOB_CATEGORY_INTERNAL_NAME =
            "net/minecraft/world/entity/MobCategory";
    private static final String MOB_CATEGORY_DESCRIPTOR =
            "Lnet/minecraft/world/entity/MobCategory;";
    private static final String ITEM_LIKE_SPAWN_DESCRIPTOR =
            "(Lnet/minecraft/world/level/ItemLike;)"
                    + "Lnet/minecraft/world/entity/item/ItemEntity;";
    private static final String FOOD_DESCRIPTOR =
            "(Lnet/minecraft/world/item/ItemStack;)Z";
    private static final String PLAYER_FEED_DESCRIPTOR =
            "(Lnet/minecraft/world/entity/player/Player;"
                    + "Lnet/minecraft/world/InteractionHand;"
                    + "Lnet/minecraft/world/item/ItemStack;)V";
    private static final String MOB_INTERACT_DESCRIPTOR =
            "(Lnet/minecraft/world/entity/player/Player;"
                    + "Lnet/minecraft/world/InteractionHand;)"
                    + "Lnet/minecraft/world/InteractionResult;";
    private static final String NBT_DESCRIPTOR =
            "(Lnet/minecraft/nbt/CompoundTag;)V";
    private static final int MAX_CLASS_BYTES = 8 * 1024 * 1024;

    private static final Map<String, PeriodicContract> PERIODIC_CONTRACTS =
            Map.of(
                    OSTRICH_INTERNAL_NAME,
                    new PeriodicContract(
                            6000,
                            "OSTRICH_EGG",
                            "net/minecraft/tags/ItemTags",
                            "VILLAGER_PLANTABLE_SEEDS",
                            true,
                            false,
                            false
                    ),
                    ALLIGATOR_INTERNAL_NAME,
                    new PeriodicContract(
                            5000,
                            "ALLIGATOR_EGG",
                            "net/minecraft/tags/ItemTags",
                            "FISHES",
                            true,
                            false,
                            false
                    ),
                    REED_FROG_INTERNAL_NAME,
                    new PeriodicContract(
                            3000,
                            "FROG_EGG",
                            "jp/jurassicsaga/server/item/JSItems",
                            "MOSQUITO",
                            true,
                            false,
                            false
                    ),
                    BASILISK_INTERNAL_NAME,
                    new PeriodicContract(
                            3000,
                            "BASILISK_EGG",
                            "jp/jurassicsaga/server/item/JSItems",
                            "MOSQUITO",
                            false,
                            true,
                            true
                    )
            );

    private static volatile Report runtimeReport;

    private JurassicSagaBreedingCompatibilityGate() {
    }

    public static Report probeRuntimeOnce() {
        Report cached = runtimeReport;
        if (cached != null) {
            return cached;
        }
        synchronized (JurassicSagaBreedingCompatibilityGate.class) {
            cached = runtimeReport;
            if (cached == null) {
                cached = probeRuntime();
                runtimeReport = cached;
            }
            return cached;
        }
    }

    public static Report probeJar(Path jarPath) {
        if (jarPath == null || !Files.isRegularFile(jarPath)) {
            return absent("Jurassic Saga archive is absent");
        }
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            return probe(readRelevantClasses(jar));
        } catch (IOException | RuntimeException exception) {
            return drift("Could not inspect Jurassic Saga archive: "
                    + exception.getClass().getSimpleName());
        }
    }

    public static Report probe(Map<String, byte[]> classes) {
        if (classes == null
                || missing(classes.get(ANIMAL_BASE_INTERNAL_NAME))) {
            return absent("Jurassic Saga JSAnimalBase class is absent");
        }

        Map<String, ClassNode> parsed = new LinkedHashMap<>();
        try {
            for (Map.Entry<String, byte[]> entry : classes.entrySet()) {
                byte[] bytes = entry.getValue();
                if (missing(bytes) || bytes.length > MAX_CLASS_BYTES) {
                    return drift("Invalid class bytes for " + entry.getKey());
                }
                ClassNode node = new ClassNode();
                new ClassReader(bytes).accept(
                        node,
                        ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES
                );
                if (!entry.getKey().equals(node.name)) {
                    return drift("Class entry name does not match owner " + entry.getKey());
                }
                parsed.put(entry.getKey(), node);
            }
        } catch (RuntimeException exception) {
            return drift("Could not parse Jurassic Saga breeding classes");
        }

        Report base = validateAnimalBase(parsed.get(ANIMAL_BASE_INTERNAL_NAME));
        if (!base.supported()) {
            return base;
        }
        Report holder = validateDataHolder(parsed.get(DATA_HOLDER_INTERNAL_NAME));
        if (!holder.supported()) {
            return holder;
        }
        Report despawnOverrides = validateNoDespawnOverrides(parsed);
        if (!despawnOverrides.supported()) {
            return despawnOverrides;
        }

        Set<String> candidates = new HashSet<>();
        for (ClassNode node : parsed.values()) {
            if (node.name.startsWith(ENTITY_PREFIX) && isPeriodicItemEggCandidate(node)) {
                candidates.add(node.name);
            }
        }
        if (!candidates.equals(PERIODIC_CONTRACTS.keySet())) {
            Set<String> missing = new HashSet<>(PERIODIC_CONTRACTS.keySet());
            missing.removeAll(candidates);
            Set<String> unexpected = new HashSet<>(candidates);
            unexpected.removeAll(PERIODIC_CONTRACTS.keySet());
            return drift("Periodic item-egg species set drifted; missing="
                    + missing + ", unexpected=" + unexpected);
        }

        for (Map.Entry<String, PeriodicContract> entry : PERIODIC_CONTRACTS.entrySet()) {
            Report periodic = validatePeriodicSpecies(
                    parsed.get(entry.getKey()),
                    entry.getValue()
            );
            if (!periodic.supported()) {
                return periodic;
            }
        }
        return new Report(
                Status.READY,
                List.of("Jurassic Saga breeding contract matches the supported binary")
        );
    }

    static void resetForTests() {
        runtimeReport = null;
    }

    private static Report probeRuntime() {
        ClassLoader context = Thread.currentThread().getContextClassLoader();
        ClassLoader loader = context == null
                ? JurassicSagaBreedingCompatibilityGate.class.getClassLoader()
                : context;
        URL baseResource = loader.getResource(ANIMAL_BASE_INTERNAL_NAME + ".class");
        if (baseResource == null) {
            return absent("Jurassic Saga JSAnimalBase class is absent");
        }
        try {
            Map<String, byte[]> classes = readRuntimeOrigin(baseResource);
            if (classes.isEmpty()) {
                return drift("Could not enumerate the Jurassic Saga entity classes");
            }
            return probe(classes);
        } catch (IOException | RuntimeException | URISyntaxException exception) {
            return drift("Could not enumerate the Jurassic Saga binary: "
                    + exception.getClass().getSimpleName());
        }
    }

    private static Map<String, byte[]> readRuntimeOrigin(URL baseResource)
            throws IOException, URISyntaxException {
        if (baseResource.openConnection() instanceof JarURLConnection connection) {
            connection.setUseCaches(false);
            try (JarFile jar = connection.getJarFile()) {
                return readRelevantClasses(jar);
            }
        }
        if ("file".equalsIgnoreCase(baseResource.getProtocol())) {
            Path classFile = Path.of(baseResource.toURI());
            Path root = classpathRoot(classFile, ANIMAL_BASE_INTERNAL_NAME + ".class");
            return readRelevantClasses(root);
        }

        Optional<Path> unionArchive = unionArchive(baseResource);
        if (unionArchive.isPresent()) {
            try (JarFile jar = new JarFile(unionArchive.get().toFile())) {
                return readRelevantClasses(jar);
            }
        }

        try {
            Path classFile = Path.of(baseResource.toURI());
            Path root = classpathRoot(classFile, ANIMAL_BASE_INTERNAL_NAME + ".class");
            return readRelevantClasses(root);
        } catch (FileSystemNotFoundException | IllegalArgumentException exception) {
            return readFromClasspath();
        }
    }

    private static Map<String, byte[]> readFromClasspath() throws IOException {
        String classPath = System.getProperty("java.class.path", "");
        for (String value : classPath.split(java.io.File.pathSeparator)) {
            if (value.isBlank()) {
                continue;
            }
            Path entry;
            try {
                entry = Path.of(value).toAbsolutePath().normalize();
            } catch (RuntimeException exception) {
                continue;
            }
            if (Files.isRegularFile(entry)) {
                try (JarFile jar = new JarFile(entry.toFile())) {
                    if (jar.getJarEntry(ANIMAL_BASE_INTERNAL_NAME + ".class") != null) {
                        return readRelevantClasses(jar);
                    }
                } catch (IOException ignored) {
                    // Not every classpath file is a ZIP/JAR.
                }
            } else if (Files.isDirectory(entry)
                    && Files.isRegularFile(entry.resolve(ANIMAL_BASE_INTERNAL_NAME + ".class"))) {
                return readRelevantClasses(entry);
            }
        }
        return Map.of();
    }

    private static Optional<Path> unionArchive(URL resource) {
        String external = URLDecoder.decode(
                resource.toExternalForm(),
                StandardCharsets.UTF_8
        );
        int colon = external.indexOf(':');
        int separator = external.indexOf("!/");
        if (colon < 0 || separator <= colon) {
            return Optional.empty();
        }
        String origin = external.substring(colon + 1, separator);
        int layerSeparator = origin.lastIndexOf('#');
        if (layerSeparator >= 0
                && origin.substring(layerSeparator + 1).chars().allMatch(Character::isDigit)) {
            origin = origin.substring(0, layerSeparator);
        }
        try {
            Path path;
            if (origin.startsWith("file:")) {
                path = Path.of(URI.create(origin));
            } else {
                if (origin.matches("^/[A-Za-z]:/.*")) {
                    origin = origin.substring(1);
                }
                path = Path.of(origin);
            }
            return Files.isRegularFile(path) ? Optional.of(path) : Optional.empty();
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private static Path classpathRoot(Path classFile, String resourceName) {
        Path root = classFile;
        for (int ignored = 0; ignored < resourceName.split("/").length; ignored++) {
            root = root.getParent();
            if (root == null) {
                throw new IllegalArgumentException("Resource has no classpath root");
            }
        }
        return root;
    }

    private static Map<String, byte[]> readRelevantClasses(JarFile jar) throws IOException {
        Map<String, byte[]> classes = new HashMap<>();
        var entries = jar.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String name = entry.getName();
            if (!isRelevantClassEntry(name)) {
                continue;
            }
            long declaredSize = entry.getSize();
            if (declaredSize > MAX_CLASS_BYTES) {
                throw new IOException("Oversized class entry " + name);
            }
            try (var stream = jar.getInputStream(entry)) {
                byte[] bytes = stream.readNBytes(MAX_CLASS_BYTES + 1);
                if (bytes.length > MAX_CLASS_BYTES) {
                    throw new IOException("Oversized class entry " + name);
                }
                classes.put(toInternalName(name), bytes);
            }
        }
        return classes;
    }

    private static Map<String, byte[]> readRelevantClasses(Path root) throws IOException {
        Path animalRoot = root.resolve(ANIMAL_ROOT);
        if (!Files.isDirectory(animalRoot)) {
            return Map.of();
        }
        Map<String, byte[]> classes = new HashMap<>();
        try (var paths = Files.walk(animalRoot)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                String name = root.relativize(path).toString().replace('\\', '/');
                if (!isRelevantClassEntry(name)) {
                    continue;
                }
                long size = Files.size(path);
                if (size > MAX_CLASS_BYTES) {
                    throw new IOException("Oversized class file " + name);
                }
                classes.put(toInternalName(name), Files.readAllBytes(path));
            }
        }
        return classes;
    }

    private static boolean isRelevantClassEntry(String name) {
        return name != null
                && (name.startsWith(ENTITY_PREFIX)
                || name.startsWith(ANIMAL_DEFINITION_PREFIX))
                && name.endsWith(".class")
                && !name.equals("module-info.class");
    }

    private static String toInternalName(String entryName) {
        return entryName.substring(0, entryName.length() - ".class".length());
    }

    private static Report validateAnimalBase(ClassNode node) {
        if (node == null || !ANIMAL_BASE_INTERNAL_NAME.equals(node.name)) {
            return drift("JSAnimalBase class bytes are absent or mismatched");
        }
        MethodNode serverAi = uniqueMethod(node, "customServerAiStep", "()V");
        if (serverAi == null) {
            return drift("JSAnimalBase.customServerAiStep()V must exist exactly once");
        }
        List<FieldInsnNode> mateWrites = fields(
                serverAi,
                instruction -> instruction.getOpcode() == Opcodes.PUTFIELD
                        && ANIMAL_BASE_INTERNAL_NAME.equals(instruction.owner)
                        && "isLookingForMate".equals(instruction.name)
                        && "Z".equals(instruction.desc)
        );
        if (mateWrites.size() != 1
                || opcode(previousMeaningful(mateWrites.getFirst())) != Opcodes.ICONST_1
                || opcode(previousMeaningful(previousMeaningful(mateWrites.getFirst())))
                != Opcodes.ALOAD) {
            return drift("Random mate-search true write no longer matches the exact field tail");
        }
        if (countCalls(
                serverAi,
                "net/minecraft/util/RandomSource",
                "nextFloat",
                "()F"
        ) < 1 || !containsFloat(serverAi, 0.01F)) {
            return drift("Random mate-search probability path drifted");
        }

        MethodNode interaction = uniqueMethod(node, "mobInteract", MOB_INTERACT_DESCRIPTOR);
        MethodNode canEatFromPlayer = uniqueMethod(
                node,
                "canEatFromPlayer",
                "(Lnet/minecraft/world/entity/player/Player;"
                        + "Lnet/minecraft/world/InteractionHand;)Z"
        );
        if (interaction == null
                || canEatFromPlayer == null
                || countCalls(
                        interaction,
                        ANIMAL_BASE_INTERNAL_NAME,
                        "onEatFromPlayer",
                        PLAYER_FEED_DESCRIPTOR
                ) != 1) {
            return drift("JSAnimalBase player-feed invocation contract drifted");
        }
        MethodNode removal = uniqueMethod(node, "removeWhenFarAway", "(D)Z");
        MethodNode despawn = uniqueMethod(node, "checkDespawn", "()V");
        if (removal == null
                || despawn == null
                || countCalls(
                        removal,
                        ANIMAL_BASE_INTERNAL_NAME,
                        "isPersistenceRequired",
                        "()Z"
                ) != 1
                || countCalls(
                        despawn,
                        ANIMAL_BASE_INTERNAL_NAME,
                        "removeWhenFarAway",
                        "(D)Z"
                ) != 2) {
            return drift("JSAnimalBase despawn contract drifted");
        }
        return ready();
    }

    private static Report validateNoDespawnOverrides(Map<String, ClassNode> parsed) {
        Map<String, Set<String>> categoriesByEntity = resolveEntityCategories(parsed);
        for (ClassNode node : parsed.values()) {
            if (ANIMAL_BASE_INTERNAL_NAME.equals(node.name)
                    || !isSubclassOf(node, parsed, ANIMAL_BASE_INTERNAL_NAME)) {
                continue;
            }
            if (uniqueMethod(node, "checkDespawn", "()V") == null) {
                continue;
            }
            Set<String> categories = categoriesByEntity.get(node.name);
            if (categories == null || categories.size() != 1) {
                return drift(node.name + " checkDespawn override category"
                        + " could not be proven uniquely");
            }
            String category = categories.iterator().next();
            if ("CREATURE".equals(category) || "WATER_CREATURE".equals(category)) {
                return drift(node.name + " declares an unsupported checkDespawn override"
                        + " for " + category);
            }
        }
        return ready();
    }

    private static Map<String, Set<String>> resolveEntityCategories(
            Map<String, ClassNode> parsed
    ) {
        Map<String, Set<String>> categoriesByEntity = new HashMap<>();
        for (ClassNode node : parsed.values()) {
            if (!node.name.startsWith(ANIMAL_DEFINITION_PREFIX)) {
                continue;
            }
            Optional<EntityCategory> mapping = resolveEntityCategory(node);
            mapping.ifPresent(value -> categoriesByEntity
                    .computeIfAbsent(value.entity(), ignored -> new HashSet<>())
                    .add(value.category()));
        }
        return categoriesByEntity;
    }

    private static Optional<EntityCategory> resolveEntityCategory(ClassNode definition) {
        MethodNode properties = uniqueMethod(
                definition,
                "applyTravelersProperties",
                APPLY_TRAVELERS_PROPERTIES_DESCRIPTOR
        );
        if (properties == null) {
            return Optional.empty();
        }

        Set<String> entities = new HashSet<>();
        Set<String> categories = new HashSet<>();
        for (AbstractInsnNode instruction : properties.instructions) {
            if (!(instruction instanceof MethodInsnNode call)
                    || !ENTITY_ATTRIBUTE_PROPERTIES_INTERNAL_NAME.equals(call.owner)) {
                continue;
            }
            AbstractInsnNode argument = previousMeaningful(call);
            if ("setEntityFactory".equals(call.name)
                    && argument instanceof InvokeDynamicInsnNode dynamic) {
                for (Object bootstrapArgument : dynamic.bsmArgs) {
                    if (bootstrapArgument instanceof Handle handle
                            && handle.getTag() == Opcodes.H_NEWINVOKESPECIAL
                            && "<init>".equals(handle.getName())
                            && handle.getOwner().startsWith(ENTITY_PREFIX)) {
                        entities.add(handle.getOwner());
                    }
                }
            } else if ("setCategory".equals(call.name)
                    && argument instanceof FieldInsnNode field
                    && field.getOpcode() == Opcodes.GETSTATIC
                    && MOB_CATEGORY_INTERNAL_NAME.equals(field.owner)
                    && MOB_CATEGORY_DESCRIPTOR.equals(field.desc)) {
                categories.add(field.name);
            }
        }
        if (entities.size() != 1 || categories.size() != 1) {
            return Optional.empty();
        }
        return Optional.of(new EntityCategory(
                entities.iterator().next(),
                categories.iterator().next()
        ));
    }

    private static boolean isSubclassOf(
            ClassNode node,
            Map<String, ClassNode> parsed,
            String expectedSuperclass
    ) {
        Set<String> visited = new HashSet<>();
        String superclass = node == null ? null : node.superName;
        while (superclass != null && visited.add(superclass)) {
            if (expectedSuperclass.equals(superclass)) {
                return true;
            }
            ClassNode parent = parsed.get(superclass);
            superclass = parent == null ? null : parent.superName;
        }
        return false;
    }

    private static Report validateDataHolder(ClassNode node) {
        if (node == null || !DATA_HOLDER_INTERNAL_NAME.equals(node.name)) {
            return drift("JSEntityDataHolder class bytes are absent or mismatched");
        }
        MethodNode read = uniqueMethod(node, "readAdditionalSaveData", NBT_DESCRIPTOR);
        MethodNode write = uniqueMethod(node, "addAdditionalSaveData", NBT_DESCRIPTOR);
        MethodNode getCooldown = uniqueMethod(node, "getBreedingCooldown", "()I");
        if (read == null
                || write == null
                || getCooldown == null
                || (getCooldown.access & Opcodes.ACC_PUBLIC) == 0
                || countString(read, "js.mating") != 1
                || countString(write, "js.mating") != 1
                || !hasCallAfterString(
                        read,
                        "js.mating",
                        "net/minecraft/nbt/CompoundTag",
                        "getBoolean",
                        "(Ljava/lang/String;)Z",
                        2
                )
                || !hasCallAfterString(
                        write,
                        "js.mating",
                        "net/minecraft/nbt/CompoundTag",
                        "putBoolean",
                        "(Ljava/lang/String;Z)V",
                        5
                )) {
            return drift("JSEntityDataHolder mating NBT contract drifted");
        }
        return ready();
    }

    private static boolean isPeriodicItemEggCandidate(ClassNode node) {
        MethodNode aiStep = uniqueMethod(node, "aiStep", "()V");
        if (aiStep == null) {
            return false;
        }
        for (AbstractInsnNode instruction : aiStep.instructions) {
            if (instruction instanceof MethodInsnNode call
                    && "spawnAtLocation".equals(call.name)
                    && ITEM_LIKE_SPAWN_DESCRIPTOR.equals(call.desc)) {
                return true;
            }
        }
        return false;
    }

    private static Report validatePeriodicSpecies(
            ClassNode node,
            PeriodicContract contract
    ) {
        if (node == null) {
            return drift("Periodic species class is absent");
        }
        if (uniqueMethod(node, "canEatItem", FOOD_DESCRIPTOR) == null
                || uniqueMethod(node, "onEatFromPlayer", PLAYER_FEED_DESCRIPTOR) == null) {
            return drift(node.name + " player-feed override contract drifted");
        }
        MethodNode food = uniqueMethod(node, "canEatItem", FOOD_DESCRIPTOR);
        if (countFields(
                food,
                contract.foodOwner(),
                contract.foodField()
        ) != 1) {
            return drift(node.name + " breeding-food contract drifted");
        }

        MethodNode constructor = uniqueMethod(
                node,
                "<init>",
                "(Lnet/minecraft/world/entity/EntityType;"
                        + "Lnet/minecraft/world/level/Level;)V"
        );
        if (constructor == null || !hasTimerReset(constructor, node.name, contract.timerBase())) {
            return drift(node.name + " initial egg timer contract drifted");
        }

        MethodNode aiStep = uniqueMethod(node, "aiStep", "()V");
        if (aiStep == null) {
            return drift(node.name + ".aiStep()V must exist exactly once");
        }
        List<MethodInsnNode> spawns = calls(
                aiStep,
                call -> call.getOpcode() == Opcodes.INVOKEVIRTUAL
                        && node.name.equals(call.owner)
                        && "spawnAtLocation".equals(call.name)
                        && ITEM_LIKE_SPAWN_DESCRIPTOR.equals(call.desc)
        );
        if (spawns.size() != 1
                || opcode(nextMeaningful(spawns.getFirst())) != Opcodes.POP) {
            return drift(node.name + " periodic item-egg spawn site drifted");
        }
        if (!hasFieldInPreviousInstructions(
                spawns.getFirst(),
                "jp/jurassicsaga/server/item/JSItems",
                contract.eggField(),
                5
        )) {
            return drift(node.name + " periodic egg item identity drifted");
        }
        if (!hasEggTimerDecrement(aiStep, node.name)
                || !hasTimerReset(aiStep, node.name, contract.timerBase())
                || countFields(aiStep, node.name, "eggTime") != 3) {
            return drift(node.name + " periodic egg timer update contract drifted");
        }

        boolean hasGamerule = countFields(
                aiStep,
                "jp/jurassicsaga/JSCommon",
                "CHICKEN_EGG_DROP"
        ) == 1;
        if (hasGamerule != contract.gameruleGated()) {
            return drift(node.name + " egg gamerule contract drifted");
        }
        boolean hasMaleGate = countCalls(
                aiStep,
                "jp/jurassicsaga/server/animal/entity/obj/modules/obj/JSGeneticModule",
                "isMale",
                "()Z"
        ) == 1;
        if (hasMaleGate != contract.femaleOnly()) {
            return drift(node.name + " egg sex-gate contract drifted");
        }

        boolean savesTimer = hasEggLayTimeNbt(node);
        if (savesTimer != contract.persistsTimer()) {
            return drift(node.name + " egg timer persistence contract drifted");
        }
        return ready();
    }

    private static boolean hasTimerReset(
            MethodNode method,
            String owner,
            int timerBase
    ) {
        for (AbstractInsnNode instruction : method.instructions) {
            if (!(instruction instanceof FieldInsnNode field)
                    || field.getOpcode() != Opcodes.PUTFIELD
                    || !owner.equals(field.owner)
                    || !"eggTime".equals(field.name)
                    || !"I".equals(field.desc)
                    || opcode(previousMeaningful(field)) != Opcodes.IADD) {
                continue;
            }
            AbstractInsnNode offset = previousMeaningful(previousMeaningful(field));
            if (integerValue(offset).orElse(Integer.MIN_VALUE) != timerBase) {
                continue;
            }
            AbstractInsnNode callInstruction = previousMeaningful(offset);
            if (callInstruction instanceof MethodInsnNode call
                    && "net/minecraft/util/RandomSource".equals(call.owner)
                    && "nextInt".equals(call.name)
                    && "(I)I".equals(call.desc)
                    && integerValue(previousMeaningful(call)).orElse(Integer.MIN_VALUE)
                    == timerBase) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasEggTimerDecrement(MethodNode method, String owner) {
        for (AbstractInsnNode instruction : method.instructions) {
            if (!(instruction instanceof FieldInsnNode field)
                    || field.getOpcode() != Opcodes.PUTFIELD
                    || !owner.equals(field.owner)
                    || !"eggTime".equals(field.name)
                    || !"I".equals(field.desc)) {
                continue;
            }
            AbstractInsnNode duplicate = previousMeaningful(field);
            AbstractInsnNode subtract = previousMeaningful(duplicate);
            AbstractInsnNode one = previousMeaningful(subtract);
            AbstractInsnNode read = previousMeaningful(one);
            if (opcode(duplicate) == Opcodes.DUP_X1
                    && opcode(subtract) == Opcodes.ISUB
                    && opcode(one) == Opcodes.ICONST_1
                    && read instanceof FieldInsnNode source
                    && source.getOpcode() == Opcodes.GETFIELD
                    && owner.equals(source.owner)
                    && "eggTime".equals(source.name)
                    && "I".equals(source.desc)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasEggLayTimeNbt(ClassNode node) {
        MethodNode read = uniqueMethod(node, "readAdditionalSaveData", NBT_DESCRIPTOR);
        MethodNode write = uniqueMethod(node, "addAdditionalSaveData", NBT_DESCRIPTOR);
        return read != null
                && write != null
                && countString(read, "EggLayTime") == 2
                && countString(write, "EggLayTime") == 1
                && countCalls(
                        read,
                        "net/minecraft/nbt/CompoundTag",
                        "getInt",
                        "(Ljava/lang/String;)I"
                ) == 1
                && countCalls(
                        write,
                        "net/minecraft/nbt/CompoundTag",
                        "putInt",
                        "(Ljava/lang/String;I)V"
                ) == 1;
    }

    private static MethodNode uniqueMethod(ClassNode node, String name, String descriptor) {
        if (node == null) {
            return null;
        }
        List<MethodNode> matches = node.methods.stream()
                .filter(method -> name.equals(method.name) && descriptor.equals(method.desc))
                .toList();
        return matches.size() == 1 ? matches.getFirst() : null;
    }

    private static List<FieldInsnNode> fields(
            MethodNode method,
            Predicate<FieldInsnNode> predicate
    ) {
        List<FieldInsnNode> matches = new ArrayList<>();
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof FieldInsnNode field && predicate.test(field)) {
                matches.add(field);
            }
        }
        return matches;
    }

    private static List<MethodInsnNode> calls(
            MethodNode method,
            Predicate<MethodInsnNode> predicate
    ) {
        List<MethodInsnNode> matches = new ArrayList<>();
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && predicate.test(call)) {
                matches.add(call);
            }
        }
        return matches;
    }

    private static int countFields(MethodNode method, String owner, String name) {
        return fields(method, field -> owner.equals(field.owner) && name.equals(field.name)).size();
    }

    private static int countCalls(
            MethodNode method,
            String owner,
            String name,
            String descriptor
    ) {
        return calls(
                method,
                call -> owner.equals(call.owner)
                        && name.equals(call.name)
                        && descriptor.equals(call.desc)
        ).size();
    }

    private static int countString(MethodNode method, String value) {
        int count = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof LdcInsnNode constant && value.equals(constant.cst)) {
                count++;
            }
        }
        return count;
    }

    private static boolean containsFloat(MethodNode method, float value) {
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof LdcInsnNode constant
                    && constant.cst instanceof Float actual
                    && Float.compare(actual, value) == 0) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasCallAfterString(
            MethodNode method,
            String value,
            String owner,
            String name,
            String descriptor,
            int limit
    ) {
        for (AbstractInsnNode instruction : method.instructions) {
            if (!(instruction instanceof LdcInsnNode constant)
                    || !value.equals(constant.cst)) {
                continue;
            }
            AbstractInsnNode current = instruction;
            for (int index = 0; index < limit; index++) {
                current = nextMeaningful(current);
                if (current == null) {
                    break;
                }
                if (current instanceof MethodInsnNode call
                        && owner.equals(call.owner)
                        && name.equals(call.name)
                        && descriptor.equals(call.desc)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasFieldInPreviousInstructions(
            AbstractInsnNode start,
            String owner,
            String name,
            int limit
    ) {
        AbstractInsnNode current = start;
        for (int index = 0; index < limit; index++) {
            current = previousMeaningful(current);
            if (current == null) {
                return false;
            }
            if (current instanceof FieldInsnNode field
                    && owner.equals(field.owner)
                    && name.equals(field.name)) {
                return true;
            }
        }
        return false;
    }

    private static Optional<Integer> integerValue(AbstractInsnNode instruction) {
        if (instruction == null) {
            return Optional.empty();
        }
        return switch (instruction.getOpcode()) {
            case Opcodes.ICONST_M1 -> Optional.of(-1);
            case Opcodes.ICONST_0 -> Optional.of(0);
            case Opcodes.ICONST_1 -> Optional.of(1);
            case Opcodes.ICONST_2 -> Optional.of(2);
            case Opcodes.ICONST_3 -> Optional.of(3);
            case Opcodes.ICONST_4 -> Optional.of(4);
            case Opcodes.ICONST_5 -> Optional.of(5);
            case Opcodes.BIPUSH, Opcodes.SIPUSH ->
                    Optional.of(((IntInsnNode) instruction).operand);
            default -> instruction instanceof LdcInsnNode constant
                    && constant.cst instanceof Integer value
                    ? Optional.of(value)
                    : Optional.empty();
        };
    }

    private static AbstractInsnNode previousMeaningful(AbstractInsnNode instruction) {
        AbstractInsnNode current = instruction == null ? null : instruction.getPrevious();
        while (current != null && current.getOpcode() < 0) {
            current = current.getPrevious();
        }
        return current;
    }

    private static AbstractInsnNode nextMeaningful(AbstractInsnNode instruction) {
        AbstractInsnNode current = instruction == null ? null : instruction.getNext();
        while (current != null && current.getOpcode() < 0) {
            current = current.getNext();
        }
        return current;
    }

    private static int opcode(AbstractInsnNode instruction) {
        return instruction == null ? -1 : instruction.getOpcode();
    }

    private static boolean missing(byte[] bytes) {
        return bytes == null || bytes.length == 0;
    }

    private static Report ready() {
        return new Report(Status.READY, List.of());
    }

    private static Report absent(String diagnostic) {
        return new Report(Status.ABSENT, List.of(diagnostic));
    }

    private static Report drift(String diagnostic) {
        return new Report(Status.DRIFT, List.of(diagnostic));
    }

    private record PeriodicContract(
            int timerBase,
            String eggField,
            String foodOwner,
            String foodField,
            boolean gameruleGated,
            boolean femaleOnly,
            boolean persistsTimer
    ) {
    }

    private record EntityCategory(String entity, String category) {
    }

    public record Report(Status status, List<String> diagnostics) {
        public Report {
            diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        }

        public boolean supported() {
            return this.status == Status.READY;
        }
    }

    public enum Status {
        READY,
        ABSENT,
        DRIFT
    }
}
