package dev.robothanzo.werewolf.commands

import dev.robothanzo.jda.interactions.annotations.slash.options.IMapper
import dev.robothanzo.jda.interactions.annotations.slash.options.Mapper
import java.time.Duration
import java.util.*

@Mapper
class DurationMapper : IMapper<String, Duration> {
    override fun getSourceType(): Class<String> {
        return String::class.java
    }

    override fun getTargetType(): Class<Duration> {
        return Duration::class.java
    }

    override fun map(source: Any): Duration {
        var strDuration = source.toString().replace("\\s+".toRegex(), "").replaceFirst("(\\d+d)".toRegex(), "P$1T")
        strDuration = if (strDuration[0] != 'P') "PT" + strDuration.replace("min", "m")
        else strDuration.replace("min", "m")
        return Duration.parse(strDuration.uppercase(Locale.ROOT))
    }
}
