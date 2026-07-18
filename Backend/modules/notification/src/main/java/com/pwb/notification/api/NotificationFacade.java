package com.pwb.notification.api;

import com.pwb.notification.api.event.EmailRequestedIntegrationEvent;

public interface NotificationFacade {

    void consumeEmailRequest(EmailRequestedIntegrationEvent event);
}