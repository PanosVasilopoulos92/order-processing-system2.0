package org.viators.orderprocessingsystem.user.query.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;
import org.viators.orderprocessingsystem.exceptions.ResourceNotFoundException;
import org.viators.orderprocessingsystem.user.command.event.EmailUpdatedEvent;
import org.viators.orderprocessingsystem.user.query.entity.UserReadEntity;
import org.viators.orderprocessingsystem.user.query.repository.UserQueryRepository;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserEventListener {

    private final UserQueryRepository userQueryRepository;

    @Async("cqrsEventExecutor")
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(EmailUpdatedEvent event) {
        log.info("Handling EmailUpdateEvent for event: {}", event.eventUuid());
        UserReadEntity entity = userQueryRepository.findByUuid(event.userUuid())
            .orElseThrow(() -> new ResourceNotFoundException("User", "uuid", event.userUuid()));

        event.update(entity);
        userQueryRepository.save(entity);
        log.debug("User with uuid: {} has updated it's email", entity.getUuid());
    }

}
