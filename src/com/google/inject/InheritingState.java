/**
 * Copyright (C) 2008 Google Inc.
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

import static com.google.common.base.Preconditions.checkNotNull;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.inject.internal.Errors;
import com.google.inject.internal.MatcherAndConverter;
import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Map;
import java.util.Collections;

/**
 * @author jessewilson@google.com (Jesse Wilson)
 */
class InheritingState implements State {

  // TODO(jessewilson): think about what we need to do w.r.t. concurrency

  private final State parent;
  private final Map<Key<?>, Binding<?>> explicitBindingsMutable = Maps.newHashMap();
  private final Map<Key<?>, Binding<?>> explicitBindings
      = Collections.unmodifiableMap(explicitBindingsMutable);
  private final Map<Class<? extends Annotation>, Scope> scopes = Maps.newHashMap();
  private final List<MatcherAndConverter> converters = Lists.newArrayList();
  private final List<MethodAspect> methodAspects = Lists.newArrayList();
  private final WeakKeySet blacklistedKeys = new WeakKeySet();

  InheritingState(State parent) {
    this.parent = checkNotNull(parent, "parent");
  }

  public State parent() {
    return parent;
  }

  @SuppressWarnings("unchecked") // we only put in BindingImpls that match their key types
  public <T> BindingImpl<T> getExplicitBinding(Key<T> key) {
    Binding<?> binding = explicitBindings.get(key);
    return binding != null ? (BindingImpl<T>) binding : parent.getExplicitBinding(key);
  }

  public Map<Key<?>, Binding<?>> getExplicitBindingsThisLevel() {
    return explicitBindings;
  }

  public void putBinding(Key<?> key, BindingImpl<?> binding) {
    explicitBindingsMutable.put(key, binding);
  }

  public Scope getScope(Class<? extends Annotation> annotationType) {
    Scope scope = scopes.get(annotationType);
    return scope != null ? scope : parent.getScope(annotationType);
  }

  public void putAnnotation(Class<? extends Annotation> annotationType, Scope scope) {
    scopes.put(annotationType, scope);
  }

  public Iterable<MatcherAndConverter> getConvertersThisLevel() {
    return converters;
  }

  public void addConverter(MatcherAndConverter matcherAndConverter) {
    converters.add(matcherAndConverter);
  }

  public MatcherAndConverter getConverter(
      String stringValue, TypeLiteral<?> type, Errors errors, Object source) {
    MatcherAndConverter matchingConverter = null;
    for (State s = this; s != State.NONE; s = s.parent()) {
      for (MatcherAndConverter converter : s.getConvertersThisLevel()) {
        if (converter.getTypeMatcher().matches(type)) {
          if (matchingConverter != null) {
            errors.ambiguousTypeConversion(stringValue, source, type, matchingConverter, converter);
          }
          matchingConverter = converter;
        }
      }
    }
    return matchingConverter;
  }

  public void addMethodAspect(MethodAspect methodAspect) {
    methodAspects.add(methodAspect);
  }

  public List<MethodAspect> getMethodAspects() {
    List<MethodAspect> result = Lists.newArrayList();
    result.addAll(parent.getMethodAspects());
    result.addAll(methodAspects);
    return result;
  }

  public void blacklist(Key<?> key) {
    parent.blacklist(key);
    blacklistedKeys.add(key);
  }

  public boolean isBlacklisted(Key<?> key) {
    return blacklistedKeys.contains(key);
  }
}
