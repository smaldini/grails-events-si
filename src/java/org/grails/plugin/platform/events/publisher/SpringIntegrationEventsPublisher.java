package org.grails.plugin.platform.events.publisher;

import org.apache.log4j.Logger;
import org.grails.plugin.platform.events.Event;
import org.grails.plugin.platform.events.registry.EventsRegistry;
import org.springframework.integration.MessageDeliveryException;
import org.springframework.integration.MessagingException;
import org.springframework.integration.handler.ReplyRequiredException;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
        } catch (MessagingException rre) {
            if (log.isDebugEnabled()) {
                log.debug("Missing reply on event " + event.getEvent() + " from source " +
                        event.getSource() + " for one or more listeners - " + rre.getMessage());
            }
        }
        return null;
    }

    public Future<Object> eventAsync(Event event) {
        return new WrappedFuture(eventsPublisherGateway.sendAsync(event, EventsRegistry.GRAILS_TOPIC_PREFIX + event.getEvent()));
    }

    public void eventAsync(Event event, Runnable onComplete) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    private static class WrappedFuture implements Future<Object> {

        private Future<Object> eventReply;

        public WrappedFuture(Future<Object> wrapped){
            this.eventReply = wrapped;
        }
        
        public boolean cancel(boolean b) {
            return eventReply.cancel(b);
        }

        public boolean isCancelled() {
            return eventReply.isCancelled();
        }

        public boolean isDone() {
            return eventReply.isDone();
        }

        public Object get() throws InterruptedException, ExecutionException {
            try {
                return eventReply.get();
            } catch (ExecutionException mde) {
                if (mde.getCause() != null && (mde.getCause().getClass().equals(MessageDeliveryException.class) ||
                        mde.getCause().getClass().equals(ReplyRequiredException.class)))
                    return null;
                else
                    throw mde;
            }
        }

        public Object get(long l, TimeUnit timeUnit) throws InterruptedException, ExecutionException, TimeoutException {
            try {
                return eventReply.get(l, timeUnit);
            } catch (MessageDeliveryException mde) {
                return null;
            }
        }
    }

}
