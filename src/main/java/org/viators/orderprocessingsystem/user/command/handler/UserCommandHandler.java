package org.viators.orderprocessingsystem.user.command.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.viators.orderprocessingsystem.exceptions.ResourceNotFoundException;
import org.viators.orderprocessingsystem.user.command.entity.UserWriteEntity;
import org.viators.orderprocessingsystem.user.command.event.EmailUpdatedEvent;
import org.viators.orderprocessingsystem.user.command.model.ChangePasswordCommand;
import org.viators.orderprocessingsystem.user.command.model.UpdateEmailCommand;
import org.viators.orderprocessingsystem.user.command.repository.UserCommandRepository;
import org.viators.orderprocessingsystem.user.dto.response.UserSummaryResponse;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UserCommandHandler {

    private final UserCommandRepository userCommandRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final PasswordEncoder passwordEncoder;

    public UserSummaryResponse handleUpdatePassword(ChangePasswordCommand command) {
        UserWriteEntity entity = userCommandRepository.findByUuid(command.userUuid())
            .orElseThrow(() -> new ResourceNotFoundException("User", "uuid", command.userUuid()));

        entity.setPassword(passwordEncoder.encode(command.newPassword()));
        log.info("Password changed for user with uuid: {}", entity.getUuid());

        return UserSummaryResponse.from(entity);
    }

    public UserSummaryResponse handleUpdateEmail(UpdateEmailCommand command) {
        UserWriteEntity entity = userCommandRepository.findByUuid(command.userUuid())
            .orElseThrow(() -> new ResourceNotFoundException("User", "uuid", command.userUuid()));

        entity.setEmail(command.newEmail());
        log.info("Email changed for user with uuid: {}", entity.getUuid());

        // Event Publishing
        EmailUpdatedEvent event = new EmailUpdatedEvent(
            UUID.randomUUID().toString(),
            entity.getUuid(),
            entity.getUsername(),
            entity.getEmail()
        );

        eventPublisher.publishEvent(event);
        log.info("Event with uuid: {} emitted for update email of user with uuid: {}",
            event.eventUuid(), entity.getUuid());

        return UserSummaryResponse.from(entity);
    }
}
