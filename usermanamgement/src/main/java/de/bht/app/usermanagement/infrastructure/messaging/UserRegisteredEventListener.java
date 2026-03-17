package de.bht.app.usermanagement.infrastructure.messaging;

import de.bht.app.usermanagement.domain.events.UserRegisteredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class UserRegisteredEventListener {
    private static final Logger logger = LoggerFactory.getLogger(UserRegisteredEventListener.class);

    @EventListener
    public void handle(UserRegisteredEvent event) {
        logger.info("Neuer Nutzer registriert:");
        logger.info("ID: {}", event.getId());
        logger.info("E-Mail: {}", event.getEmail());
    }
}