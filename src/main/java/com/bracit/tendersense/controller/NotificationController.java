package com.bracit.tendersense.controller;

import com.bracit.tendersense.config.CurrentOrganisation;
import com.bracit.tendersense.dto.NotificationResponse;
import com.bracit.tendersense.dto.PageResponse;
import com.bracit.tendersense.entity.Organisation;
import com.bracit.tendersense.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** The bell in the top bar: what newly matched, and what the company has seen. */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private static final int MAX_PAGE_SIZE = 50;

    private final NotificationService notificationService;

    @GetMapping
    public PageResponse<NotificationResponse> list(
            @CurrentOrganisation Organisation organisation,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "8") int size) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        return notificationService.list(organisation, unreadOnly, pageable);
    }

    /** Polled by the bell to keep the red count live without opening the panel. */
    @GetMapping("/unread-count")
    public long unreadCount(@CurrentOrganisation Organisation organisation) {
        return notificationService.unreadCount(organisation);
    }

    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@CurrentOrganisation Organisation organisation, @PathVariable Long id) {
        notificationService.markRead(organisation, id);
    }

    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllRead(@CurrentOrganisation Organisation organisation) {
        notificationService.markAllRead(organisation);
    }
}
