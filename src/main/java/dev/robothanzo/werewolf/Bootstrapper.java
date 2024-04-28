package dev.robothanzo.werewolf;

import io.github.slimjar.app.builder.ApplicationBuilder;
import io.github.slimjar.logging.ProcessLogger;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.text.MessageFormat;
import java.util.logging.Logger;

public class Bootstrapper {
    public static void main(String[] args) throws ReflectiveOperationException, URISyntaxException, NoSuchAlgorithmException, IOException {
        ApplicationBuilder.appending("WerewolfHelper")
                .logger(new ProcessLogger() {
                    @Override
                    public void log(String s, Object... objects) {
                        System.out.println(new MessageFormat(s).format(objects));
                    }

                    @Override
                    public void debug(String message, Object... args) {
                        Logger.getGlobal().fine(new MessageFormat(message).format(args));
                    }
                })
                .downloadDirectoryPath(Path.of("libs")).build();
        WerewolfHelper.main(args);
    }
}
