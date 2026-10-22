package com.trustvault.audit;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/activity")
public class AdminActivityController {
    private final AdminActivityService activity;

    public AdminActivityController(AdminActivityService activity) {
        this.activity = activity;
    }

    @GetMapping
    List<AdminActivityService.ActivityEntry> latest(@RequestParam(defaultValue = "50") int limit) {
        return activity.latest(limit);
    }
}
