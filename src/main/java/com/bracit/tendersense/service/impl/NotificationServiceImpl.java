package com.bracit.tendersense.service.impl;

import com.bracit.tendersense.dto.NotificationResponse;
import com.bracit.tendersense.dto.PageResponse;
import com.bracit.tendersense.entity.MatchResult;
import com.bracit.tendersense.entity.Notification;
import com.bracit.tendersense.entity.Organisation;
import com.bracit.tendersense.entity.enums.MatchGrade;
import com.bracit.tendersense.entity.enums.MatcherType;
import com.bracit.tendersense.repository.MatchResultRepository;
import com.bracit.tendersense.repository.NotificationRepository;
import com.bracit.tendersense.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    /** Only a strong fit is worth an alert; B/C grades stay on the shortlist, not the bell. */
    private static final List<MatchGrade> NOTIFIABLE_GRADES = List.of(MatchGrade.S, MatchGrade.A);

    private final NotificationRepository notificationRepository;
    private final MatchResultRepository matchResultRepository;

    @Override
    @Transactional
    public void syncNewMatches(Organisation organisation) {
        List<MatchResult> fresh = matchResultRepository.findUnnotified(
                MatcherType.EMBEDDING, organisation.getId(), NOTIFIABLE_GRADES, LocalDateTime.now());
        if (fresh.isEmpty()) {
            return;
        }

        Instant now = Instant.now();
        List<Notification> batch = fresh.stream()
                .map(m -> Notification.builder()
                        .organisation(organisation)
                        .tender(m.getTender())
                        .tenderTitle(m.getTender().getTitle())
                        .procuringEntity(m.getTender().getProcuringEntity())
                        .closingAt(m.getTender().getClosingAt())
                        .grade(m.getGrade())
                        .score(m.getScore())
                        .readFlag(false)
                        .createdAt(now)
                        .build())
                .toList();
        notificationRepository.saveAll(batch);
        log.info("{}: {} new match notification(s)", organisation.getSlug(), batch.size());
    }

    @Override
    public PageResponse<NotificationResponse> list(
            Organisation organisation, boolean unreadOnly, Pageable pageable) {
        Page<Notification> page = unreadOnly
                ? notificationRepository.findByOrganisationIdAndReadFlagFalseOrderByCreatedAtDesc(
                        organisation.getId(), pageable)
                : notificationRepository.findByOrganisationIdOrderByCreatedAtDesc(
                        organisation.getId(), pageable);
        return PageResponse.of(page, this::toDto);
    }

    @Override
    public long unreadCount(Organisation organisation) {
        return notificationRepository.countByOrganisationIdAndReadFlagFalse(organisation.getId());
    }

    @Override
    @Transactional
    public void markRead(Organisation organisation, Long id) {
        notificationRepository.findByIdAndOrganisationId(id, organisation.getId())
                .filter(n -> !n.isReadFlag())
                .ifPresent(n -> {
                    n.setReadFlag(true);
                    notificationRepository.save(n);
                });
    }

    @Override
    @Transactional
    public int markAllRead(Organisation organisation) {
        return notificationRepository.markAllRead(organisation.getId());
    }

    /** {@code n.getTender().getId()} reads a lazy proxy's id, which needs no open session. */
    private NotificationResponse toDto(Notification n) {
        return new NotificationResponse(
                n.getId(), n.getTender().getId(), n.getTenderTitle(), n.getProcuringEntity(),
                n.getGrade(), n.getScore(), n.getClosingAt(), n.isReadFlag(), n.getCreatedAt());
    }
}
