package org.grails.plugin.platform.events.dispatcher;

import org.apache.log4j.Logger;
import org.codehaus.groovy.grails.support.PersistenceContextInterceptor;
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
        persistenceInterceptor.init();
        log.debug("intercepting");
        return message;
    }

    public void postSend(Message<?> message, MessageChannel messageChannel, boolean b) {
        log.debug("flushing");
        try {
            persistenceInterceptor.flush();
        } catch (RuntimeException re) {
            if(!catchFlushExceptions)
                throw re;
        }
        persistenceInterceptor.destroy();
    }

    public boolean preReceive(MessageChannel messageChannel) {
        return true;
    }

    public Message<?> postReceive(Message<?> message, MessageChannel messageChannel) {
        return message;
    }
}
