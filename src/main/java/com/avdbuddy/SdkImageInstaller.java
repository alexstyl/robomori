package com.avdbuddy;

import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.net.URLClassLoader;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/** Runs the installed SDK's installer with an already downloaded image archive. */
public final class SdkImageInstaller {
    public static void main(String[] args) throws Exception {
        Path sdk = Path.of(args[0]);
        Path archive = Path.of(args[3]);
        try (var loader = new URLClassLoader(new java.net.URL[]{Path.of(args[4]).toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
            Thread.currentThread().setContextClassLoader(loader);
            Class<?> settingsType = loader.loadClass("com.android.sdklib.tool.sdkmanager.SdkManagerCliSettings");
            var createSettings = settingsType.getMethod("createSettings", List.class, java.nio.file.FileSystem.class);
            createSettings.setAccessible(true);
            Object settings = createSettings.invoke(null, List.of("--install", args[1], "--sdk_root=" + sdk), FileSystems.getDefault());
            Class<?> controllerType = loader.loadClass("com.android.repository.api.SettingsController");
            Class<?> downloaderType = loader.loadClass("com.android.repository.api.Downloader");
            Object delegate = loader.loadClass("com.android.sdklib.repository.legacy.LegacyDownloader")
                    .getConstructor(controllerType).newInstance(settings);
            Object downloader = Proxy.newProxyInstance(loader, new Class<?>[]{downloaderType}, (proxy, method, values) -> {
                if (values != null && values.length > 0 && values[0] instanceof java.net.URL url && url.toString().equals(args[2])) {
                    if (method.getName().equals("downloadAndStream")) return Files.newInputStream(archive);
                    if (method.getName().startsWith("downloadFully")) {
                        if (values.length == 2) return archive;
                        Path destination = (Path) values[1];
                        if (values[2] != null) {
                            Object checksum = values[2];
                            String algorithm = (String) checksum.getClass().getMethod("getType").invoke(checksum);
                            String expected = (String) checksum.getClass().getMethod("getValue").invoke(checksum);
                            var digest = java.security.MessageDigest.getInstance(algorithm);
                            try (var input = new java.security.DigestInputStream(Files.newInputStream(archive), digest)) {
                                input.transferTo(java.io.OutputStream.nullOutputStream());
                            }
                            String actual = java.util.HexFormat.of().formatHex(digest.digest());
                            if (!actual.equalsIgnoreCase(expected)) throw new java.io.IOException("The Android image changed. Download it again.");
                        }
                        Files.createDirectories(destination.getParent());
                        // The SDK owns this file from here, including cleanup on failure/cancellation.
                        Files.move(archive, destination, StandardCopyOption.REPLACE_EXISTING);
                        return null;
                    }
                }
                try { return method.invoke(delegate, values); }
                catch (InvocationTargetException e) { throw e.getCause(); }
            });
            Class<?> locationsType = loader.loadClass("com.android.prefs.AndroidLocationsProvider");
            Object locations = loader.loadClass("com.android.prefs.AndroidLocationsSingleton").getField("INSTANCE").get(null);
            Class<?> handlerType = loader.loadClass("com.android.sdklib.repository.AndroidSdkHandler");
            Object handler = handlerType.getMethod("getInstance", locationsType, Path.class).invoke(null, locations, sdk);
            Class<?> cliType = loader.loadClass("com.android.sdklib.tool.sdkmanager.SdkManagerCli");
            Object cli = cliType.getConstructor(settingsType, java.io.PrintStream.class, InputStream.class, downloaderType, handlerType)
                    .newInstance(settings, System.out, System.in, downloader, handler);
            Class<?> progressType = loader.loadClass("com.android.repository.api.ProgressIndicator");
            var getProgress = settingsType.getMethod("getProgressIndicator");
            getProgress.setAccessible(true);
            Object progress = getProgress.invoke(settings);
            var run = cliType.getDeclaredMethod("run", progressType);
            run.setAccessible(true);
            try { run.invoke(cli, progress); }
            catch (InvocationTargetException e) { throw new IllegalStateException("Could not install the Android image", e.getCause()); }
        }
    }
}
