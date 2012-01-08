package org.grails.plugin.platform.events.registry;

import org.apache.log4j.Logger;
import org.grails.plugin.platform.events.dispatcher.SprintIntegrationEventsDispatcher;
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
import org.springframework.integration.handler.ServiceActivatingHandler;
import org.springframework.integration.support.channel.BeanFactoryChannelResolver;
import org.springframework.util.ReflectionUtils;

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
    private BeanFactoryChannelResolver resolver;
    private MessageChannel outputChannel;
    private ChannelInterceptor interceptor;

    public void setInterceptor(ChannelInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    public void setOutputChannel(MessageChannel outputChannel) {
        this.outputChannel = outputChannel;
    }

    private PublishSubscribeChannel getOrCreateChannel(String topic) {
        if (topic == null || topic.isEmpty()) {
            throw new RuntimeException("topic name must not be null or empty");
        }

        String channelName = GRAILS_TOPIC_PREFIX + topic;
        PublishSubscribeChannel channel = null;

        try {
            channel = ctx.getBean(channelName, PublishSubscribeChannel.class);
        } catch (BeansException be) {
            log.debug("creating channel because " + be.getMessage());
        }

        if (channel == null) {
            channel = new PublishSubscribeChannel();
            channel.setApplySequence(true);
            channel.setBeanName(channelName);
            channel.addInterceptor(interceptor);
            beanFactory.registerSingleton(channelName, channel);
        }
        return channel;
    }

    private void registerHandler(Object bean, Method callback, String topic) {
        Object target = bean;

        //todo expose param to let the listener traversing proxies (like tx)
        if (bean instanceof Advised) {
            try {
                target = ((Advised) bean).getTargetSource().getTarget();
            } catch (Exception e) {
                log.error("failed to retrieve bean origin from proxy", e);
            }
        }

        ServiceActivatingHandler serviceActivatingHandler =
                new GrailsServiceActivatingHandler(target, callback);

        serviceActivatingHandler.setBeanName(target.getClass().getName() + "#" + callback.getName());
        serviceActivatingHandler.setChannelResolver(resolver);
        serviceActivatingHandler.setRequiresReply(true);
        serviceActivatingHandler.setOutputChannel(outputChannel);
        PublishSubscribeChannel channel = getOrCreateChannel(topic);
        channel.subscribe(serviceActivatingHandler);
    }

    public void addListener(String topic, Object bean, String callbackName) {
        registerHandler(bean, ReflectionUtils.findMethod(bean.getClass(), callbackName), topic);
    }

    public void addListener(String topic, Object bean, Method callback) {
        registerHandler(bean, callback, topic);
    }

    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.ctx = applicationContext;
    }

    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = (ConfigurableBeanFactory) beanFactory;
        this.resolver = new BeanFactoryChannelResolver(beanFactory);
    }

    private class GrailsServiceActivatingHandler extends ServiceActivatingHandler {

        public GrailsServiceActivatingHandler(Object object, Method method) {
            super(object, method);
        }

        @Override
        protected Object handleRequestMessage(Message<?> message) {
            Object res = super.handleRequestMessage(message);
            if(res == null){
                //Return TRUE if GORM event to bypass cancel
                //Else return FALSE for other events (never returning true)
                return message.getHeaders().get(SprintIntegrationEventsDispatcher.GORM_EVENT_KEY) != null;
            }
            return res;
        }
    }
}
