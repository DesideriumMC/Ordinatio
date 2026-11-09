package dev.desiderium.ordinatio.config;

import dev.desiderium.ordinatio.config.ConfigIndex.RegistryId;
import dev.desiderium.ordinatio.mixin.ConfigCategoryAccessor;
import net.minecraftforge.common.ConfigCategory;
import net.minecraftforge.common.Configuration;
import net.minecraftforge.common.Property;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

public final class ConfigDocument {
    private final ConfigIndex.ConfigFile file;
    private final byte[] originalBytes;
    public final List<Value> values = new ArrayList<Value>();

    public ConfigDocument(ConfigIndex.ConfigFile file) throws IOException {
        this.file = file;
        originalBytes = Files.readAllBytes(file.file.toPath());
        Configuration config;
        Path temporary = Files.createTempFile("ordinatio-read-", ".cfg");
        try {
            Files.write(temporary, originalBytes);
            config = read(temporary);
        } finally {
            Files.deleteIfExists(temporary);
        }
        for (ConfigIndex.Entry entry : ConfigIndex.entriesFor(file)) {
            if (!config.hasKey(entry.category, entry.key)) continue;
            Property property = config.getCategory(entry.category).get(entry.key);
            values.add(new Value(entry, property));
        }
        if (values.isEmpty()) throw new IOException("No recognized properties in " + file.getName());
    }

    public boolean isDirty() {
        for (Value value : values) {
            if (value.isDirty()) return true;
        }
        return false;
    }

    public EnumSet<RegistryId.Kind> changedRegistryIdKinds() {
        EnumSet<RegistryId.Kind> kinds = EnumSet.noneOf(RegistryId.Kind.class);
        for (Value value : values) {
            if (value.registryId != null && value.isDirty()) kinds.add(value.registryId.kind);
        }
        return kinds;
    }

    public void save() throws IOException {
        if (!isDirty()) return;
        ConfigIndex.entriesFor(file);
        Path target = file.file.toPath();
        checkUnchanged(target);

        // Serialize a detached copy through Forge and verify its values before replacing the file.
        Path temporary = Files.createTempFile(target.getParent(), ".ordinatio-", ".cfg");
        try {
            Files.write(temporary, originalBytes);
            Configuration config = read(temporary);
            for (Value value : values) value.apply(config);
            BufferedWriter out = Files.newBufferedWriter(temporary, Charset.forName(config.defaultEncoding));
            try {
                out.write("# Configuration file");
                out.newLine();
                out.newLine();
                for (String name : config.getCategoryNames()) {
                    ConfigCategory category = config.getCategory(name);
                    if (!category.isChild()) {
                        category.write(out, 0);
                        out.newLine();
                    }
                }
            } finally {
                out.close();
            }
            verifyContents(config, read(temporary));
            checkUnchanged(target);
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private void checkUnchanged(Path target) throws IOException {
        if (!file.file.getCanonicalFile().equals(file.file)
                || !Arrays.equals(originalBytes, Files.readAllBytes(target))) {
            throw new IOException("The configuration changed on disk. Close it and open it again.");
        }
    }

    private static Configuration read(Path path) throws IOException {
        Configuration result = new Configuration();
        ConfigFileAccess access = (ConfigFileAccess) result;
        if (access.ordinatio$isGlobalConfig()) {
            throw new IOException("Forge global.cfg mode is active.");
        }
        access.ordinatio$setFile(path.toFile());
        try {
            result.load();
        } catch (RuntimeException exception) {
            throw new IOException("Forge could not parse the configuration.", exception);
        }
        if (!Files.exists(path)) throw new IOException("Forge could not read the configuration.");
        return result;
    }

    private static void verifyContents(Configuration config, Configuration written) throws IOException {
        if (!config.getCategoryNames().equals(written.getCategoryNames())) {
            throw new IOException("Forge could not preserve the configuration categories.");
        }
        for (String name : config.getCategoryNames()) {
            ConfigCategory before = config.getCategory(name);
            ConfigCategory after = written.getCategory(name);
            if (!before.keySet().equals(after.keySet())) {
                throw new IOException("Forge could not preserve the properties in " + name);
            }
            for (String key : before.keySet()) {
                Property expected = before.get(key);
                Property actual = after.get(key);
                if (expected.isList() != actual.isList() || expected.getType() != actual.getType()
                        || !Arrays.equals(readValue(expected), readValue(actual))) {
                    throw new IOException("Forge could not preserve the value of " + name + " / " + key);
                }
            }
        }
    }

    private static String[] readValue(Property property) {
        return property.isList() ? property.getStringList().clone() : new String[] { property.getString() };
    }

    static boolean isRepresentable(Property.Type type, boolean list, String value) {
        if (type == null || value == null || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) return false;
        if (list && (value.trim().isEmpty() || !value.equals(value.trim()) || value.indexOf('>') >= 0)) {
            return false;
        }
        try {
            switch (type) {
                case BOOLEAN:
                    return "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value);
                case INTEGER:
                    Integer.parseInt(value);
                    return true;
                case DOUBLE:
                    double number = Double.parseDouble(value);
                    return !Double.isNaN(number) && !Double.isInfinite(number);
                default:
                    return true;
            }
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    public static final class Value {
        public final String category;
        public final String key;
        public final String comment;
        public final Property.Type type;
        public final boolean list;
        public final boolean editable;
        public final RegistryId registryId;
        public final List<String> allowedValues;
        private final String[] initial;
        private final String[] defaults;
        private final String categoryComment;
        private String[] draft;

        private Value(ConfigIndex.Entry entry, Property property) {
            this.category = entry.category;
            this.key = entry.key;
            ConfigCategory source = entry.source.hasCategory(category) ? entry.source.getCategory(category) : null;
            Property sourceProperty = source == null ? null : source.get(key);
            this.comment = sourceProperty == null ? property.comment : sourceProperty.comment;
            this.categoryComment = source == null ? null : ((ConfigCategoryAccessor) source).ordinatio$getComment();
            this.type = entry.type;
            this.list = property.isList();
            this.editable = type != null && type == property.getType();
            this.registryId = entry.registryId;
            this.initial = readValue(property);
            this.draft = initial.clone();
            this.defaults = entry.defaults;
            List<String> options = new ArrayList<String>();
            for (String option : entry.allowedValues()) {
                if (isRepresentable(option)) options.add(option);
            }
            this.allowedValues = Collections.unmodifiableList(options);
        }

        public String[] get() {
            return draft.clone();
        }

        private boolean isDirty() {
            return !Arrays.equals(initial, draft);
        }

        public boolean hasDefault() {
            if (!editable || defaults == null || !list && defaults.length != 1) return false;
            for (String value : defaults) {
                if (!isRepresentable(value)) return false;
            }
            return true;
        }

        public void restoreDefault() {
            if (hasDefault()) draft = defaults.clone();
        }

        public boolean hasUnlistedDefault() {
            if (!hasDefault() || Arrays.equals(defaults, draft)) return false;
            for (String value : defaults) {
                if (isUnlisted(value)) return true;
            }
            return false;
        }

        public boolean isUnlisted(String value) {
            return !allowedValues.isEmpty() && !allowedValues.contains(value);
        }

        public boolean isRepresentable(String value) {
            return ConfigDocument.isRepresentable(type, list, value);
        }

        public boolean isValid(String value) {
            return isRepresentable(value) && !isUnlisted(value);
        }

        public void set(String[] values) {
            for (String value : values) {
                if (isUnlisted(value)) throw new IllegalArgumentException(value);
            }
            setAllowingUnlisted(values);
        }

        public void setAllowingUnlisted(String[] values) {
            if (!editable || !list && values.length != 1) throw new IllegalArgumentException(key);
            for (String value : values) {
                if (!isRepresentable(value)) throw new IllegalArgumentException(value);
            }
            draft = values.clone();
        }

        private void apply(Configuration config) {
            ConfigCategory group = config.getCategory(category);
            group.setComment(categoryComment);
            Property property = group.get(key);
            property.comment = comment;
            if (!isDirty()) return;
            if (list) property.set(draft.clone());
            else property.set(draft[0]);
        }
    }
}
