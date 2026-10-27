package dev.simlab.server.room;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class RoomConfiguration {

    /** Runs the tick loops and timeouts of all rooms, cancelled tasks leave its queue right away */
    @Bean(destroyMethod = "shutdownNow")
    ScheduledThreadPoolExecutor roomScheduler() {
        var scheduler = new ScheduledThreadPoolExecutor(
                Runtime.getRuntime().availableProcessors(),
                Thread.ofPlatform().name("room-", 0).factory());
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }
}
