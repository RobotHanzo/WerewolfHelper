package dev.robothanzo.werewolf;

import io.github.slimjar.app.builder.ApplicationBuilder;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;

public class Bootstrapper {
    public static void main(String[] args) throws ReflectiveOperationException, URISyntaxException, NoSuchAlgorithmException, IOException {
        ApplicationBuilder.appending("WerewolfHelper")
                .downloadDirectoryPath(Path.of("libs")).build();
        WerewolfHelper.main(args);
    }
}
