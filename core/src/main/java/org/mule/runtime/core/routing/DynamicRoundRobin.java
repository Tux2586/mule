/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.routing;

import org.mule.runtime.core.api.MuleContext;
import org.mule.runtime.core.api.MuleEvent;
import org.mule.runtime.core.api.MuleException;
import org.mule.runtime.core.api.context.MuleContextAware;
import org.mule.runtime.core.api.lifecycle.Initialisable;
import org.mule.runtime.core.api.lifecycle.InitialisationException;
import org.mule.runtime.core.api.processor.MessageProcessor;


/**
 * RoundRobin divides the messages it receives among its target routes in round-robin fashion. The set of routes is obtained
 * dynamically using a {@link org.mule.runtime.core.routing.DynamicRouteResolver}
 * <p/>
 * This includes messages received on all threads, so there is no guarantee that messages received from a splitter are sent to
 * consecutively numbered targets.
 */
public class DynamicRoundRobin implements MessageProcessor, Initialisable, MuleContextAware {

  private RoundRobinRoutingStrategy routingStrategy;
  private MuleContext muleContext;
  private DynamicRouteResolver dynamicRouteResolver;

  @Override
  public MuleEvent process(MuleEvent event) throws MuleException {
    return routingStrategy.route(event, dynamicRouteResolver.resolveRoutes(event));
  }

  @Override
  public void initialise() throws InitialisationException {
    routingStrategy = new RoundRobinRoutingStrategy(muleContext, new DynamicRouteResolverAdapter(dynamicRouteResolver));
  }

  @Override
  public void setMuleContext(MuleContext context) {
    this.muleContext = context;
  }

  /**
   * @param dynamicRouteResolver custom identifiable route resolver to use
   */
  public void setDynamicRouteResolver(DynamicRouteResolver dynamicRouteResolver) {
    this.dynamicRouteResolver = dynamicRouteResolver;
  }
}
