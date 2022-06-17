package dev.robothanzo.werewolf.commands;

import dev.robothanzo.jda.interactions.annotations.slash.options.IMapper;
import dev.robothanzo.jda.interactions.annotations.slash.options.Mapper;

import java.time.Duration;
import java.util.Locale;

@Mapper
public class DurationMapper implements IMapper<String, Duration> {
    @Override
    public Class<String> getSourceType() {
        return String.class;
    }

    @Override
    public Class<Duration> getTargetType() {
        return Duration.class;
    }

    @Override
    public Duration map(Object source) {
        String strDuration = source.toString().replaceAll("\\s+", "").replaceFirst("(\\d+d)", "P$1T");
        strDuration = strDuration.charAt(0) != 'P' ? "PT" + strDuration.replace("min", "m")
                : strDuration.replace("min", "m");
        return Duration.parse(strDuration.toUpperCase(Locale.ROOT));
    }
}
