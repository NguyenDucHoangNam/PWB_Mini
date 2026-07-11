package com.pwb.backend.audio.internal.model;

import com.pwb.backend.shared.messaging.outbox.model.OutboxEvent;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "outbox_events")
public class AudioOutboxEvent extends OutboxEvent {
}