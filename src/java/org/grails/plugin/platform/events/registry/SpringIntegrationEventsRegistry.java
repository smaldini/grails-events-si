package org.grails.plugin.platform.events.registry;

import org.apache.log4j.Logger;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.integration.channel.PublishSubscribeChannel;
import org.springframework.integration.handler.ServiceActivatingHandler;

import java.lang.reflect.Method;

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

    private PublishSubscribeChannel getOrCreateChannel(String topic) {
        if (topic == null || topic.isEmpty()) {
            throw new RuntimeException("topic name must not be null or empty");
        }

        String channelName = GRAILS_TOPIC_PREFIX + topic;
        PublishSubscribeChannel channel = null;

        try{
            channel = ctx.getBean(channelName, PublishSubscribeChannel.class);
        }catch(BeansException be){
            log.debug("creating channel because "+be.getMessage());
        }

        if (channel == null) {
            channel = new PublishSubscribeChannel();
            channel.setBeanName(channelName);
            beanFactory.registerSingleton(channelName, channel);
        }
        return channel;
    }

    public void addListener(String topic, Object bean, String callbackName) {
        PublishSubscribeChannel channel = getOrCreateChannel(topic);
        ServiceActivatingHandler serviceActivatingHandler = new ServiceActivatingHandler(bean, callbackName);
        channel.subscribe(serviceActivatingHandler);
    }

    public void addListener(String topic, Object bean, Method callback) {
        PublishSubscribeChannel channel = getOrCreateChannel(topic);
        ServiceActivatingHandler serviceActivatingHandler = new ServiceActivatingHandler(bean, callback);
        channel.subscribe(serviceActivatingHandler);
    }

    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.ctx = applicationContext;
    }

    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = (ConfigurableBeanFactory) beanFactory;
    }
}
