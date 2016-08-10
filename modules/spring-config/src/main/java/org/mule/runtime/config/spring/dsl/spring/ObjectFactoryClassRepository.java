/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.config.spring.dsl.spring;

import org.mule.runtime.config.spring.dsl.api.ComponentBuildingDefinition;
import org.mule.runtime.config.spring.dsl.api.ObjectFactory;
import org.mule.runtime.core.api.MuleRuntimeException;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import java.lang.reflect.Method;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.springframework.beans.factory.SmartFactoryBean;
import org.springframework.cglib.proxy.Callback;
import org.springframework.cglib.proxy.Enhancer;
import org.springframework.cglib.proxy.MethodInterceptor;
import org.springframework.cglib.proxy.MethodProxy;

public class ObjectFactoryClassRepository
{

    private Cache<ComponentBuildingDefinition, Class<ObjectFactory>> objectFactoryClassCache = CacheBuilder.newBuilder().build();


    public Class<ObjectFactory> getObjectFactoryClass(ComponentBuildingDefinition componentBuildingDefinition,
                                                      Class objectFactoryType,
                                                      Class createdObjectType,
                                                      Supplier<Boolean> isLazyInitFunction,
                                                      Consumer<Object> instancePostCreationFunction)
    {
        try
        {
            return objectFactoryClassCache.get(componentBuildingDefinition, () -> {
                /*
                   We need this to allow spring create the object using a FactoryBean but using the object factory setters and getters so
                   we create as FactoryBean a dynamic class that will have the same attributes and methods as the ObjectFactory that the user
                   defined. This way our API does not expose spring specific classes.
                */
                Enhancer enhancer = new Enhancer();
                //Use SmartFactoryBean since it's the only way to force spring to pre-instantiate FactoryBean for singletons
                enhancer.setInterfaces(new Class[] {SmartFactoryBean.class});
                enhancer.setSuperclass(objectFactoryType);
                enhancer.setCallbackType(MethodInterceptor.class);
                //If cache is used then the same class instance with the same callback instance will be repeated for the
                //same ObjectFactory which prevents reusing the same ObjectFactory class for different components
                enhancer.setUseCache(false);
                enhancer.setSerialVersionUID(new Random(System.currentTimeMillis()).nextLong());
                Class factoryBeanClass = enhancer.createClass();
                Enhancer.registerStaticCallbacks(factoryBeanClass, new Callback[] {
                        new MethodInterceptor()
                        {
                            @Override
                            public Object intercept(Object obj, Method method, Object[] args, MethodProxy proxy) throws Throwable
                            {
                                if (method.getName().equals("isSingleton"))
                                {
                                    return !componentBuildingDefinition.isPrototype();
                                }
                                if (method.getName().equals("getObjectType"))
                                {
                                    return createdObjectType;
                                }
                                if (method.getName().equals("getObject"))
                                {
                                    Object createdInstance = proxy.invokeSuper(obj, args);
                                    instancePostCreationFunction.accept(createdInstance);
                                    return createdInstance;
                                }
                                if (method.getName().equals("isPrototype"))
                                {
                                    return componentBuildingDefinition.isPrototype();
                                }
                                if (method.getName().equals("isEagerInit"))
                                {
                                    return !isLazyInitFunction.get();
                                }
                                return proxy.invokeSuper(obj, args);
                            }
                        }
                });
                return factoryBeanClass;
            });
        }
        catch (ExecutionException e)
        {
            throw new MuleRuntimeException(e);
        }
    }

}
