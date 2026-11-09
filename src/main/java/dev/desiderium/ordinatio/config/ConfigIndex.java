package dev.desiderium.ordinatio.config;

import net.minecraftforge.common.Configuration;
import net.minecraftforge.common.Property;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ConfigIndex {
    private static final Pattern ID_WORD = Pattern.compile("(^|[^a-z0-9])ids?($|[^a-z0-9])",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ID_SUFFIX = Pattern.compile("[a-z](ID|Id|IDs|Ids)$");
    private static final Pattern ID_KIND_IN_KEY = Pattern.compile(
            "(block|item|biome|dimension)[_ -]*ids?($|[^a-z0-9])", Pattern.CASE_INSENSITIVE);
    private static final Map<Configuration, ConfigFile> INSTANCES =
            new WeakHashMap<Configuration, ConfigFile>();
    private static final Map<Property, Options> PROPERTIES = new WeakHashMap<Property, Options>();
    private static final Map<File, ConfigFile> FILES = new LinkedHashMap<File, ConfigFile>();

    private ConfigIndex() {
    }

    public static synchronized void track(String modId, File configDir, File file, Configuration instance) {
        try {
            File path = file.getCanonicalFile();
            File root = configDir.getCanonicalFile();
            if (path.equals(root) || !path.toPath().startsWith(root.toPath())) return;

            ConfigFile config = FILES.get(path);
            if (config == null) {
                String relativePath = root.toPath().relativize(path.toPath()).toString()
                        .replace(File.separatorChar, '/');
                config = new ConfigFile(modId, path, relativePath);
                FILES.put(path, config);
            } else if (!config.modId.equals(modId)) {
                config.shared = true;
            }
            INSTANCES.put(instance, config);
        } catch (IOException ignored) {
            // An unresolved path cannot be attributed safely.
        }
    }

    public static synchronized void trackEntry(Configuration instance, String category, String key,
                                               String[] defaults, Property.Type type, Property property) {
        ConfigFile config = INSTANCES.get(instance);
        if (config == null) return;
        Map<String, Entry> entries = config.category(category);
        Entry previous = entries.get(key);
        String[] recordedDefaults = previous != null && previous.defaults != null && previous.type == type
                ? previous.defaults : defaults;
        RegistryId registryId = previous != null && previous.registryId != null
                ? previous.registryId : inferRegistryId(category, key, type);
        Options options = previous == null ? new Options() : previous.options;
        entries.put(key, new Entry(instance, category, key, recordedDefaults, type, registryId, options));
        if (property != null) PROPERTIES.put(property, options);
    }

    public static synchronized void setAllowedValues(Property property, String[] values) {
        if (property == null || values == null || values.length == 0) {
            throw new IllegalArgumentException("A property and at least one allowed value are required.");
        }
        Set<String> ordered = new LinkedHashSet<String>();
        for (String value : values) {
            if (!ConfigDocument.isRepresentable(property.getType(), property.isList(), value)) {
                throw new IllegalArgumentException("Invalid allowed value for " + property.getName() + ": " + value);
            }
            ordered.add(value);
        }
        Options options = PROPERTIES.get(property);
        if (options != null) options.values = Collections.unmodifiableList(new ArrayList<String>(ordered));
    }

    public static synchronized void markRegistryId(Configuration instance, String category, String key,
                                                   int defaultId, RegistryId registryId) {
        ConfigFile config = INSTANCES.get(instance);
        if (config == null) return;
        Entry entry = config.category(category).get(key);
        if (entry != null) {
            entry.registryId = registryId;
            // The Forge allocator's nested get call uses -1 as its missing-value marker.
            entry.defaults = new String[] { Integer.toString(defaultId) };
        }
    }

    public static synchronized List<ConfigFile> forMod(String modId) {
        List<ConfigFile> files = new ArrayList<ConfigFile>();
        for (ConfigFile config : FILES.values()) {
            if (config.modId.equals(modId) && !config.shared && !config.categories.isEmpty()
                    && config.file.isFile()) {
                files.add(config);
            }
        }
        return files;
    }

    static synchronized List<Entry> entriesFor(ConfigFile file) throws IOException {
        if (file.shared) throw new IOException("Multiple mods use " + file.file.getName());
        List<Entry> entries = new ArrayList<Entry>();
        for (Map<String, Entry> category : file.categories.values()) {
            entries.addAll(category.values());
        }
        return entries;
    }

    private static RegistryId inferRegistryId(String category, String key, Property.Type type) {
        boolean idKey = ID_WORD.matcher(key).find() || ID_SUFFIX.matcher(key).find();
        if (idKey) {
            Matcher matcher = ID_KIND_IN_KEY.matcher(key);
            if (matcher.find()) {
                return new RegistryId(RegistryId.Kind.fromName(matcher.group(1)), RegistryId.Source.KEY);
            }
        }
        if (type == Property.Type.INTEGER) {
            String root = category.toLowerCase(Locale.ENGLISH).split("\\.", 2)[0];
            RegistryId.Kind kind = RegistryId.Kind.fromName(root.equals("dimensions") ? "dimension" : root);
            if (kind != RegistryId.Kind.UNKNOWN) return new RegistryId(kind, RegistryId.Source.CATEGORY);
        }
        return idKey ? new RegistryId(RegistryId.Kind.UNKNOWN, RegistryId.Source.KEY) : null;
    }

    public static final class RegistryId {
        public static final RegistryId FORGE_BLOCK = new RegistryId(Kind.BLOCK, Source.FORGE_BLOCK);
        public static final RegistryId FORGE_ITEM = new RegistryId(Kind.ITEM, Source.FORGE_ITEM);

        public final Kind kind;
        public final Source source;

        private RegistryId(Kind kind, Source source) {
            this.kind = kind;
            this.source = source;
        }

        public enum Kind {
            BLOCK, ITEM, BIOME, DIMENSION, UNKNOWN;

            private static Kind fromName(String name) {
                for (Kind kind : values()) {
                    if (kind.name().equalsIgnoreCase(name)) return kind;
                }
                return UNKNOWN;
            }
        }

        public enum Source {
            FORGE_BLOCK, FORGE_ITEM, CATEGORY, KEY
        }
    }

    public static final class ConfigFile {
        private final String modId;
        private final String relativePath;
        final File file;
        private final Map<String, Map<String, Entry>> categories =
                new LinkedHashMap<String, Map<String, Entry>>();
        private boolean shared;

        private ConfigFile(String modId, File file, String relativePath) {
            this.modId = modId;
            this.file = file;
            this.relativePath = relativePath;
        }

        public String getModId() {
            return modId;
        }

        public String getRelativePath() {
            return relativePath;
        }

        public String getName() {
            return file.getName();
        }

        public String getPath() {
            return file.getPath();
        }

        private Map<String, Entry> category(String name) {
            Map<String, Entry> entries = categories.get(name);
            if (entries == null) {
                entries = new LinkedHashMap<String, Entry>();
                categories.put(name, entries);
            }
            return entries;
        }
    }

    private static final class Options {
        volatile List<String> values = Collections.emptyList();
    }

    static final class Entry {
        final Configuration source;
        final String category;
        final String key;
        String[] defaults;
        final Property.Type type;
        RegistryId registryId;
        private final Options options;

        private Entry(Configuration source, String category, String key, String[] defaults,
                      Property.Type type, RegistryId registryId, Options options) {
            this.source = source;
            this.category = category;
            this.key = key;
            this.defaults = defaults == null ? null : defaults.clone();
            this.type = type;
            this.registryId = registryId;
            this.options = options;
        }

        List<String> allowedValues() {
            return options.values;
        }
    }
}
