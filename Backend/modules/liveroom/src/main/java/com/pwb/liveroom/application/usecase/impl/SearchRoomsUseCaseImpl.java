package com.pwb.liveroom.application.usecase.impl;

import com.pwb.liveroom.application.support.RoomViewFactory;
import com.pwb.liveroom.application.usecase.SearchRoomsUseCase;
import com.pwb.liveroom.application.view.RoomView;
import com.pwb.liveroom.domain.model.LiveRoom;
import com.pwb.liveroom.domain.repository.LiveRoomRepository;
import com.pwb.liveroom.domain.repository.RoomSearchCriteria;
import com.pwb.liveroom.domain.service.RoomSearchHits;
import com.pwb.liveroom.domain.service.RoomSearchPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchRoomsUseCaseImpl implements SearchRoomsUseCase {

    private final RoomSearchPort roomSearchPort;
    private final LiveRoomRepository liveRoomRepository;
    private final RoomViewFactory roomViewFactory;

    @Override
    @Transactional(readOnly = true)
    public Page<RoomView> execute(RoomSearchCriteria criteria, Pageable pageable) {
        Optional<RoomSearchHits> hits = roomSearchPort.search(
                criteria, (int) pageable.getOffset(), pageable.getPageSize());

        if (hits.isEmpty()) {
            log.debug("SEARCH.rooms fallback to database: ownerId={}", criteria.ownerId());
            Page<LiveRoom> page = liveRoomRepository.search(criteria, pageable);
            return page.map(roomViewFactory::toView);
        }
        return toPage(hits.get(), pageable);
    }

    private Page<RoomView> toPage(RoomSearchHits hits, Pageable pageable) {
        Map<UUID, LiveRoom> byId = liveRoomRepository.findAllByIdIn(hits.ids()).stream()
                .collect(Collectors.toMap(LiveRoom::getId, Function.identity()));

        List<RoomView> ordered = hits.ids().stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .map(roomViewFactory::toView)
                .toList();

        return new PageImpl<>(ordered, pageable, hits.total());
    }
}