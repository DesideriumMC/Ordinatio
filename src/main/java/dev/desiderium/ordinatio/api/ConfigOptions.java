package dev.desiderium.ordinatio.api;

import dev.desiderium.ordinatio.config.ConfigIndex;
import net.minecraftforge.common.Property;

/** Supplies the choices displayed by Ordinatio for Forge configuration properties. */
public final class ConfigOptions {
    private ConfigOptions() {
    }

    /**
     * Sets the allowed serialized values in display order and returns the same property.
     * For a list property, these choices apply to each item. Call this with the property returned
     * by Forge during configuration initialization. A later registration replaces the choices.
     *
     * @throws IllegalArgumentException if no choices are supplied or a choice is invalid for the property
     */
    public static Property setAllowedValues(Property property, String... values) {
        ConfigIndex.setAllowedValues(property, values);
        return property;
    }

    /** Uses the enum constants' names in declaration order as the serialized choices. */
    public static Property setEnumValues(Property property, Class<? extends Enum<?>> enumType) {
        if (enumType == null) throw new IllegalArgumentException("An enum class is required.");
        Enum<?>[] constants = enumType.getEnumConstants();
        if (constants == null) throw new IllegalArgumentException("An enum class is required.");
        String[] values = new String[constants.length];
        for (int index = 0; index < constants.length; index++) {
            values[index] = constants[index].name();
        }
        return setAllowedValues(property, values);
    }
}
