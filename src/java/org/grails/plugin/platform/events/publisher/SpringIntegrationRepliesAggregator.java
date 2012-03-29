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

import org.apache.log4j.Logger;
import org.springframework.integration.Message;
import org.springframework.integration.annotation.Aggregator;
import org.springframework.integration.support.MessageBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Stephane Maldini <smaldini@doc4web.com>
 * @version 1.0
 * @file
 * @date 02/01/12
 * @section DESCRIPTION
 * <p/>
 * [Does stuff]
 */
public class SpringIntegrationRepliesAggregator {

    private final static Logger log = Logger.getLogger(SpringIntegrationRepliesAggregator.class);

    @Aggregator
    public Message<?> createSingleMessageFromGroup(List<Message<?>> messages) {
        if (messages.size() == 1 && !messages.get(0).getPayload().getClass().isAssignableFrom(TrackableNullResult.class))
            return messages.get(0);

        List<Object> payload = new ArrayList<Object>();

        for (Message<?> message : messages) {
            if (!message.getPayload().getClass().isAssignableFrom(TrackableNullResult.class))
                payload.add(message.getPayload());
        }


        return MessageBuilder.withPayload(payload.size() == 1 ? payload.get(0) : payload).setSequenceSize(payload.size()).build();
    }

}
