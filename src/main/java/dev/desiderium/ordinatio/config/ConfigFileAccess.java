package dev.desiderium.ordinatio.config;

import java.io.File;

public interface ConfigFileAccess {
    void ordinatio$setFile(File file);

    boolean ordinatio$isGlobalConfig();
}
