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

import org.grails.plugin.platform.events.EventMessage;
import org.grails.plugin.platform.events.publisher.EventsPublisherGateway;
import org.grails.plugin.platform.events.publisher.TrackableNullResult;
import org.springframework.integration.Message;
import org.springframework.integration.MessageChannel;
import org.springframework.integration.channel.ChannelInterceptor;
import org.springframework.integration.core.PollableChannel;
import org.springframework.integration.message.GenericMessage;

/**
 * @author Stephane Maldini <smaldini@doc4web.com>
 * @version 1.0
 * @file
 * @date 05/01/12
 * @section DESCRIPTION
 * <p/>
 * [Does stuff]
 */
public class NullReplyInterceptor implements ChannelInterceptor {


    public Message<?> preSend(Message<?> message, MessageChannel messageChannel) {
        EventMessage evt = (EventMessage)message.getHeaders().get(EventsPublisherGateway.EVENT_OBJECT_KEY);
        if (evt != null) {
            ((PollableChannel) message.getHeaders().getReplyChannel()).send(new GenericMessage<TrackableNullResult>(new TrackableNullResult()));
        }
        return message;
    }

    public void postSend(Message<?> message, MessageChannel messageChannel, boolean b) {
    }

    public boolean preReceive(MessageChannel messageChannel) {
        return true;
    }

    public Message<?> postReceive(Message<?> message, MessageChannel messageChannel) {
        return message;
    }
}
