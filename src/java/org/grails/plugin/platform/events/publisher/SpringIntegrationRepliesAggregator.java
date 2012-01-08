package org.grails.plugin.platform.events.publisher;

import org.apache.log4j.Logger;
import org.springframework.integration.Message;
import org.springframework.integration.annotation.Aggregator;
import org.springframework.integration.message.GenericMessage;
import org.springframework.integration.support.MessageBuilder;

import java.util.ArrayList;
import java.util.Collections;
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
        if(messages.size() == 1)
            return messages.get(0);

        List<Object> payload = new ArrayList<Object>();

        for(Message<?> message : messages){
            payload.add(message.getPayload());
        }

        return new GenericMessage(payload);
    }

}
