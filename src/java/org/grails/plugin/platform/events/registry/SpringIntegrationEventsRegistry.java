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
package org.grails.plugin.platform.events.registry;

import groovy.lang.Closure;
import org.apache.log4j.Logger;
import org.grails.plugin.platform.events.EventDefinition;
import org.grails.plugin.platform.events.EventMessage;
import org.grails.plugin.platform.events.ListenerId;
import org.grails.plugin.platform.events.publisher.EventsPublisherGateway;
import org.grails.plugin.platform.events.publisher.TrackableNullResult;
import org.springframework.aop.framework.Advised;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionReaderUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.integration.Message;
import org.springframework.integration.MessageChannel;
import org.springframework.integration.channel.ChannelInterceptor;
import org.springframework.integration.channel.PublishSubscribeChannel;
import org.springframework.integration.core.SubscribableChannel;
import org.springframework.integration.handler.BridgeHandler;
import org.springframework.integration.handler.ServiceActivatingHandler;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.integration.support.channel.BeanFactoryChannelResolver;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Stephane Maldini <smaldini@doc4web.com>
 * @version 1.0
 * @file
 * @date 02/01/12
 * @section DESCRIPTION
 * <p/>
 * [Does stuff]
 */
public class SpringIntegrationEventsRegistry implements EventsRegistry, BeanFactoryAware, ApplicationContextAware {

    static final private Logger log = Logger.getLogger(SpringIntegrationEventsRegistry.class);

    private ApplicationContext ctx;
    private ConfigurableBeanFactory beanFactory;
    private BeanFactoryChannelResolver resolver;
    private MessageChannel outputChannel;
    private ChannelInterceptor interceptor;

    public void setInterceptor(ChannelInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    public void setOutputChannel(MessageChannel outputChannel) {
        this.outputChannel = outputChannel;
    }

    private SubscribableChannel createChannel(String channelName) {
        GrailsPublishSubscribeChannel _channel;
        try {
            _channel = ctx.getBean(channelName, GrailsPublishSubscribeChannel.class);
            return _channel;
        } catch (BeansException be) {
            log.debug("no overriding/existing channel found " + be.getMessage());
        }

        _channel = new GrailsPublishSubscribeChannel();
        _channel.setApplySequence(true);
        _channel.setBeanName(channelName);
        _channel.setBeanFactory(beanFactory);
        if(interceptor != null)
        	_channel.addInterceptor(interceptor);
        beanFactory.registerSingleton(channelName, _channel);

        return _channel;
    }

    private String registerHandler(Object bean, Method callback, String scope, String topic, EventDefinition defintion) {
        Object target = bean;

        //todo expose param to let the listener traversing proxies (like tx)
        if (bean instanceof Advised) {
            try {
                target = ((Advised) bean).getTargetSource().getTarget();
            } catch (Exception e) {
                log.error("failed to retrieve bean origin from proxy", e);
            }
        }

        ListenerId listener = ListenerId.build(scope, topic, target, callback);

        ServiceActivatingHandler serviceActivatingHandler =
                new GrailsServiceActivatingHandler(target, callback, listener, defintion);


        initServiceActivatingHandler(serviceActivatingHandler, listener, topic);

        return listener.toString();
    }

    private String registerHandler(Closure callback, String scope, String topic, EventDefinition definition) {

        ListenerId listener = ListenerId.build(scope, topic, callback);

        ServiceActivatingHandler serviceActivatingHandler =
                new GrailsServiceActivatingHandler(callback, "call", listener, definition);

        initServiceActivatingHandler(serviceActivatingHandler, listener, topic);

        return listener.toString();
    }

    private void initServiceActivatingHandler(ServiceActivatingHandler serviceActivatingHandler, ListenerId listener, String topic) {
        if (topic == null || topic.isEmpty()) {
            throw new RuntimeException("topic name must not be null or empty");
        }

        String callBackId = listener.toString();
        serviceActivatingHandler.setBeanName(callBackId);
        serviceActivatingHandler.setChannelResolver(resolver);
        serviceActivatingHandler.setRequiresReply(true);
        serviceActivatingHandler.setOutputChannel(outputChannel);
        String beanIdBase = listener.getClassName();
        int counter = 0;
        String beanId;

        do {
            counter++;
            beanId = beanIdBase + BeanDefinitionReaderUtils.GENERATED_BEAN_NAME_SEPARATOR + counter;
        } while (beanFactory.containsBean(beanId));

        beanFactory.registerSingleton(beanId, serviceActivatingHandler);
        serviceActivatingHandler.afterPropertiesSet();

        SubscribableChannel bridgeChannel = null;
        SubscribableChannel channel = null;
        String channelName = listener.getTopic();


        try {
            bridgeChannel = ctx.getBean(channelName, SubscribableChannel.class);
        } catch (BeansException be) {
            log.debug("no overriding/existing channel found " + be.getMessage());
        }

        if (bridgeChannel == null || !bridgeChannel.getClass().isAssignableFrom(GrailsPublishSubscribeChannel.class)) {
            if (bridgeChannel != null) {
                channelName += "-plugin";
            }
            channel = createChannel(channelName);
        } else {
            channel = bridgeChannel;
        }

        channel.subscribe(serviceActivatingHandler);

        if (bridgeChannel != null && !bridgeChannel.getClass().isAssignableFrom(GrailsPublishSubscribeChannel.class)) {
            BridgeHandler bridgeHandler = new BridgeHandler();
            bridgeHandler.setOutputChannel(channel);
            bridgeChannel.subscribe(bridgeHandler);
        }

    }

    public String addListener(String scope, String topic, Closure callback) {
        return registerHandler(callback, scope, topic, null);
    }

    public String addListener(String scope, String topic, Object bean, String callbackName) {
        return registerHandler(bean, ReflectionUtils.findMethod(bean.getClass(), callbackName), scope, topic, null);
    }

    public String addListener(String scope, String topic, Object bean, Method callback) {
        return registerHandler(bean, callback, scope, topic, null);
    }

    public String addListener(String scope, String topic, Closure callback, EventDefinition definition) {
        return registerHandler(callback, scope, topic, definition);
    }

    public String addListener(String scope, String topic, Object bean, String callbackName, EventDefinition definition) {
        return registerHandler(bean, ReflectionUtils.findMethod(bean.getClass(), callbackName), scope, topic, definition);
    }

    public String addListener(String scope, String topic, Object bean, Method callback, EventDefinition definition) {
        return registerHandler(bean, callback, scope, topic, definition);
    }

    private List<GrailsServiceActivatingHandler> findAllListenersFor(String callbackId) {
        ListenerId listener = ListenerId.parse(callbackId);

        List<GrailsServiceActivatingHandler> targetListeners = new ArrayList<GrailsServiceActivatingHandler>();
        if (listener == null)
            return targetListeners;

        Map<String, GrailsServiceActivatingHandler> grailsListeners = ctx.getBeansOfType(GrailsServiceActivatingHandler.class);
        for (Map.Entry<String, GrailsServiceActivatingHandler> _listener : grailsListeners.entrySet()) {
            if (listener.matches(_listener.getValue().getListenerId()))
                targetListeners.add(_listener.getValue());
        }

        return targetListeners;
    }

    public int removeListeners(String callbackId) {
        if (callbackId == null) return 0;

        List<GrailsServiceActivatingHandler> targetListeners = findAllListenersFor(callbackId);

        if (targetListeners.isEmpty()) {
            return 0;
        }

        Map<String, PublishSubscribeChannel> channels = ctx.getBeansOfType(PublishSubscribeChannel.class);

        int removed = 0;
        for (Map.Entry<String, PublishSubscribeChannel> entry : channels.entrySet()) {
            for (GrailsServiceActivatingHandler _listener : targetListeners) {
                if (entry.getValue().unsubscribe(_listener)) removed++;
            }
        }
        return removed;
    }

    public int countListeners(String callbackId) {
        return findAllListenersFor(callbackId).size();
    }

    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.ctx = applicationContext;
    }

    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = (ConfigurableBeanFactory) beanFactory;
        this.resolver = new BeanFactoryChannelResolver(beanFactory);
    }

    private static class GrailsPublishSubscribeChannel extends PublishSubscribeChannel {
    }

    private class GrailsServiceActivatingHandler extends ServiceActivatingHandler implements EventHandler {

        private ListenerId listenerId;
        private boolean useEventMessage = false;
        private EventDefinition definition;

        private void init(ListenerId listenerId, EventDefinition definition) {
            this.listenerId = listenerId;
            this.definition = definition;
        }

        public GrailsServiceActivatingHandler(Object object, String methodName, ListenerId listenerId, EventDefinition definition) {
            super(object, methodName);
            init(listenerId, definition);
        }

        public GrailsServiceActivatingHandler(Object object, Method method, ListenerId listenerId, EventDefinition definition) {
            super(object, method);
            init(listenerId, definition);
            if (method.getParameterTypes().length > 0) {
                Class<?> type = method.getParameterTypes()[0];
                useEventMessage = EventMessage.class.isAssignableFrom(type);
            }
        }


        @Override
        protected Object handleRequestMessage(Message<?> message) {
            EventMessage eventObject = (EventMessage) message.getHeaders().get(EventsPublisherGateway.EVENT_OBJECT_KEY);
            Object res = null;
            if (listenerId.getScope() == null || listenerId.getScope().equals(ListenerId.SCOPE_WILDCARD) || eventObject.getScope().equalsIgnoreCase(listenerId.getScope())) {
                Message<?> _message = useEventMessage ?
                        MessageBuilder.withPayload(eventObject).copyHeaders(message.getHeaders()).build() :
                        message;

                if (definition == null || (definition.getFilterClass() == null && definition.getFilterClosure() == null) ||
                        (_message.getPayload() != null && (definition.getFilterClass() != null && definition.getFilterClass().isAssignableFrom(_message.getPayload().getClass())) ||
                                definition.getFilterClosure() != null && (Boolean) ((Closure<?>) definition.getFilterClosure().clone()).call(_message.getPayload()))) {

                    res = super.handleRequestMessage(_message);
                }
            }
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
}
