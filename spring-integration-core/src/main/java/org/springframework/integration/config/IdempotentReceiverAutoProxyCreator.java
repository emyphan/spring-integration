/*
 * Copyright 2014 the original author or authors.
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

package org.springframework.integration.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.aop.Advisor;
import org.springframework.aop.TargetSource;
import org.springframework.aop.framework.autoproxy.AbstractAutoProxyCreator;
import org.springframework.aop.support.DefaultBeanFactoryPointcutAdvisor;
import org.springframework.aop.support.NameMatchMethodPointcut;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.messaging.MessageHandler;
import org.springframework.util.Assert;
import org.springframework.util.PatternMatchUtils;

/**
 * The {@link AbstractAutoProxyCreator} implementation that applies {@code IdempotentReceiverInterceptor}s
 * to {@link MessageHandler}s mapped by their {@code endpoint beanName}.
 *
 * @author Artem Bilan
 * @since 4.1
 */
@SuppressWarnings("serial")
class IdempotentReceiverAutoProxyCreator extends AbstractAutoProxyCreator {

	private List<Map<String, String>> idempotentEndpointsMapping;

	private Map<String, List<String>> idempotentEndpoints;

	public void setIdempotentEndpointsMapping(List<Map<String, String>> idempotentEndpointsMapping) {
		Assert.notEmpty(idempotentEndpointsMapping);
		this.idempotentEndpointsMapping = idempotentEndpointsMapping;
	}

	@Override
	protected Object[] getAdvicesAndAdvisorsForBean(Class<?> beanClass, String beanName,
			TargetSource customTargetSource) throws BeansException {
		initIdempotentEndpointsIfNecessary();

		if (MessageHandler.class.isAssignableFrom(beanClass)) {
			List<Advisor> interceptors = new ArrayList<Advisor>();
			for (Map.Entry<String, List<String>> entry : this.idempotentEndpoints.entrySet()) {
				List<String> mappedNames = entry.getValue();
				for (String mappedName : mappedNames) {
					String pattern = mappedName + IntegrationConfigUtils.HANDLER_ALIAS_SUFFIX;
					if (isMatch(pattern, beanName)) {
						DefaultBeanFactoryPointcutAdvisor idempotentReceiverInterceptor
								= new DefaultBeanFactoryPointcutAdvisor();
						idempotentReceiverInterceptor.setAdviceBeanName(entry.getKey());
						NameMatchMethodPointcut pointcut = new NameMatchMethodPointcut();
						pointcut.setMappedName("handleMessage");
						idempotentReceiverInterceptor.setPointcut(pointcut);
						idempotentReceiverInterceptor.setBeanFactory(getBeanFactory());
						interceptors.add(idempotentReceiverInterceptor);
					}
				}
			}
			if (!interceptors.isEmpty()) {
				return interceptors.toArray();
			}
		}
		return DO_NOT_PROXY;
	}

	private void initIdempotentEndpointsIfNecessary() {
		if (this.idempotentEndpoints == null) {
			synchronized (this) {
				if (this.idempotentEndpoints == null) {
					this.idempotentEndpoints = new LinkedHashMap<String, List<String>>();
					for (Map<String, String> mapping : this.idempotentEndpointsMapping) {
						Assert.isTrue(mapping.size() == 1, "The 'idempotentEndpointMapping' must be a SingletonMap");
						String interceptor = mapping.keySet().iterator().next();
						String endpoint = mapping.values().iterator().next();
						Assert.hasText(interceptor, "The 'idempotentReceiverInterceptor' can't be empty String");
						Assert.hasText(endpoint, "The 'idempotentReceiverEndpoint' can't be empty String");
						List<String> endpoints = this.idempotentEndpoints.get(interceptor);
						if (endpoints == null) {
							endpoints = new ArrayList<String>();
							this.idempotentEndpoints.put(interceptor, endpoints);
						}
						endpoints.add(endpoint);
					}
				}
			}
		}
	}

	private boolean isMatch(String mappedName, String beanName) {
		boolean matched = PatternMatchUtils.simpleMatch(mappedName, beanName);
		if (!matched) {
			BeanFactory beanFactory = getBeanFactory();
			if (beanFactory != null) {
				String[] aliases = beanFactory.getAliases(beanName);
				for (String alias : aliases) {
					matched = PatternMatchUtils.simpleMatch(mappedName, alias);
					if (matched) {
						break;
					}
				}
			}
		}
		return matched;
	}

}
