/*
 * Copyright 2011 JBoss, by Red Hat, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.errai.ioc.rebind.ioc.injector;

import static org.jboss.errai.codegen.builder.impl.ObjectBuilder.newInstanceOf;
import static org.jboss.errai.codegen.meta.MetaClassFactory.parameterizedAs;
import static org.jboss.errai.codegen.meta.MetaClassFactory.typeParametersOf;
import static org.jboss.errai.codegen.util.Stmt.declareVariable;
import static org.jboss.errai.codegen.util.Stmt.load;
import static org.jboss.errai.codegen.util.Stmt.loadVariable;
import static org.jboss.errai.ioc.rebind.ioc.injector.InjectUtil.getConstructionStrategy;

import org.jboss.errai.codegen.Modifier;
import org.jboss.errai.codegen.Parameter;
import org.jboss.errai.codegen.Statement;
import org.jboss.errai.codegen.builder.AnonymousClassStructureBuilder;
import org.jboss.errai.codegen.builder.BlockBuilder;
import org.jboss.errai.codegen.meta.MetaClass;
import org.jboss.errai.codegen.util.Refs;
import org.jboss.errai.ioc.client.api.qualifiers.BuiltInQualifiers;
import org.jboss.errai.ioc.client.container.CreationalCallback;
import org.jboss.errai.ioc.client.container.CreationalContext;
import org.jboss.errai.ioc.rebind.ioc.bootstrapper.IOCProcessingContext;
import org.jboss.errai.ioc.rebind.ioc.exception.InjectionFailure;
import org.jboss.errai.ioc.rebind.ioc.injector.api.ConstructionStatusCallback;
import org.jboss.errai.ioc.rebind.ioc.injector.api.InjectableInstance;
import org.jboss.errai.ioc.rebind.ioc.injector.api.InjectionContext;
import org.jboss.errai.ioc.rebind.ioc.injector.api.WiringElementType;

import javax.enterprise.inject.Specializes;
import javax.inject.Named;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * This injector implementation is responsible for the lion's share of the container's workload. It is responsible
 * for generating the <tt>CreationalContext</tt>'s which produce instances of beans. It is also responsible for
 * handling the differences in semantics between singleton and dependent-scoped beans.
 *
 * @author Mike Brock
 */
public class TypeInjector extends AbstractInjector {
  protected final MetaClass type;
  protected String instanceVarName;

  public TypeInjector(final MetaClass type, final InjectionContext context) {
    this.type = type;

    if (!context.isReachable(type)) {
      disableSoftly();
    }

    // check to see if this is a singleton and/or alternative bean
    this.testMock = context.isElementType(WiringElementType.TestMockBean, type);
    this.singleton = context.isElementType(WiringElementType.SingletonBean, type);
    this.alternative = context.isElementType(WiringElementType.AlternativeBean, type);

    this.instanceVarName = InjectUtil.getNewInjectorName().concat("_").concat(type.getName());

    final Set<Annotation> qualifiers = new HashSet<Annotation>();
    qualifiers.addAll(InjectUtil.getQualifiersFromAnnotations(type.getAnnotations()));

    if (qualifiers.isEmpty()) {
      qualifiers.add(BuiltInQualifiers.DEFAULT_INSTANCE);
    }

    qualifiers.add(BuiltInQualifiers.ANY_INSTANCE);

    if (type.isAnnotationPresent(Specializes.class)) {
      qualifiers.addAll(makeSpecialized(context));
    }

    if (type.isAnnotationPresent(Named.class)) {
      final Named namedAnnotation = type.getAnnotation(Named.class);

      this.beanName = namedAnnotation.value().equals("")
          ? type.getBeanDescriptor().getBeanName() : namedAnnotation.value();
    }

    if (!qualifiers.isEmpty()) {
      qualifyingMetadata = context.getProcessingContext().getQualifyingMetadataFactory()
          .createFrom(qualifiers.toArray(new Annotation[qualifiers.size()]));

    }
    else {
      qualifyingMetadata = context.getProcessingContext().getQualifyingMetadataFactory().createDefaultMetadata();
    }
  }

  @Override
  public Statement getBeanInstance(final InjectableInstance injectableInstance) {
    final Statement val = _getType(injectableInstance);
    registerWithBeanManager(injectableInstance.getInjectionContext(), val);
    return val;
  }

  private Statement _getType(final InjectableInstance injectableInstance) {
    // check to see if this injector has already been injected
    if (isRendered()) {
      if (isSingleton() && !hasNewQualifier(injectableInstance)) {

        /*
        if this bean is a singleton bean and there is no @New qualifier on the site we're injecting
        into, we merely return a reference to the singleton instance variable from the bootstrapper.
        */
        return Refs.get(instanceVarName);
      }
      else if (creationalCallbackVarName != null) {

        /*
        if the bean is not singleton, or it's scope is overridden to be effectively dependent,
        we return a call CreationContext.getInstance() on the CreationalContext for this injector.
        */
        return loadVariable(creationalCallbackVarName).invoke("getInstance", Refs.get("context"));
      }
    }

    final InjectionContext injectContext = injectableInstance.getInjectionContext();
    final IOCProcessingContext ctx = injectContext.getProcessingContext();

    /*
    get a parameterized version of the CreationalCallback class, parameterized with the type of
    bean it produces.
    */
    final MetaClass creationCallbackRef = parameterizedAs(CreationalCallback.class, typeParametersOf(type));

    /*
    begin building the creational callback, implement the "getInstance" method from the interface
    and assign its BlockBuilder to a callbackBuilder so we can work with it.
    */
    final BlockBuilder<AnonymousClassStructureBuilder> callbackBuilder
        = newInstanceOf(creationCallbackRef).extend()
        .publicOverridesMethod("getInstance", Parameter.of(CreationalContext.class, "context", true));

    /* push the method block builder onto the stack, so injection tasks are rendered appropriately. */
    ctx.pushBlockBuilder(callbackBuilder);

    /* get a new unique variable for the creational callback */
    creationalCallbackVarName = InjectUtil.getNewInjectorName().concat("_")
        .concat(type.getName()).concat("_creational");

    /* get the construction strategy and execute it to wire the bean */
    getConstructionStrategy(this, injectContext).generateConstructor(new ConstructionStatusCallback() {
      @Override
      public void beanConstructed() {
        /* the bean has been constructed, so get a reference to the BeanRef and set it to the 'beanRef' variable. */

        /* add the bean to CreationalContext */
        callbackBuilder.append(
            loadVariable("context").invoke("addBean", loadVariable("context").invoke("getBeanReference", load(type),
                load(qualifyingMetadata.getQualifiers())), Refs.get(instanceVarName))
        );

        /* mark this injector as injected so we don't go into a loop if there is a cycle. */
        setCreated(true);
      }
    });

    /*
    return the instance of the bean from the creational callback.
    */
    callbackBuilder.append(loadVariable(instanceVarName).returnValue());

    /* pop the block builder of the stack now that we're done wiring. */
    ctx.popBlockBuilder();

    /*
    declare a final variable for the CreationalCallback and initialize it with the anonymous class we just
    built.
    */
    ctx.getBootstrapBuilder().privateField(creationalCallbackVarName, creationCallbackRef).modifiers(Modifier.Final)
        .initializesWith(callbackBuilder.finish().finish()).finish();

    final Statement retVal;

    if (isSingleton()) {
      /*
       if the injector is for a singleton, we create a variable to hold the singleton reference in the bootstrapper
       method and assign it with CreationalContext.getInstance().
       */
      ctx.getBootstrapBuilder().privateField(instanceVarName, type).modifiers(Modifier.Final)
          .initializesWith(loadVariable(creationalCallbackVarName).invoke("getInstance", Refs.get("context"))).finish();

      /*
       use the variable we just assigned as the return value for this injector.
       */
      retVal = Refs.get(instanceVarName);
    }
    else {
      /*
       the injector is a dependent scope, so use CreationContext.getInstance() as the return value.
       */
      retVal = loadVariable(creationalCallbackVarName).invoke("getInstance", Refs.get("context"));
    }

    setRendered(true);
    markRendered(injectableInstance);

    /*
      notify any component waiting for this type that is is ready now.
     */
    injectableInstance.getInjectionContext().getProcessingContext()
        .handleDiscoveryOfType(injectableInstance);

    injectContext.markProxyClosedIfNeeded(getInjectedType(), getQualifyingMetadata());
    /*
      return the reference to this bean to whoever called us.
     */
    return retVal;
  }

  private Set<Annotation> makeSpecialized(final InjectionContext context) {
    final MetaClass type = getInjectedType();

    if (type.getSuperClass().getFullyQualifiedName().equals(Object.class.getName())) {
      throw new InjectionFailure("the specialized bean " + type.getFullyQualifiedName() + " must directly inherit "
          + "from another bean");
    }

    final Set<Annotation> qualifiers = new HashSet<Annotation>();

    MetaClass cls = type;
    while ((cls = cls.getSuperClass()) != null && !cls.getFullyQualifiedName().equals(Object.class.getName())) {
      if (!context.hasInjectorForType(cls)) {
        context.addType(cls);
      }

      context.declareOverridden(cls);

      final List<Injector> injectors = context.getInjectors(cls);

      for (final Injector inj : injectors) {
        if (this.beanName == null) {
          this.beanName = inj.getBeanName();
        }

        inj.setEnabled(false);
        qualifiers.addAll(Arrays.asList(inj.getQualifyingMetadata().getQualifiers()));
      }
    }

    return qualifiers;
  }

  public boolean isPseudo() {
    return replaceable;
  }

  @Override
  public String getInstanceVarName() {
    return instanceVarName;
  }

  @Override
  public MetaClass getInjectedType() {
    return type;
  }

  public String getCreationalCallbackVarName() {
    return creationalCallbackVarName;
  }
}
