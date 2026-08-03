package com.pwb.iam.testsupport;

import com.pwb.iam.domain.service.EmailDeliveryPort;
import com.pwb.iam.domain.service.EmailEnqueueCommand;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class InMemoryEmailDeliveryAdapter implements EmailDeliveryPort {

    private final List<EmailEnqueueCommand> sent = new ArrayList<>();

    @Override
    public void enqueue(EmailEnqueueCommand command) {
        sent.add(command);
    }

    public List<EmailEnqueueCommand> sentEmails() {
        return Collections.unmodifiableList(sent);
    }

    public void clear() {
        sent.clear();
    }
}
