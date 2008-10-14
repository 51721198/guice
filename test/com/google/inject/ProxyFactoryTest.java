
/**
 * Copyright (C) 2006 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.inject;

import com.google.common.collect.Lists;
import com.google.inject.internal.Errors;
import com.google.inject.internal.ErrorsException;
import static com.google.inject.matcher.Matchers.annotatedWith;
import static com.google.inject.matcher.Matchers.any;
import static com.google.inject.matcher.Matchers.not;
import static com.google.inject.matcher.Matchers.only;
import com.google.inject.spi.InjectionPoint;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import junit.framework.TestCase;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

/**
 * @author crazybob@google.com (Bob Lee)
 */
public class ProxyFactoryTest extends TestCase {

  List<MethodAspect> aspects = Lists.newArrayList();

  public void testSimpleCase()
      throws NoSuchMethodException, InvocationTargetException, ErrorsException {
    SimpleInterceptor interceptor = new SimpleInterceptor();

    aspects.add(new MethodAspect(any(), any(), interceptor));
    ProxyFactory factory = new ProxyFactory(aspects);

    ConstructionProxy<Simple> constructionProxy = factory
        .createConstructionProxy(new Errors(), InjectionPoint.forConstructorOf(Simple.class));

    Simple simple = constructionProxy.newInstance();
    simple.invoke();
    assertTrue(simple.invoked);
    assertTrue(interceptor.invoked);
  }

  static class Simple {
    boolean invoked = false;
    public void invoke() {
      invoked = true;
    }
  }

  static class SimpleInterceptor implements MethodInterceptor {

    boolean invoked = false;

    public Object invoke(MethodInvocation methodInvocation) throws Throwable {
      invoked = true;
      return methodInvocation.proceed();
    }
  }

  public void testInterceptOneMethod()
      throws NoSuchMethodException, InvocationTargetException, ErrorsException {
    SimpleInterceptor interceptor = new SimpleInterceptor();

    aspects.add(new MethodAspect(only(Bar.class), annotatedWith(Intercept.class), interceptor));
    ProxyFactory factory = new ProxyFactory(aspects);

    ConstructionProxy<Foo> fooFactory =
        factory.get(new Errors(), InjectionPoint.forConstructorOf(Foo.class));
    ConstructionProxy<Bar> barFactory =
        factory.get(new Errors(), InjectionPoint.forConstructorOf(Bar.class));

    Foo foo = fooFactory.newInstance();
    Bar bar = barFactory.newInstance();

    foo.foo();
    assertTrue(foo.fooCalled);
    assertFalse(interceptor.invoked);

    bar.bar();
    assertTrue(bar.barCalled);
    assertFalse(interceptor.invoked);

    bar.intercepted();
    assertTrue(bar.interceptedCalled);
    assertTrue(interceptor.invoked);
  }

  static class Foo {
    boolean fooCalled;
    @Intercept
    void foo() {
      fooCalled = true;
    }
  }

  static class Bar {

    boolean barCalled;
    void bar() {
      barCalled = true;
    }

    boolean interceptedCalled;

    @Intercept
    void intercepted() {
      interceptedCalled = true;
    }
  }

  @Retention(RetentionPolicy.RUNTIME)
  @interface Intercept {}

  public void testWithConstructorArguments()
      throws InvocationTargetException, NoSuchMethodException, ErrorsException {
    SimpleInterceptor interceptor = new SimpleInterceptor();

    aspects.add(new MethodAspect(any(), any(), interceptor));
    ProxyFactory factory = new ProxyFactory(aspects);

    ConstructionProxy<A> constructor =
        factory.get(new Errors(), InjectionPoint.forConstructorOf(A.class));

    A a = constructor.newInstance(5);
    a.a();
    assertEquals(5, a.i);
  }

  public void testNotProxied()
      throws NoSuchMethodException, InvocationTargetException, ErrorsException {
    SimpleInterceptor interceptor = new SimpleInterceptor();

    aspects.add(new MethodAspect(not(any()), not(any()), interceptor));
    ProxyFactory factory = new ProxyFactory(aspects);

    ConstructionProxy<A> constructor =
        factory.get(new Errors(), InjectionPoint.forConstructorOf(A.class));

    A a = constructor.newInstance(5);
    assertEquals(A.class, a.getClass());
  }

  static class A {
    final int i;
    @Inject public A(int i) {
      this.i = i;
    }
    public void a() {}
  }

  public void testMultipleInterceptors()
      throws NoSuchMethodException, InvocationTargetException, ErrorsException {
    DoubleInterceptor doubleInterceptor = new DoubleInterceptor();
    CountingInterceptor countingInterceptor = new CountingInterceptor();

    aspects.add(new MethodAspect(any(), any(), doubleInterceptor, countingInterceptor));
    ProxyFactory factory = new ProxyFactory(aspects);

    ConstructionProxy<Counter> constructor =
        factory.get(new Errors(), InjectionPoint.forConstructorOf(Counter.class));

    Counter counter = constructor.newInstance();
    counter.inc();
    assertEquals(2, counter.count);
    assertEquals(2, countingInterceptor.count);
  }

  static class CountingInterceptor implements MethodInterceptor {

    int count;

    public Object invoke(MethodInvocation methodInvocation) throws Throwable {
      count++;
      return methodInvocation.proceed();
    }
  }

  static class DoubleInterceptor implements MethodInterceptor {

    public Object invoke(MethodInvocation methodInvocation) throws Throwable {
      methodInvocation.proceed();
      return methodInvocation.proceed();
    }
  }

  static class Counter {
    int count;
    void inc() {
      count++;
    }
  }
}
