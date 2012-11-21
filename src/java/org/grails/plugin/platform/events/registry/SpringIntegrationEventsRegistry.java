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
import org.codehaus.groovy.grails.commons.spring.WebRuntimeSpringConfiguration;
import org.grails.plugin.platform.events.EventMessage;
import org.grails.plugin.platform.events.ListenerId;
import org.grails.plugin.platform.events.publisher.EventsPublisherGateway;
import org.springframework.aop.framework.Advised;
import org.springframework.beans.BeansException;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.support.*;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.integration.MessageChannel;
import org.springframework.integration.annotation.Header;
import org.springframework.integration.annotation.Router;
import org.springframework.integration.channel.ChannelInterceptor;
import org.springframework.integration.channel.PublishSubscribeChannel;
import org.springframework.integration.core.SubscribableChannel;
import org.springframework.integration.handler.BridgeHandler;
import org.springframework.integration.handler.ServiceActivatingHandler;
import org.springframework.integration.support.channel.BeanFactoryChannelResolver;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
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
    static final private String APP_NAMESPACE = "app";

    private ApplicationContext ctx;
    private BeanDefinitionRegistry beanFactory;
    private BeanFactoryChannelResolver resolver;
    private MessageChannel outputChannel;
    private ChannelInterceptor interceptor;
    private final Map<ListenerId, SubscribableChannel> grailsPublishSubscribeChannels
            = new HashMap<ListenerId, SubscribableChannel>();
    private static final String HANDLER_SUFFIX = "#eventsHandler";


    public void setInterceptor(ChannelInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    public void setOutputChannel(MessageChannel outputChannel) {
        this.outputChannel = outputChannel;
    }

    @Router
    public List<MessageChannel> route(@Header(EventsPublisherGateway.EVENT_OBJECT_KEY) EventMessage eventMessage) {
        List<MessageChannel> messageChannels = new ArrayList<MessageChannel>();
        ListenerId listenerId = new ListenerId(eventMessage.getNamespace(), eventMessage.getEvent());
        for (Map.Entry<ListenerId, SubscribableChannel> _listener : this.grailsPublishSubscribeChannels.entrySet()) {
            if (listenerId.matches(_listener.getKey())) {
                messageChannels.add(_listener.getValue());
            }
        }

        return messageChannels;
    }

    private SubscribableChannel createChannel(String channelName, ListenerId listenerId) {
        GrailsPublishSubscribeChannel _channel;
        try {
            _channel = ctx.getBean(channelName, GrailsPublishSubscribeChannel.class);
            return _channel;
        } catch (BeansException be) {
            log.debug("no overriding/existing channel found " + be.getMessage());
        }

        BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(GrailsPublishSubscribeChannel.class)
                .addConstructorArgValue(listenerId)
                .addPropertyValue("applySequence", true);

        if (interceptor != null){
            builder.addPropertyValue("interceptors", interceptor);
        }

        beanFactory.registerBeanDefinition(channelName, builder.getBeanDefinition());

        return ctx.getBean(channelName, GrailsPublishSubscribeChannel.class);
    }

    private String registerHandler(Object bean, Method callback, String scope, String topic) {
        ListenerId listener = ListenerId.build(scope, topic, bean, callback);

//        ServiceActivatingHandler serviceActivatingHandler =
//                new GrailsServiceActivatingHandler(target, callback, listener);

        BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition()
                .addConstructorArgValue(bean)
                .addConstructorArgValue(callback);

        if (interceptor != null){
            builder.addPropertyValue("interceptors", interceptor);
        }

        initServiceActivatingHandler(builder, listener, topic);

        return listener.toString();
    }

    private String registerHandler(Closure callback, String scope, String topic) {

        ListenerId listener = ListenerId.build(scope, topic, callback);

        BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition()
                .addConstructorArgValue(callback)
                .addConstructorArgValue("call");

//        ServiceActivatingHandler serviceActivatingHandler =
//                new GrailsServiceActivatingHandler(callback, "call", listener);

        initServiceActivatingHandler(builder, listener, topic);

        return listener.toString();
    }

    private void initServiceActivatingHandler(final BeanDefinitionBuilder serviceActivatingHandler, final ListenerId listener, final String topic) {
        if (topic == null || topic.isEmpty()) {
            throw new RuntimeException("topic name must not be null or empty");
        }

        serviceActivatingHandler.addConstructorArgValue(listener)
        .addPropertyValue("channelResolver", resolver)
        .addPropertyValue("requiresReply", true)
        .addPropertyValue("outputChannel", outputChannel);

        BeanDefinition beanDefinition = serviceActivatingHandler.getBeanDefinition();
        serviceActivatingHandler.getBeanDefinition().setBeanClass(GrailsServiceActivatingHandler.class);

        String beanIdBase = listener.getClassName();
        int counter = 0;
        String beanId;

        do {
            counter++;
            beanId = beanIdBase + HANDLER_SUFFIX + BeanDefinitionReaderUtils.GENERATED_BEAN_NAME_SEPARATOR + counter;
        } while (ctx.containsBean(beanId));

        beanFactory.registerBeanDefinition(beanId, beanDefinition);
        ServiceActivatingHandler _serviceActivatingHandler = ctx.getBean(beanId, ServiceActivatingHandler.class);

        SubscribableChannel bridgeChannel = null;
        SubscribableChannel channel = null;
        String channelName =
                (
                        listener.getNamespace() != null &&
                                !listener.getNamespace().equalsIgnoreCase(APP_NAMESPACE) ? listener.getNamespace() + "://" : ""
                )
                        + listener.getTopic();


        try {
            bridgeChannel = ctx.getBean(channelName, SubscribableChannel.class);
        } catch (BeansException be) {
            log.debug("no overriding/existing channel found " + be.getMessage());
        }

        if (bridgeChannel == null || !bridgeChannel.getClass().isAssignableFrom(GrailsPublishSubscribeChannel.class)) {
            if (bridgeChannel != null) {
                channelName += "-local";
            }
            channel = createChannel(channelName, listener);
        } else {
            channel = bridgeChannel;
        }

        if(bridgeChannel != channel){
            synchronized (grailsPublishSubscribeChannels) {
                grailsPublishSubscribeChannels.put(listener, bridgeChannel == null ? channel : bridgeChannel);
            }
        }

        channel.subscribe(_serviceActivatingHandler);

        if (bridgeChannel != null && !bridgeChannel.getClass().isAssignableFrom(GrailsPublishSubscribeChannel.class)) {
            BridgeHandler bridgeHandler = new BridgeHandler();
            bridgeHandler.setOutputChannel(channel);
            bridgeChannel.subscribe(bridgeHandler);
        }

    }

    public String on(String scope, String topic, Closure callback) {
        return registerHandler(callback, scope, topic);
    }

    public String on(String scope, String topic, Object bean, String callbackName) {
        return registerHandler(bean, ReflectionUtils.findMethod(bean.getClass(), callbackName), scope, topic);
    }

    public String on(String scope, String topic, Object bean, Method callback) {
        return registerHandler(bean, callback, scope, topic);
    }

    private Map<String, GrailsServiceActivatingHandler> findAllListenersFor(String callbackId) {
        ListenerId listener = ListenerId.parse(callbackId);

        Map<String, GrailsServiceActivatingHandler> targetListeners = new HashMap<String, GrailsServiceActivatingHandler>();
        if (listener == null)
            return targetListeners;

        Map<String, GrailsServiceActivatingHandler> grailsListeners = ctx.getBeansOfType(GrailsServiceActivatingHandler.class);
        for (Map.Entry<String, GrailsServiceActivatingHandler> _listener : grailsListeners.entrySet()) {
            if (listener.matches(_listener.getValue().getListenerId()))
                targetListeners.put(_listener.getKey(), _listener.getValue());
        }

        return targetListeners;
    }

    public int removeListeners(String callbackId) {
        if (callbackId == null) return 0;

        Map<String, GrailsServiceActivatingHandler> targetListeners = findAllListenersFor(callbackId);

        if (targetListeners.isEmpty()) {
            return 0;
        }

        Map<String, PublishSubscribeChannel> channels = ctx.getBeansOfType(PublishSubscribeChannel.class);

        int removed = 0;
        for (Map.Entry<String, PublishSubscribeChannel> entry : channels.entrySet()) {
            for (Map.Entry<String, GrailsServiceActivatingHandler> _listener : targetListeners.entrySet()) {
                if (entry.getValue().unsubscribe(_listener.getValue())) removed++;
            }
        }
        for (String key : targetListeners.keySet()) {
            try {
                beanFactory.removeBeanDefinition(key);
            } catch (Exception e) {
                log.error("failed to destroy bean named : " + key);
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
        this.beanFactory = (BeanDefinitionRegistry) beanFactory;
        this.resolver = new BeanFactoryChannelResolver(beanFactory);
    }

    private static class GrailsPublishSubscribeChannel extends PublishSubscribeChannel {
        private ListenerId listenerId;

        private GrailsPublishSubscribeChannel(ListenerId listenerId) {
            this.listenerId = listenerId;
        }

        public ListenerId getListenerId() {
            return listenerId;
        }
    }

}
