package org.grails.plugin.platform.events.registry;


import org.grails.plugin.platform.events.EventMessage;
import org.grails.plugin.platform.events.ListenerId;
import org.grails.plugin.platform.events.publisher.EventsPublisherGateway;
import org.grails.plugin.platform.events.publisher.TrackableNullResult;
import org.springframework.integration.Message;
import org.springframework.integration.handler.ServiceActivatingHandler;
import org.springframework.integration.support.MessageBuilder;

import java.lang.reflect.Method;

class GrailsServiceActivatingHandler extends ServiceActivatingHandler implements EventHandler {

    private ListenerId listenerId;
    private boolean useEventMessage = false;

    private void init(ListenerId listenerId) {
        this.listenerId = listenerId;
    }

    public GrailsServiceActivatingHandler(Object object, String methodName, ListenerId listenerId) {
        super(object, methodName);
        init(listenerId);
    }

    public GrailsServiceActivatingHandler(Object object, Method method, ListenerId listenerId) {
        super(object, method);
        init(listenerId);
        if (method.getParameterTypes().length > 0) {
            Class<?> type = method.getParameterTypes()[0];
            useEventMessage = EventMessage.class.isAssignableFrom(type);
        }
    }


    @Override
    protected Object handleRequestMessage(Message<?> message) {
        EventMessage eventObject = (EventMessage) message.getHeaders().get(EventsPublisherGateway.EVENT_OBJECT_KEY);
        Object res = null;
        Message<?> _message = useEventMessage ?
                MessageBuilder.withPayload(eventObject).copyHeaders(message.getHeaders()).build() :
                message;
        res = super.handleRequestMessage(_message);
        if (res == null) {
            //Return TRUE if GORM event to bypass cancel
            //Else return FALSE for other events (never returning true)
            return new TrackableNullResult();
        }
        return res;
    }

    public boolean isUseEventMessage() {
        return useEventMessage;
    }

    public ListenerId getListenerId() {
        return listenerId;
    }
}
