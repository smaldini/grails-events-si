/* Copyright 2011-2012 the original author or authors:
 *
 *    Marc Palmer (marc@grailsrocks.com)
 *    Stéphane Maldini (stephane.maldini@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.plugin.platform.events.publisher;

import groovy.lang.Closure;
import org.apache.log4j.Logger;
import org.grails.plugin.platform.events.EventMessage;
import org.grails.plugin.platform.events.EventReply;
import org.springframework.core.task.TaskExecutor;
import org.springframework.integration.Message;
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
    private TaskExecutor taskExecutor;

    public void setTaskExecutor(TaskExecutor taskExecutor) {
        this.taskExecutor = taskExecutor;
    }

    public void setEventsPublisherGateway(EventsPublisherGateway eventsPublisherGateway) {
        this.eventsPublisherGateway = eventsPublisherGateway;
    }

    public EventReply event(final EventMessage event) {
        try {
            Object data = event.getData() != null ? event.getData() : new TrackableNullResult();
            Message<?> res = eventsPublisherGateway.send(data, event, event.getEvent());
            return new EventReply(res.getPayload(), res.getHeaders().getSequenceSize());
        } catch (MessagingException rre) {
            if (log.isDebugEnabled()) {
                log.debug("Missing reply on event " + event.getEvent() + " for scope " +
                        event.getNamespace() + " for one or more listeners - " + rre.getMessage());
            }
        }
        return new EventReply(null, 0);
    }

    public EventReply eventAsync(final EventMessage event) {
        Object data = event.getData() != null ? event.getData() : new TrackableNullResult();
        return new WrappedFuture(
                eventsPublisherGateway.sendAsync(data, event, event.getEvent()),
                -1
        );
    }

    public void eventAsync(final EventMessage event, final Closure onComplete) {
        taskExecutor.execute(new Runnable() {

            public void run() {
                Object data = event.getData() != null ? event.getData() : new TrackableNullResult();
                Message<?> res =
                        eventsPublisherGateway.send(data, event,
                                event.getEvent());

                onComplete.call(
                        new EventReply(res.getPayload(),
                                res.getHeaders().getSequenceSize()
                        ));
            }
        });
    }

    public EventReply[] waitFor(EventReply... replies) throws ExecutionException, InterruptedException {
        for (EventReply reply : replies) {
            reply.get();
        }
        return replies;
    }

    private static class WrappedFuture extends EventReply {

        public WrappedFuture(Future<?> wrapped, int receivers) {
            super(wrapped, receivers);
        }

        @Override
        protected void initValues(Object val) {
            Message<?> message = (Message<?>) val;
            setReceivers(message.getHeaders().getSequenceSize());
            super.initValues(message.getPayload());
        }

        @Override
        public Object get() throws InterruptedException, ExecutionException {
            try {
                super.get();
                return getValue();
            } catch (ExecutionException mde) {
                if (mde.getCause() != null && (mde.getCause().getClass().equals(MessageDeliveryException.class) ||
                        mde.getCause().getClass().equals(ReplyRequiredException.class)))
                    return null;
                else
                    throw mde;
            }
        }

        @Override
        public int size() throws Throwable {
            get();
            return super.size();
        }

        @Override
        public Object get(long l, TimeUnit timeUnit) throws InterruptedException, ExecutionException, TimeoutException {
            try {
                super.get(l, timeUnit);
                return getValue();
            } catch (MessageDeliveryException mde) {
                return null;
            }
        }
    }

}