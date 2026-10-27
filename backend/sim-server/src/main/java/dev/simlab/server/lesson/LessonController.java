package dev.simlab.server.lesson;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
class LessonController {

    private final LessonService lessons;

    LessonController(LessonService lessons) {
        this.lessons = lessons;
    }

    @GetMapping("/api/simulations/{type}/lesson")
    Lesson get(@PathVariable String type) {
        return lessons.find(type)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "There is no simulation of type '%s'".formatted(type)));
    }
}
