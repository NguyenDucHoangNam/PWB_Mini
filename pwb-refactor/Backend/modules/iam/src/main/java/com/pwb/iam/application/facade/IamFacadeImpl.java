package com.pwb.iam.application.facade;

import com.pwb.iam.api.dto.request.CompleteProfileRequest;
import com.pwb.iam.api.dto.request.RegisterRequest;
import com.pwb.iam.api.dto.request.ResendOtpRequest;
import com.pwb.iam.api.dto.request.VerifyOtpRequest;
import com.pwb.iam.api.dto.response.AuthMessageResponse;
import com.pwb.iam.api.dto.response.AuthResponse;
import com.pwb.iam.application.command.CompleteProfileCommand;
import com.pwb.iam.application.command.RegisterCommand;
import com.pwb.iam.application.command.ResendOtpCommand;
import com.pwb.iam.application.command.VerifyOtpCommand;
import com.pwb.iam.application.usecase.CompleteProfileUseCase;
import com.pwb.iam.application.usecase.RegisterUseCase;
import com.pwb.iam.application.usecase.ResendOtpUseCase;
import com.pwb.iam.application.usecase.VerifyOtpUseCase;
import com.pwb.iam.domain.event.AuthSuccessEvent;
import com.pwb.iam.domain.event.AuthEventPublisher;
import com.pwb.iam.domain.model.OtpPurpose;
import com.pwb.iam.domain.model.User;
import com.pwb.web.message.MessageResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IamFacadeImpl implements IamFacade {

    private static final String MSG_REGISTER = "AUTH_REGISTER_MESSAGE";
    private static final String MSG_VERIFY_OTP = "AUTH_VERIFY_OTP_SUCCESSFUL";
    private static final String MSG_COMPLETE_PROFILE = "AUTH_COMPLETE_PROFILE_SUCCESSFUL";
    private static final String MSG_OTP_RESENT = "AUTH_OTP_RESENT";

    private final RegisterUseCase registerUseCase;
    private final VerifyOtpUseCase verifyOtpUseCase;
    private final CompleteProfileUseCase completeProfileUseCase;
    private final ResendOtpUseCase resendOtpUseCase;
    private final MessageResolver messageResolver;
    private final AuthEventPublisher authEventPublisher;

    @Override
    public AuthMessageResponse register(RegisterRequest request) {
        RegisterCommand command = new RegisterCommand(
                request.email(),
                request.password(),
                request.fullName()
        );
        User saved = registerUseCase.execute(command);
        String message = messageResolver.get(MSG_REGISTER);
        return AuthMessageResponse.of(saved.getUserId(), message);
    }

    @Override
    public AuthResponse verifyOtp(VerifyOtpRequest request) {
        VerifyOtpCommand command = new VerifyOtpCommand(
                request.userId(),
                request.purpose(),
                request.code()
        );
        User user = verifyOtpUseCase.execute(command);
        AuthResponse response = AuthResponse.bearerOnly(
                user.getUserId(),
                user.getEmail().value(),
                user.getUsername(),
                user.getStatus().name(),
                user.getRole().name()
        );
        authEventPublisher.publishAuthSuccess(AuthSuccessEvent.of(user.getUserId(), user.getEmail().value()));
        response.setMessage(messageResolver.get(MSG_VERIFY_OTP));
        return response;
    }

    @Override
    public AuthResponse completeProfile(UUID userId, CompleteProfileRequest request) {
        CompleteProfileCommand command = new CompleteProfileCommand(
                userId,
                request.username(),
                request.fullName()
        );
        User user = completeProfileUseCase.execute(command);
        AuthResponse response = AuthResponse.bearerOnly(
                user.getUserId(),
                user.getEmail().value(),
                user.getUsername(),
                user.getStatus().name(),
                user.getRole().name()
        );
        response.setMessage(messageResolver.get(MSG_COMPLETE_PROFILE));
        return response;
    }

    @Override
    public AuthMessageResponse resendOtp(ResendOtpRequest request) {
        OtpPurpose purpose = request.purpose() == null ? OtpPurpose.REGISTER : request.purpose();
        ResendOtpCommand command = new ResendOtpCommand(request.userId(), purpose);
        resendOtpUseCase.execute(command);
        return AuthMessageResponse.of(request.userId(), messageResolver.get(MSG_OTP_RESENT));
    }
}
