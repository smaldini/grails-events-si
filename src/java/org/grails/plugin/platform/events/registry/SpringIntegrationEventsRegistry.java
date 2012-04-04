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
import org.grails.plugin.platform.events.ListenerId;
import org.grails.plugin.platform.events.publisher.TrackableNullResult;
import org.springframework.aop.framework.Advised;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.integration.Message;
import org.springframework.integration.MessageChannel;
import org.springframework.integration.channel.ChannelInterceptor;
import org.springframework.integration.channel.PublishSubscribeChannel;
import org.springframework.integration.core.SubscribableChannel;
import org.springframework.integration.handler.BridgeHandler;
import org.springframework.integration.handler.ServiceActivatingHandler;
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
    public static final String GORM_EVENT_KEY = "applicationEvent";

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
        PublishSubscribeChannel _channel = new PublishSubscribeChannel();
        _channel.setApplySequence(true);
        _channel.setBeanName(channelName);
        _channel.setBeanFactory(beanFactory);
        _channel.addInterceptor(interceptor);
        beanFactory.registerSingleton(channelName, _channel);

        return _channel;
    }

    private String registerHandler(Object bean, Method callback, String topic) {
        Object target = bean;

        //todo expose param to let the listener traversing proxies (like tx)
        if (bean instanceof Advised) {
            try {
                target = ((Advised) bean).getTargetSource().getTarget();
            } catch (Exception e) {
                log.error("failed to retrieve bean origin from proxy", e);
            }
        }

        ListenerId listener = ListenerId.build(topic, target, callback);

        ServiceActivatingHandler serviceActivatingHandler =
                new GrailsServiceActivatingHandler(target, callback, listener);


        initServiceActivatingHandler(serviceActivatingHandler, listener, topic);

        return listener.toString();
    }

    private String registerHandler(Closure callback, String topic) {

        ListenerId listener = ListenerId.build(topic, callback);

        ServiceActivatingHandler serviceActivatingHandler =
                new GrailsServiceActivatingHandler(callback, "call", listener);

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
        beanFactory.registerSingleton(callBackId, serviceActivatingHandler);

        SubscribableChannel bridgeChannel = null;
        String channelName = listener.getTopic();

        try {
            bridgeChannel = ctx.getBean(channelName, SubscribableChannel.class);
        } catch (BeansException be) {
            log.debug("no overriding channel found " + be.getMessage());
        }

        if (bridgeChannel != null) {
            channelName += "-plugin";
        }

        SubscribableChannel channel = createChannel(channelName);
        channel.subscribe(serviceActivatingHandler);

        if (bridgeChannel != null) {
            BridgeHandler bridgeHandler = new BridgeHandler();
            bridgeHandler.setOutputChannel(channel);
            bridgeChannel.subscribe(bridgeHandler);
        }

    }

    public String addListener(String topic, Closure callback) {
        return registerHandler(callback, topic);
    }

    public String addListener(String topic, Object bean, String callbackName) {
        return registerHandler(bean, ReflectionUtils.findMethod(bean.getClass(), callbackName), topic);
    }

    public String addListener(String topic, Object bean, Method callback) {
        return registerHandler(bean, callback, topic);
    }

    private List<GrailsServiceActivatingHandler> findAllListenersFor(String callbackId) {
        ListenerId listener = ListenerId.parse(callbackId);

        List<GrailsServiceActivatingHandler> targetListeners = new ArrayList<GrailsServiceActivatingHandler>();
        if (listener == null)
            return targetListeners;

        Map<String, GrailsServiceActivatingHandler> grailsListeners = ctx.getBeansOfType(GrailsServiceActivatingHandler.class);
        for (Map.Entry<String, GrailsServiceActivatingHandler> _listener : grailsListeners.entrySet()) {
            if (listener.equals(_listener.getKey()))
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
            if (entry.getKey().startsWith(GRAILS_TOPIC_PREFIX)) {
                for (GrailsServiceActivatingHandler _listener : targetListeners) {
                    if (entry.getValue().unsubscribe(_listener)) removed++;
                }
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

    private class GrailsServiceActivatingHandler extends ServiceActivatingHandler implements EventHandler {

        private ListenerId listenerId;

        public GrailsServiceActivatingHandler(Object object, String methodName, ListenerId listenerId) {
            super(object, methodName);
            this.listenerId = listenerId;
        }

        public GrailsServiceActivatingHandler(Object object, Method method, ListenerId listenerId) {
            super(object, method);
            this.listenerId = listenerId;
        }


        @Override
        protected Object handleRequestMessage(Message<?> message) {
            Object res = super.handleRequestMessage(message);
            if (res == null) {
                //Return TRUE if GORM event to bypass cancel
                //Else return FALSE for other events (never returning true)
                return new TrackableNullResult();
            }
            return res;
        }

        public ListenerId getListenerId() {
            return listenerId;
        }
    }
}
