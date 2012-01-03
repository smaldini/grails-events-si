package org.grails.plugin.platform.events.publisher;

import org.apache.log4j.Logger;
import org.grails.plugin.platform.events.Event;
import org.grails.plugin.platform.events.registry.EventsRegistry;
import org.springframework.integration.handler.ReplyRequiredException;

import java.util.concurrent.Future;

/**
 * @author Stephane Maldini <smaldini@doc4web.com>
 * @version 1.0
 * @file
 * @date 02/01/12
 * @section DESCRIPTION
 * <p/>
 * [Does stuff]
 */
public class SpringIntegrationEventsPublisher implements EventsPublisher {

    private final static Logger log = Logger.getLogger(SpringIntegrationEventsPublisher.class);


    private EventsPublisherGateway eventsPublisherGateway;

    public void setEventsPublisherGateway(EventsPublisherGateway eventsPublisherGateway) {
        this.eventsPublisherGateway = eventsPublisherGateway;
    }

    public Object event(Event event) {
        try {
            return eventsPublisherGateway.send(event, EventsRegistry.GRAILS_TOPIC_PREFIX + event.getEvent());
        } catch (ReplyRequiredException rre) {
            if (log.isDebugEnabled()) {
                log.debug("Missing reply on event " + event.getEvent() + " from source " +
                        event.getSource() + " for one or more listeners - " + rre.getMessage());
            }
        }
        return null;
    }

    public Future<Object> eventAsync(Event event) {
        return eventsPublisherGateway.sendAsync(event, EventsRegistry.GRAILS_TOPIC_PREFIX + event.getEvent());
    }

    public void eventAsync(Event event, Runnable onComplete) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

}
