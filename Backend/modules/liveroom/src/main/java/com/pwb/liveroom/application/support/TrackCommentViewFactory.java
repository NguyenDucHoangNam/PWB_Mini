package com.pwb.liveroom.application.support;

import com.pwb.liveroom.application.view.TrackCommentView;
import com.pwb.liveroom.domain.model.TrackComment;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TrackCommentViewFactory {

    public TrackCommentView toView(TrackComment comment) {
        return new TrackCommentView(
                comment.getId(),
                comment.getSongId(),
                comment.getUserId(),
                comment.getUserEmail(),
                comment.getContent(),
                comment.getPositionSeconds(),
                comment.getCreatedAt()
        );
    }

    public List<TrackCommentView> toViews(List<TrackComment> comments) {
        return comments.stream().map(this::toView).toList();
    }
}