package dev.xpple.seedmapper.datapack;

import com.github.cubiomes.Cubiomes;
import com.mojang.logging.LogUtils;
import dev.xpple.seedmapper.SeedMapper;
import dev.xpple.seedmapper.config.Configs;
import dev.xpple.seedmapper.seedmap.SeedMapScreen;
import dev.xpple.seedmapper.util.WorldIdentifier;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.packs.BuiltInMetadata;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.VanillaPackResourcesBuilder;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.CloseableResourceManager;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.slf4j.Logger;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.lang.reflect.Method;

public final class DatapackStructureManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<Long, DatapackStructureCollection> COLLECTIONS = new HashMap<>();
    private static final Method STRUCTURE_PLACEMENT_SALT = resolvePlacementSaltMethod();
    private static volatile String lastImportedUrl = null;
    private static volatile Path lastImportedCachePath = null;

    private DatapackStructureManager() {}

    public static List<CustomStructureSet> get(WorldIdentifier identifier) {
        if (identifier == null) {
            return Collections.emptyList();
        }
        synchronized (COLLECTIONS) {
            DatapackStructureCollection collection = COLLECTIONS.get(identifier.seed());
            return collection != null ? collection.sets() : Collections.emptyList();
        }
    }

    public static DatapackWorldgen getWorldgen(WorldIdentifier identifier) {
        if (identifier == null) {
            return null;
        }
        synchronized (COLLECTIONS) {
            DatapackStructureCollection collection = COLLECTIONS.get(identifier.seed());
            return collection != null ? collection.worldgen() : null;
        }
    }

    public static CompletableFuture<Void> importDatapack(WorldIdentifier identifier, String url, Consumer<Component> onSuccess, Consumer<Component> onFailure) {
        return CompletableFuture.runAsync(() -> {
            if (url == null || url.isBlank()) {
                onFailure.accept(Component.translatable("seedMap.datapackImport.failed", Component.literal("empty")));
                return;
            }
            lastImportedUrl = url.trim();
            Path downloaded = null;
            try {
                downloaded = downloadDatapack(url);
                Path extracted = prepareDatapack(downloaded);
                Path root = validateDatapackRoot(extracted);
                loadDatapack(identifier, root, onSuccess, onFailure);
            } catch (Exception e) {
                LOGGER.error("Failed to import datapack", e);
                String message = e.getMessage();
                if (message == null || message.isBlank()) {
                    message = "unknown";
                }
                onFailure.accept(Component.translatable("seedMap.datapackImport.failed", Component.literal(message)));
            } finally {
                if (downloaded != null) {
                    try {
                        Files.deleteIfExists(downloaded);
                    } catch (IOException e) {
                        LOGGER.warn("Failed to delete temporary datapack", e);
                    }
                }
            }
        });
    }

    public static CompletableFuture<Void> importDatapackFromPath(WorldIdentifier identifier, Path datapackRoot, Consumer<Component> onSuccess, Consumer<Component> onFailure) {
        return CompletableFuture.runAsync(() -> {
            if (datapackRoot == null) {
                onFailure.accept(Component.translatable("seedMap.datapackImport.failed", Component.literal("missing cache")));
                return;
            }
            try {
                Path root = validateDatapackRoot(datapackRoot);
                loadDatapack(identifier, root, onSuccess, onFailure);
            } catch (Exception e) {
                LOGGER.error("Failed to import datapack from cache", e);
                String message = e.getMessage();
                if (message == null || message.isBlank()) {
                    message = "unknown";
                }
                onFailure.accept(Component.translatable("seedMap.datapackImport.failed", Component.literal(message)));
            }
        });
    }

    public static CompletableFuture<Void> importDatapackWithFallback(WorldIdentifier identifier, Path cachePath, String url, Consumer<Component> onSuccess, Consumer<Component> onFailure) {
        return CompletableFuture.runAsync(() -> {
            boolean triedCache = false;
            if (cachePath != null) {
                triedCache = true;
                try {
                    Path root = validateDatapackRoot(cachePath);
                    loadDatapack(identifier, root, onSuccess, onFailure);
                    return;
                } catch (Exception e) {
                    LOGGER.warn("Failed to load cached datapack, falling back to URL", e);
                }
            }
            if (url != null && !url.isBlank()) {
                try {
                    lastImportedUrl = url.trim();
                    Path downloaded = downloadDatapack(url);
                    try {
                        Path extracted = prepareDatapack(downloaded);
                        Path root = validateDatapackRoot(extracted);
                        loadDatapack(identifier, root, onSuccess, onFailure);
                    } finally {
                        Files.deleteIfExists(downloaded);
                    }
                    return;
                } catch (Exception e) {
                    LOGGER.error("Failed to import datapack", e);
                    String message = e.getMessage();
                    if (message == null || message.isBlank()) {
                        message = "unknown";
                    }
                    onFailure.accept(Component.translatable("seedMap.datapackImport.failed", Component.literal(message)));
                    return;
                }
            }
            if (triedCache) {
                onFailure.accept(Component.translatable("seedMap.datapackImport.failed", Component.literal("cache invalid")));
            } else {
                onFailure.accept(Component.translatable("seedMap.datapackImport.failed", Component.literal("missing url")));
            }
        });
    }

    public static String getLastImportedUrl() {
        return lastImportedUrl;
    }

    public static Path getLastImportedCachePath() {
        return lastImportedCachePath;
    }

    private static void loadDatapack(WorldIdentifier identifier, Path datapackRoot, Consumer<Component> onSuccess, Consumer<Component> onFailure) throws IOException {
        DatapackWorldgen worldgen = DatapackWorldgen.load(datapackRoot, identifier.seed());
        List<CustomStructureSet> sets = worldgen.customStructureSets();
        if (sets.isEmpty()) {
            try {
                worldgen.close();
            } catch (Exception e) {
                LOGGER.warn("Failed to close datapack worldgen", e);
            }
            throw new IOException("No structures");
        }
        DatapackStructureCollection collection = new DatapackStructureCollection(worldgen, sets);
        synchronized (COLLECTIONS) {
            DatapackStructureCollection previous = COLLECTIONS.put(identifier.seed(), collection);
            if (previous != null) {
                previous.close();
            }
        }
        lastImportedCachePath = datapackRoot;
        Configs.ShowDatapackStructures = true;
        Configs.save();
        SeedMapScreen.reopenIfOpen(identifier.generatorFlags());
        onSuccess.accept(Component.translatable("seedMap.datapackImport.success", sets.size()));
    }

    private static Path downloadDatapack(String urlString) throws IOException {
        URL url;
        try {
            url = new URL(urlString);
        } catch (MalformedURLException e) {
            throw new IOException("Invalid URL", e);
        }
        URLConnection connection = url.openConnection();
        connection.setRequestProperty("User-Agent", "SeedMapper/" + SeedMapper.MOD_ID);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(15000);
        Path tempFile = Files.createTempFile(SeedMapper.MOD_ID + "-datapack", ".zip");
        try (InputStream input = connection.getInputStream()) {
            Files.copy(input, tempFile, StandardCopyOption.REPLACE_EXISTING);
        }
        return tempFile;
    }

    private static Path prepareDatapack(Path zippedFile) throws IOException {
        Path cacheRoot = SeedMapper.modConfigPath.resolve("datapacks");
        Files.createDirectories(cacheRoot);
        String hash = sha1Hex(zippedFile);
        Path unpacked = cacheRoot.resolve(hash);
        if (!Files.exists(unpacked)) {
            Files.createDirectories(unpacked);
            unzip(zippedFile, unpacked);
        }
        return resolveDatapackRoot(unpacked);
    }

    private static Path validateDatapackRoot(Path baseDir) throws IOException {
        if (baseDir == null) {
            throw new IOException("Missing datapack root");
        }
        if (!Files.exists(baseDir)) {
            throw new IOException("Datapack cache missing");
        }
        Path root = Files.isDirectory(baseDir) ? resolveDatapackRoot(baseDir) : baseDir;
        if (!Files.exists(root.resolve("data"))) {
            throw new IOException("Datapack cache missing data folder");
        }
        return root;
    }

    private static void unzip(Path zipFile, Path targetDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(Files.newInputStream(zipFile)))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String normalized = entry.getName().replace('\\', '/');
                Path target = targetDir.resolve(normalized).normalize();
                if (!target.startsWith(targetDir)) {
                    throw new IOException("Zip entry outside target dir");
                }
                Files.createDirectories(target.getParent());
                Files.copy(zis, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static Path resolveDatapackRoot(Path baseDir) throws IOException {
        if (Files.exists(baseDir.resolve("data"))) {
            return baseDir;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(baseDir)) {
            Path onlyDir = null;
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    if (onlyDir != null) {
                        return baseDir;
                    }
                    onlyDir = entry;
                }
            }
            if (onlyDir != null && Files.exists(onlyDir.resolve("data"))) {
                return onlyDir;
            }
        }
        return baseDir;
    }

    private static String sha1Hex(Path file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
        }
        StringBuilder builder = new StringBuilder();
        for (byte b : digest.digest()) {
            builder.append(String.format(Locale.ROOT, "%02x", b));
        }
        return builder.toString();
    }

    private record DatapackStructureCollection(DatapackWorldgen worldgen, List<CustomStructureSet> sets) implements AutoCloseable {
        @Override
        public void close() {
            if (this.worldgen != null) {
                try {
                    this.worldgen.close();
                } catch (Exception e) {
                    LOGGER.warn("Failed to close datapack worldgen", e);
                }
            }
        }
    }

    public static final class DatapackWorldgen implements AutoCloseable {
        private final CloseableResourceManager resourceManager;
        private final RegistryAccess.Frozen registryAccess;
        private final StructureTemplateManager templateManager;
        private final LevelStorageSource.LevelStorageAccess storageAccess;
        private final long seed;
        private final List<CustomStructureSet> customStructureSets;
        private final Map<Integer, DimensionContext> dimensionContexts = new HashMap<>();
        private final Map<Integer, List<CustomStructureSet>> dimensionStructureSets = new HashMap<>();
        private final Map<String, Map<Long, StructureResult>> structureCache = new HashMap<>();

        private DatapackWorldgen(CloseableResourceManager resourceManager, RegistryAccess.Frozen registryAccess, StructureTemplateManager templateManager,
                                 LevelStorageSource.LevelStorageAccess storageAccess, long seed, List<CustomStructureSet> customStructureSets) {
            this.resourceManager = resourceManager;
            this.registryAccess = registryAccess;
            this.templateManager = templateManager;
            this.storageAccess = storageAccess;
            this.seed = seed;
            this.customStructureSets = customStructureSets;
        }

        public static DatapackWorldgen load(Path datapackRoot, long seed) throws IOException {
            PackResources vanilla = createVanillaPack();
            PackResources customPack = createPathPack("seedmapper_datapack", datapackRoot, PackSource.WORLD);
            CloseableResourceManager resourceManager = new MultiPackResourceManager(PackType.SERVER_DATA, List.of(vanilla, customPack));
            RegistryAccess.Frozen registryAccess = loadRegistryAccess(resourceManager);
            Set<Identifier> vanillaStructures = loadVanillaStructureIds();
            LevelStorageSource.LevelStorageAccess storageAccess = createTempStorageAccess();
            StructureTemplateManager templateManager = createTemplateManager(resourceManager, storageAccess);
            List<CustomStructureSet> sets = buildCustomStructureSets(registryAccess, vanillaStructures);
            if (Configs.DevMode) {
                Registry<StructureSet> structureSets = registryAccess.lookupOrThrow(Registries.STRUCTURE_SET);
                Registry<Structure> structures = registryAccess.lookupOrThrow(Registries.STRUCTURE);
                LOGGER.info("Datapack registries: structure_sets={}, structures={}, custom_sets={}",
                    structureSets.keySet().size(), structures.keySet().size(), sets.size());
            }
            return new DatapackWorldgen(resourceManager, registryAccess, templateManager, storageAccess, seed, sets);
        }

        public List<CustomStructureSet> customStructureSets() {
            return this.customStructureSets;
        }

        public List<CustomStructureSet> getStructureSetsForDimension(int dimensionId) {
            if (this.dimensionStructureSets.containsKey(dimensionId)) {
                List<CustomStructureSet> cached = this.dimensionStructureSets.get(dimensionId);
                return cached != null ? cached : Collections.emptyList();
            }
            DimensionContext context = getDimensionContext(dimensionId);
            if (context == null) {
                this.dimensionStructureSets.put(dimensionId, null);
                return Collections.emptyList();
            }
            Set<Holder<Biome>> possibleBiomes = context.biomeSource().possibleBiomes();
            List<CustomStructureSet> filtered = new ArrayList<>();
            for (CustomStructureSet set : this.customStructureSets) {
                boolean matchesDimension = false;
                for (StructureSetEntry entry : set.entries()) {
                    Structure structure = entry.structure().value();
                    for (Holder<Biome> biome : structure.biomes()) {
                        if (possibleBiomes.contains(biome)) {
                            matchesDimension = true;
                            break;
                        }
                    }
                    if (matchesDimension) {
                        break;
                    }
                }
                if (matchesDimension) {
                    filtered.add(set);
                }
            }
            List<CustomStructureSet> result = Collections.unmodifiableList(filtered);
            this.dimensionStructureSets.put(dimensionId, result);
            return result;
        }

        public DimensionContext getDimensionContext(int dimensionId) {
            if (this.dimensionContexts.containsKey(dimensionId)) {
                DimensionContext cached = this.dimensionContexts.get(dimensionId);
                if (cached != null) {
                    return cached;
                }
                this.dimensionContexts.remove(dimensionId);
            }
            ResourceKey<LevelStem> stemKey = stemKeyForDimension(dimensionId);
            if (stemKey == null) {
                this.dimensionContexts.put(dimensionId, null);
                return null;
            }
            Optional<Registry<LevelStem>> optionalStemRegistry = this.registryAccess.lookup(Registries.LEVEL_STEM);
            if (optionalStemRegistry.isEmpty()) {
                optionalStemRegistry = getClientLevelStemRegistry();
            }
            LevelStem stem = null;
            if (optionalStemRegistry.isPresent()) {
                Registry<LevelStem> stemRegistry = optionalStemRegistry.get();
                if (!stemRegistry.containsKey(stemKey)) {
                    if (Configs.DevMode) {
                        LOGGER.info("Datapack debug: missing LevelStem {} (available: {})",
                            stemKey.identifier(), stemRegistry.keySet());
                    }
                } else {
                    stem = stemRegistry.getValueOrThrow(stemKey);
                }
            }
            if (stem == null) {
                stem = resolveLevelStemFromPreset(this.registryAccess, stemKey).orElse(null);
            }
            if (stem == null) {
                if (Configs.DevMode) {
                    LOGGER.info("Datapack debug: missing LEVEL_STEM registry (available registries: {})",
                        this.registryAccess.listRegistryKeys().toList());
                }
                this.dimensionContexts.put(dimensionId, null);
                return null;
            }
            ChunkGenerator chunkGenerator = stem.generator();
            BiomeSource biomeSource = chunkGenerator.getBiomeSource();
            RandomState randomState = createRandomState(chunkGenerator, this.registryAccess, dimensionId, this.seed);
            Registry<StructureSet> structureSetRegistry = this.registryAccess.lookupOrThrow(Registries.STRUCTURE_SET);
            ChunkGeneratorStructureState structureState = chunkGenerator.createState(structureSetRegistry, randomState, this.seed);
            structureState.ensureStructuresGenerated();
            LevelHeightAccessor heightAccessor = LevelHeightAccessor.create(chunkGenerator.getMinY(), chunkGenerator.getGenDepth());
            DimensionContext context = new DimensionContext(dimensionId, chunkGenerator, biomeSource, randomState, structureState, heightAccessor);
            this.dimensionContexts.put(dimensionId, context);
            return context;
        }

        public StructureResult resolveStructure(CustomStructureSet set, DimensionContext context, ChunkPos chunkPos, WorldgenRandom selectionRandom) {
            String cacheKey = set.id() + ":" + context.dimensionId();
            Map<Long, StructureResult> cache = this.structureCache.computeIfAbsent(cacheKey, _ -> new HashMap<>());
            long chunkKey = chunkPos.toLong();
            StructureResult cached = cache.get(chunkKey);
            if (cached != null) {
                return cached;
            }
            StructureSetEntry entry = set.selectEntry(selectionRandom);
            if (entry == null) {
                cache.put(chunkKey, StructureResult.EMPTY);
                return StructureResult.EMPTY;
            }
            Structure structure = entry.structure().value();
            Predicate<Holder<Biome>> validBiome = structure.biomes()::contains;
            Structure.GenerationContext generationContext = new Structure.GenerationContext(
                this.registryAccess,
                context.chunkGenerator(),
                context.biomeSource(),
                context.randomState(),
                this.templateManager,
                this.seed,
                chunkPos,
                context.heightAccessor(),
                validBiome
            );
            Optional<Structure.GenerationStub> stub = structure.findValidGenerationPoint(generationContext);
            if (stub.isEmpty()) {
                cache.put(chunkKey, StructureResult.EMPTY);
                return StructureResult.EMPTY;
            }
            StructureResult result = new StructureResult(entry, stub.get().position());
            cache.put(chunkKey, result);
            return result;
        }

        @Override
        public void close() throws Exception {
            if (this.resourceManager != null) {
                this.resourceManager.close();
            }
            if (this.storageAccess != null) {
                this.storageAccess.close();
            }
        }

        private static StructureTemplateManager createTemplateManager(ResourceManager resourceManager, LevelStorageSource.LevelStorageAccess storageAccess) throws IOException {
            StructureTemplateManager manager = new StructureTemplateManager(
                resourceManager,
                storageAccess,
                DataFixers.getDataFixer(),
                BuiltInRegistries.BLOCK
            );
            manager.onResourceManagerReload(resourceManager);
            return manager;
        }

        private static LevelStorageSource.LevelStorageAccess createTempStorageAccess() throws IOException {
            Path root = Files.createTempDirectory("seedmapper-datapack");
            LevelStorageSource source = LevelStorageSource.createDefault(root);
            return source.createAccess("seedmapper");
        }

        private static Set<Identifier> loadVanillaStructureIds() throws IOException {
            PackResources vanilla = createVanillaPack();
            try (CloseableResourceManager manager = new MultiPackResourceManager(PackType.SERVER_DATA, List.of(vanilla))) {
                RegistryAccess.Frozen access = loadRegistryAccess(manager);
                Registry<Structure> structureRegistry = access.lookupOrThrow(Registries.STRUCTURE);
                return new HashSet<>(structureRegistry.keySet());
            }
        }

        private static List<CustomStructureSet> buildCustomStructureSets(RegistryAccess access, Set<Identifier> vanillaStructures) {
            Registry<StructureSet> structureSets = access.lookupOrThrow(Registries.STRUCTURE_SET);
            List<CustomStructureSet> result = new ArrayList<>();
            for (Map.Entry<ResourceKey<StructureSet>, StructureSet> entry : structureSets.entrySet()) {
                ResourceKey<StructureSet> key = entry.getKey();
                StructureSet set = entry.getValue();
                List<StructureSetEntry> entries = new ArrayList<>();
                boolean hasCustom = false;
                for (StructureSet.StructureSelectionEntry selection : set.structures()) {
                    Holder<Structure> holder = selection.structure();
                    Optional<ResourceKey<Structure>> structureKey = holder.unwrapKey();
                    if (structureKey.isEmpty()) {
                        continue;
                    }
                    Identifier id = structureKey.get().identifier();
                    boolean custom = !vanillaStructures.contains(id);
                    if (custom) {
                        hasCustom = true;
                    }
                    entries.add(new StructureSetEntry(id.toString(), holder, selection.weight(), custom));
                }
                if (!hasCustom || entries.isEmpty()) {
                    continue;
                }
                result.add(new CustomStructureSet(key, set, entries));
            }
            return result;
        }
    }

    public record DimensionContext(int dimensionId, ChunkGenerator chunkGenerator, BiomeSource biomeSource, RandomState randomState,
                                   ChunkGeneratorStructureState structureState, LevelHeightAccessor heightAccessor) {}

    public record StructureResult(StructureSetEntry entry, net.minecraft.core.BlockPos position) {
        static final StructureResult EMPTY = new StructureResult(null, null);

        public boolean isPresent() {
            return this.entry != null && this.position != null;
        }
    }

    public static final class CustomStructureSet {
        private final ResourceKey<StructureSet> key;
        private final StructureSet set;
        private final List<StructureSetEntry> entries;
        private final int totalWeight;

        private CustomStructureSet(ResourceKey<StructureSet> key, StructureSet set, List<StructureSetEntry> entries) {
            this.key = key;
            this.set = set;
            this.entries = Collections.unmodifiableList(entries);
            int sum = 0;
            for (StructureSetEntry entry : entries) {
                sum += entry.weight();
            }
            this.totalWeight = Math.max(sum, 0);
        }

        public String id() {
            return this.key.identifier().toString();
        }

        public StructurePlacement placement() {
            return this.set.placement();
        }

        public List<StructureSetEntry> entries() {
            return this.entries;
        }

        public StructureSetEntry selectEntry(WorldgenRandom random) {
            if (this.totalWeight <= 0) {
                return null;
            }
            int roll = random.nextInt(this.totalWeight);
            int accumulator = 0;
            for (StructureSetEntry entry : this.entries) {
                accumulator += entry.weight();
                if (roll < accumulator) {
                    return entry;
                }
            }
            return this.entries.get(this.entries.size() - 1);
        }

        public RandomSpreadCandidate sampleRandomSpread(long seed, int regionX, int regionZ) {
            if (!(this.set.placement() instanceof RandomSpreadStructurePlacement randomPlacement)) {
                return null;
            }
            int spacing = randomPlacement.spacing();
            ChunkPos chunkPos = randomPlacement.getPotentialStructureChunk(seed, regionX * spacing, regionZ * spacing);
            WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
            random.setLargeFeatureWithSalt(seed, regionX, regionZ, getPlacementSalt(randomPlacement));
            return new RandomSpreadCandidate(chunkPos, random);
        }
    }

    public record RandomSpreadCandidate(ChunkPos chunkPos, WorldgenRandom random) {}

    public record StructureSetEntry(String id, Holder<Structure> structure, int weight, boolean custom) {
        private static final int EMPTY_COLOR = 0xFF_FFFFFF;
        private static final double GOLDEN_RATIO = 0.618033988749895;
        private static final double MIN_COLOR_DISTANCE = 0.35;
        private static final int MAX_COLOR_TRIES = 64;
        private static final Map<Integer, Map<String, Integer>> SCHEME_COLOR_CACHE = new HashMap<>();
        private static final Map<Integer, java.util.List<Integer>> SCHEME_COLORS = new HashMap<>();

        private static int colorFromId(String idString) {
            int hash = idString.hashCode();
            int r = 64 + (hash & 0x9F);
            int g = 64 + ((hash >> 7) & 0x9F);
            int b = 64 + ((hash >> 14) & 0x9F);
            return 0xFF000000 | (r << 16) | (g << 8) | b;
        }

        private static int colorForScheme(String idString, int scheme) {
            synchronized (SCHEME_COLOR_CACHE) {
                Map<String, Integer> cache = SCHEME_COLOR_CACHE.computeIfAbsent(scheme, _ -> new HashMap<>());
                Integer cached = cache.get(idString);
                if (cached != null) {
                    return cached;
                }
                int hash = idString.hashCode();
                double baseHue = (Math.floorMod(hash, 100000) / 100000.0);
                double hue = schemeHue(baseHue, scheme, hash);
                double saturation = schemeSaturation(scheme);
                double value = schemeValue(scheme, hash);
                int color = pickDistinctColor(scheme, hue, saturation, value);
                cache.put(idString, color);
                return color;
            }
        }

        private static int pickDistinctColor(int scheme, double hue, double saturation, double value) {
            java.util.List<Integer> used = SCHEME_COLORS.computeIfAbsent(scheme, _ -> new java.util.ArrayList<>());
            int tries = 0;
            double currentHue = hue;
            while (tries < MAX_COLOR_TRIES) {
                int candidate = hsvToRgb(currentHue, saturation, value);
                if (isDistinct(candidate, used)) {
                    used.add(candidate);
                    return candidate;
                }
                currentHue = (currentHue + GOLDEN_RATIO) % 1.0;
                tries++;
            }
            int fallback = hsvToRgb((hue + 0.5) % 1.0, saturation, value);
            used.add(fallback);
            return fallback;
        }

        private static double schemeHue(double baseHue, int scheme, int hash) {
            return switch (scheme) {
                case 2 -> (baseHue + 0.50 + ((hash >> 5) & 0x7F) / 2048.0) % 1.0; // pastel shift
                case 3 -> (1.0 - baseHue + 0.15) % 1.0; // fluorescent contrast
                default -> baseHue; // primary/bold
            };
        }

        private static double schemeSaturation(int scheme) {
            return switch (scheme) {
                case 2 -> 0.40; // pastels
                case 3 -> 1.00; // fluorescents
                default -> 0.90; // primaries
            };
        }

        private static double schemeValue(int scheme, int hash) {
            return switch (scheme) {
                case 2 -> 0.92 + (((hash >> 6) & 0x7) / 200.0); // 0.92-0.955
                case 3 -> 0.98; // bright neon
                default -> 0.78 + (((hash >> 8) & 0x7) / 100.0); // 0.78-0.85
            };
        }

        private static boolean isDistinct(int candidate, java.util.List<Integer> used) {
            if (used.isEmpty()) {
                return true;
            }
            double cr = ((candidate >> 16) & 0xFF) / 255.0;
            double cg = ((candidate >> 8) & 0xFF) / 255.0;
            double cb = (candidate & 0xFF) / 255.0;
            for (int color : used) {
                double r = ((color >> 16) & 0xFF) / 255.0;
                double g = ((color >> 8) & 0xFF) / 255.0;
                double b = (color & 0xFF) / 255.0;
                double dist = Math.sqrt((cr - r) * (cr - r) + (cg - g) * (cg - g) + (cb - b) * (cb - b));
                if (dist < MIN_COLOR_DISTANCE) {
                    return false;
                }
            }
            return true;
        }

        private static int hsvToRgb(double hue, double saturation, double value) {
            double h = (hue % 1.0 + 1.0) % 1.0;
            double s = Math.clamp(saturation, 0.0, 1.0);
            double v = Math.clamp(value, 0.0, 1.0);
            double c = v * s;
            double x = c * (1.0 - Math.abs((h * 6.0) % 2.0 - 1.0));
            double m = v - c;
            double r1;
            double g1;
            double b1;
            double h6 = h * 6.0;
            if (h6 < 1.0) {
                r1 = c; g1 = x; b1 = 0.0;
            } else if (h6 < 2.0) {
                r1 = x; g1 = c; b1 = 0.0;
            } else if (h6 < 3.0) {
                r1 = 0.0; g1 = c; b1 = x;
            } else if (h6 < 4.0) {
                r1 = 0.0; g1 = x; b1 = c;
            } else if (h6 < 5.0) {
                r1 = x; g1 = 0.0; b1 = c;
            } else {
                r1 = c; g1 = 0.0; b1 = x;
            }
            int r = (int) Math.round((r1 + m) * 255.0);
            int g = (int) Math.round((g1 + m) * 255.0);
            int b = (int) Math.round((b1 + m) * 255.0);
            return 0xFF000000 | (r << 16) | (g << 8) | b;
        }

        private static String prettyName(String id) {
            if (id == null) {
                return "";
            }
            String value = id.contains(":") ? id.substring(id.indexOf(":") + 1) : id;
            value = value.replace('_', ' ').trim();
            if (value.isEmpty()) {
                return id;
            }
            return Character.toUpperCase(value.charAt(0)) + value.substring(1);
        }

        public Component tooltip() {
            return Component.literal(prettyName(this.id));
        }

        public int tint() {
            if (!this.custom) {
                return EMPTY_COLOR;
            }
            return switch (Configs.DatapackColorScheme) {
                case 2 -> colorForScheme(this.id, 2);
                case 3 -> colorForScheme(this.id, 3);
                default -> colorForScheme(this.id, 1);
            };
        }
    }

    public static void clearColorSchemeCache() {
        synchronized (DatapackStructureManager.StructureSetEntry.SCHEME_COLOR_CACHE) {
            DatapackStructureManager.StructureSetEntry.SCHEME_COLOR_CACHE.clear();
            DatapackStructureManager.StructureSetEntry.SCHEME_COLORS.clear();
        }
    }

    private static PackResources createPathPack(String id, Path path, PackSource source) {
        PackLocationInfo info = new PackLocationInfo(id, Component.literal(id), source, Optional.empty());
        return new PathPackResources(info, path);
    }

    private static PackResources createVanillaPack() {
        PackLocationInfo info = new PackLocationInfo("seedmapper_vanilla", Component.literal("Vanilla"), PackSource.BUILT_IN, Optional.empty());
        VanillaPackResourcesBuilder builder = new VanillaPackResourcesBuilder()
            .applyDevelopmentConfig()
            .setMetadata(BuiltInMetadata.of())
            .pushJarResources()
            .exposeNamespace("minecraft", "c");
        Path vanillaBase = Path.of("mc-datapack-map-main", "mc-datapack-map-main", "vanilla_datapack_base");
        if (Files.exists(vanillaBase)) {
            builder.pushUniversalPath(vanillaBase);
        }
        return builder.build(info);
    }

    private static RegistryAccess.Frozen loadRegistryAccess(ResourceManager resourceManager) {
        List<RegistryLoadAttempt> attempts = List.of(
            new RegistryLoadAttempt(
                "filtered_enchantment_and_provider",
                new FilteringResourceManager(resourceManager, DatapackStructureManager::isEnchantmentRelatedResource),
                DatapackStructureManager::isEnchantmentRelatedRegistry
            ),
            new RegistryLoadAttempt(
                "unfiltered",
                resourceManager,
                key -> true
            )
        );
        Exception lastError = null;
        for (RegistryLoadAttempt attempt : attempts) {
            try {
                LOGGER.info("Datapack registry load attempt: {}", attempt.name());
                return loadRegistryAccessInternal(attempt.resourceManager(), attempt.registryPredicate());
            } catch (Exception e) {
                lastError = e;
                LOGGER.warn("Datapack registry load attempt failed: {}", attempt.name(), e);
            }
        }
        if (lastError instanceof RuntimeException runtime) {
            throw runtime;
        }
        throw new RuntimeException(lastError);
    }

    private static RegistryAccess.Frozen loadRegistryAccessInternal(ResourceManager resourceManager, Predicate<ResourceKey<? extends Registry<?>>> registryFilter) {
        LayeredRegistryAccess<RegistryLayer> layered = RegistryLayer.createRegistryAccess();
        RegistryAccess.Frozen staticAccess = layered.getLayer(RegistryLayer.STATIC);
        List<HolderLookup.RegistryLookup<?>> staticLookups = staticAccess.listRegistries().collect(Collectors.toList());
        List<RegistryDataLoader.RegistryData<?>> worldgenRegistries = filterRegistryData(RegistryDataLoader.WORLDGEN_REGISTRIES, registryFilter);
        RegistryAccess.Frozen worldgen = RegistryDataLoader.load(resourceManager, staticLookups, worldgenRegistries);
        List<HolderLookup.RegistryLookup<?>> dimensionLookups = Stream.concat(staticLookups.stream(), worldgen.listRegistries()).collect(Collectors.toList());
        List<RegistryDataLoader.RegistryData<?>> dimensionRegistries = filterRegistryData(RegistryDataLoader.DIMENSION_REGISTRIES, registryFilter);
        RegistryAccess.Frozen dimensions = RegistryDataLoader.load(resourceManager, dimensionLookups, dimensionRegistries);
        Optional<Registry<LevelStem>> loadedStems = dimensions.lookup(Registries.LEVEL_STEM);
        if (loadedStems.isEmpty() || loadedStems.get().keySet().isEmpty()) {
            dimensions = layered.getLayer(RegistryLayer.DIMENSIONS);
        }
        RegistryAccess.Frozen composite = layered.replaceFrom(RegistryLayer.WORLDGEN, worldgen, dimensions).compositeAccess();
        try {
            List<Registry.PendingTags<?>> pendingTags = TagLoader.loadTagsForExistingRegistries(resourceManager, composite);
            for (Registry.PendingTags<?> pending : pendingTags) {
                pending.apply();
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to apply datapack tags", e);
        }
        return composite;
    }

    private static List<RegistryDataLoader.RegistryData<?>> filterRegistryData(
        List<RegistryDataLoader.RegistryData<?>> registries,
        Predicate<ResourceKey<? extends Registry<?>>> registryFilter
    ) {
        if (registries == null || registries.isEmpty()) {
            return List.of();
        }
        return registries.stream()
            .filter(data -> data != null && registryFilter.test(data.key()))
            .toList();
    }

    private static boolean isEnchantmentResource(Identifier id) {
        if (id == null) {
            return false;
        }
        String path = id.getPath();
        return path != null && path.startsWith("enchantment/");
    }

    private static boolean isEnchantmentRelatedResource(Identifier id) {
        if (id == null) {
            return false;
        }
        String path = id.getPath();
        if (path == null) {
            return false;
        }
        return path.startsWith("enchantment/")
            || path.startsWith("enchantment_provider/")
            || path.startsWith("tags/enchantments")
            || path.startsWith("tags/items/enchantable");
    }

    private static boolean isEnchantmentRelatedRegistry(ResourceKey<? extends Registry<?>> key) {
        if (key == null) {
            return false;
        }
        return !Registries.ENCHANTMENT.equals(key) && !Registries.ENCHANTMENT_PROVIDER.equals(key);
    }

    private record RegistryLoadAttempt(
        String name,
        ResourceManager resourceManager,
        Predicate<ResourceKey<? extends Registry<?>>> registryPredicate
    ) {}

    private static final class FilteringResourceManager implements ResourceManager {
        private final ResourceManager delegate;
        private final Predicate<Identifier> deny;

        private FilteringResourceManager(ResourceManager delegate, Predicate<Identifier> deny) {
            this.delegate = delegate;
            this.deny = deny;
        }

        @Override
        public Optional<Resource> getResource(Identifier id) {
            if (this.deny.test(id)) {
                return Optional.empty();
            }
            return this.delegate.getResource(id);
        }

        @Override
        public List<Resource> getResourceStack(Identifier id) {
            if (this.deny.test(id)) {
                return List.of();
            }
            return this.delegate.getResourceStack(id);
        }

        @Override
        public Map<Identifier, Resource> listResources(String path, Predicate<Identifier> filter) {
            return this.delegate.listResources(path, id -> !this.deny.test(id) && filter.test(id));
        }

        @Override
        public Map<Identifier, List<Resource>> listResourceStacks(String path, Predicate<Identifier> filter) {
            return this.delegate.listResourceStacks(path, id -> !this.deny.test(id) && filter.test(id));
        }

        @Override
        public Set<String> getNamespaces() {
            return this.delegate.getNamespaces();
        }

        @Override
        public java.util.stream.Stream<PackResources> listPacks() {
            return this.delegate.listPacks();
        }
    }

    private static ResourceKey<LevelStem> stemKeyForDimension(int dimensionId) {
        if (dimensionId == 0) {
            return LevelStem.OVERWORLD;
        }
        if (dimensionId == -1) {
            return LevelStem.NETHER;
        }
        if (dimensionId == 1) {
            return LevelStem.END;
        }
        if (dimensionId == Cubiomes.DIM_NETHER()) {
            return LevelStem.NETHER;
        }
        if (dimensionId == Cubiomes.DIM_END()) {
            return LevelStem.END;
        }
        if (dimensionId == Cubiomes.DIM_OVERWORLD()) {
            return LevelStem.OVERWORLD;
        }
        return null;
    }

    private static ResourceKey<NoiseGeneratorSettings> noiseSettingsForDimension(int dimensionId) {
        if (dimensionId == 0) {
            return NoiseGeneratorSettings.OVERWORLD;
        }
        if (dimensionId == -1) {
            return NoiseGeneratorSettings.NETHER;
        }
        if (dimensionId == 1) {
            return NoiseGeneratorSettings.END;
        }
        if (dimensionId == Cubiomes.DIM_NETHER()) {
            return NoiseGeneratorSettings.NETHER;
        }
        if (dimensionId == Cubiomes.DIM_END()) {
            return NoiseGeneratorSettings.END;
        }
        return NoiseGeneratorSettings.OVERWORLD;
    }

    private static RandomState createRandomState(ChunkGenerator chunkGenerator, RegistryAccess access, int dimensionId, long seed) {
        if (chunkGenerator instanceof net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator noiseGenerator) {
            Holder<NoiseGeneratorSettings> holder = noiseGenerator.generatorSettings();
            Optional<ResourceKey<NoiseGeneratorSettings>> key = holder.unwrapKey();
            if (key.isPresent()) {
                return RandomState.create(access, key.get(), seed);
            }
            HolderGetter<net.minecraft.world.level.levelgen.synth.NormalNoise.NoiseParameters> noiseParams = access.lookupOrThrow(Registries.NOISE);
            return RandomState.create(holder.value(), noiseParams, seed);
        }
        ResourceKey<NoiseGeneratorSettings> fallback = noiseSettingsForDimension(dimensionId);
        return RandomState.create(access, fallback, seed);
    }

    public static WorldgenRandom createSelectionRandom(long seed, int chunkX, int chunkZ, StructurePlacement placement) {
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
        random.setLargeFeatureWithSalt(seed, chunkX, chunkZ, getPlacementSalt(placement));
        return random;
    }

    private static Optional<Registry<LevelStem>> getClientLevelStemRegistry() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null) {
            return Optional.empty();
        }
        return minecraft.level.registryAccess().lookup(Registries.LEVEL_STEM);
    }

    private static Optional<LevelStem> resolveLevelStemFromPreset(RegistryAccess registryAccess, ResourceKey<LevelStem> stemKey) {
        Optional<Registry<WorldPreset>> presets = getRegistryFromDatapackOrClient(registryAccess, Registries.WORLD_PRESET);
        if (presets.isEmpty()) {
            return Optional.empty();
        }
        Identifier presetId = mapWorldPresetId(Configs.WorldPresetId);
        WorldPreset preset = presets.get().getValue(presetId);
        if (preset == null) {
            preset = presets.get().getValue(Identifier.withDefaultNamespace("normal"));
        }
        if (preset == null) {
            return Optional.empty();
        }
        WorldDimensions dimensions = preset.createWorldDimensions();
        if (Configs.DevMode) {
            LOGGER.info("Datapack debug: preset={} dimension keys={}", presetId, dimensions.dimensions().keySet());
        }
        return dimensions.get(stemKey);
    }

    private static Identifier mapWorldPresetId(String presetId) {
        if (presetId == null || presetId.isBlank()) {
            return Identifier.withDefaultNamespace("normal");
        }
        return switch (presetId) {
            case "default" -> Identifier.withDefaultNamespace("normal");
            case "superflat" -> Identifier.withDefaultNamespace("flat");
            case "large_biomes" -> Identifier.withDefaultNamespace("large_biomes");
            case "amplified" -> Identifier.withDefaultNamespace("amplified");
            case "single_biome_surface" -> Identifier.withDefaultNamespace("single_biome_surface");
            default -> {
                Identifier parsed = Identifier.tryParse(presetId);
                yield parsed != null ? parsed : Identifier.withDefaultNamespace("normal");
            }
        };
    }

    private static <T> Optional<Registry<T>> getRegistryFromDatapackOrClient(RegistryAccess registryAccess, ResourceKey<? extends Registry<T>> key) {
        Optional<Registry<T>> datapackRegistry = registryAccess.lookup(key);
        if (datapackRegistry.isPresent()) {
            return datapackRegistry;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.level != null) {
            Optional<Registry<T>> registry = minecraft.level.registryAccess().lookup(key);
            if (registry.isPresent()) {
                return registry;
            }
        }
        return Optional.empty();
    }

    private static int getPlacementSalt(StructurePlacement placement) {
        if (placement == null || STRUCTURE_PLACEMENT_SALT == null) {
            return 0;
        }
        try {
            return (int) STRUCTURE_PLACEMENT_SALT.invoke(placement);
        } catch (ReflectiveOperationException e) {
            LOGGER.warn("Failed to read structure placement salt", e);
            return 0;
        }
    }

    private static Method resolvePlacementSaltMethod() {
        try {
            Method method = StructurePlacement.class.getDeclaredMethod("salt");
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException e) {
            Method fallback = null;
            for (Method method : StructurePlacement.class.getDeclaredMethods()) {
                if (method.getParameterCount() == 0 && method.getReturnType() == int.class) {
                    fallback = method;
                    break;
                }
            }
            if (fallback != null) {
                try {
                    fallback.setAccessible(true);
                    return fallback;
                } catch (Exception ignored) {
                }
            }
            LOGGER.warn("Failed to resolve StructurePlacement.salt()", e);
            return null;
        }
    }
}
