package dev.robothanzo.werewolf.discord

/** The audio cues played into the court voice channel (FEATURES §10.7), mapped to bundled assets. */
enum class SoundCue(val resource: String) {
    EXPEL_POLL_START("sounds/expel_poll.mp3"),
    POLICE_ENROLL_START("sounds/police_enroll.mp3"),
    POLICE_VOTE_START("sounds/police_poll.mp3"),
    ENROLL_TEN_SECONDS("sounds/enroll_10s_remaining.mp3"),
    POLL_TEN_SECONDS("sounds/poll_10s_remaining.mp3"),
    TIMER_THIRTY_SECONDS("sounds/timer_30s_remaining.mp3"),
    TIMER_ENDED("sounds/timer_ended.mp3"),
    NIGHT("sounds/night.mp3"),
    MORNING("sounds/morning.mp3"),
}
