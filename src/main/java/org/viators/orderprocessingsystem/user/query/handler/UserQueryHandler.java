package org.viators.orderprocessingsystem.user.query.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.viators.orderprocessingsystem.user.query.repository.UserQueryRepository;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class UserQueryHandler {

    private final UserQueryRepository userQueryRepository;
}
