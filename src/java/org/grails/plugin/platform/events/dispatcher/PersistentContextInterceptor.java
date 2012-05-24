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
package org.grails.plugin.platform.events.dispatcher;

import org.apache.log4j.Logger;
import org.codehaus.groovy.grails.support.PersistenceContextInterceptor;
import org.grails.plugin.platform.events.EventMessage;
import org.grails.plugin.platform.events.publisher.EventsPublisherGateway;
import org.springframework.integration.Message;
import org.springframework.integration.MessageChannel;
import org.springframework.integration.channel.ChannelInterceptor;

/**
 * @author Stephane Maldini <smaldini@doc4web.com>
 * @version 1.0
 * @file
 * @date 05/01/12
 * @section DESCRIPTION
 * <p/>
 * [Does stuff]
 */
public class PersistentContextInterceptor implements ChannelInterceptor {

    static final private Logger log = Logger.getLogger(PersistentContextInterceptor.class);

    private PersistenceContextInterceptor persistenceInterceptor;
    private boolean catchFlushExceptions = false;

    public void setCatchFlushExceptions(boolean catchFlushExceptions) {
        this.catchFlushExceptions = catchFlushExceptions;
    }

    public void setPersistenceInterceptor(PersistenceContextInterceptor persistenceInterceptor) {
        this.persistenceInterceptor = persistenceInterceptor;
    }

    public Message<?> preSend(Message<?> message, MessageChannel messageChannel) {
        EventMessage event = (EventMessage)message.getHeaders().get(EventsPublisherGateway.EVENT_OBJECT_KEY);
        if (event != null && event.isGormSession()) {
            persistenceInterceptor.init();
            log.debug("intercepting");
        }

        return message;
    }

    public void postSend(Message<?> message, MessageChannel messageChannel, boolean b) {
        EventMessage event = (EventMessage)message.getHeaders().get(EventsPublisherGateway.EVENT_OBJECT_KEY);
        if (event.isGormSession()) {
            try {
                persistenceInterceptor.flush();
                log.debug("flushed");
            } catch (RuntimeException re) {
                if (!catchFlushExceptions)
                    throw re;
            } finally {
                persistenceInterceptor.destroy();
            }
        }
    }

    public boolean preReceive(MessageChannel messageChannel) {
        return true;
    }

    public Message<?> postReceive(Message<?> message, MessageChannel messageChannel) {
        return message;
    }
}
