package com.pwb.iam.domain.service;

public interface EmailDeliveryPort {

    void enqueue(EmailEnqueueCommand command);
}
