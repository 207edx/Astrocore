# AstroBackport for Minecraft 1.16.1

This folder contains the AstroBackport source archive for Minecraft 1.16.1 / Forge 32.0.108.

## Automatic build

Every push or pull request that changes this folder or its workflow starts a GitHub Actions build. The workflow uses Temurin Java 8 and Gradle 4.10.3, compiles the mod, and uploads the JAR as an Actions artifact.

1. Open the **Actions** tab.
2. Select **Build AstroBackport 1.16.1**.
3. Open the latest successful run.
4. Download the `AstroBackport-1.16.1-...` artifact and extract the JAR.

No Java, Gradle, or source setup is needed on your PC.

## Source archive

`source.tar.gz.b64` is the gzip-compressed source tree encoded as Base64 so it can be stored through GitHub's text-file interface. The workflow decodes and extracts it before compiling.

The archive includes the source, ForgeGradle build files, resources, and editable texture-pack files. It deliberately excludes the optional prebuilt client helper JAR.
