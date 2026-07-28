package com.pwb.iam.domain.event;

public interface AuthEventPublisher {

    void publishAuthSuccess(AuthSuccessEvent event);

    void publishOtpIssued(OtpIssuedDomainEvent event);

    void publishOtpVerified(OtpVerifiedDomainEvent event);
}
