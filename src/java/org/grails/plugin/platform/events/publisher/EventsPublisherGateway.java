package org.grails.plugin.platform.events.publisher;

import org.grails.plugin.platform.events.Event;
import org.springframework.integration.annotation.Header;

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
public interface EventsPublisherGateway {

    public static final String TARGET_CHANNEL = "targetChannel";

    public Object send(Event event,  @Header(TARGET_CHANNEL) String targetChannel);

    public Future<Object> sendAsync(Event event, @Header(TARGET_CHANNEL) String targetChannel);
}
