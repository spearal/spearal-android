package org.spearal.android;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import org.spearal.SpearalContext;
import org.spearal.configuration.PartialObjectFactory;
import org.spearal.configuration.PropertyFactory.Property;
import org.spearal.impl.instantiator.ProxyInstantiator;

import com.google.dexmaker.stock.ProxyBuilder;


public class DexMakerPartialObjectFactory implements PartialObjectFactory {

	@Override
	public Object instantiatePartial(SpearalContext context, Class<?> cls, Property[] partialProperties)
		throws InstantiationException, IllegalAccessException {
		
		if (Proxy.isProxyClass(cls))
			return ProxyInstantiator.instantiatePartial(context, cls, partialProperties);
		
		try {
			return ProxyBuilder.forClass(cls)
					.implementing(ExtendedPartialObjectProxy.class)
					.handler(new PartialObjectProxyHandler(context, cls, partialProperties))
					.build();
		} 
		catch (IOException e) {
			throw new InstantiationException("Could not build proxy for class " + cls.getName() + ": " + e.getMessage());
		}
	}
	
	private static class PartialObjectProxyHandler implements InvocationHandler {

		private final Property[] allProperties;
		private final Map<String, Property> definedProperties;

		public PartialObjectProxyHandler(SpearalContext context, Class<?> cls, Property[] partialProperties) {
			this.allProperties = context.getProperties(cls);
			
			this.definedProperties = new HashMap<String, Property>(partialProperties.length);
			for (Property property : partialProperties) {
				if (property != null)
					this.definedProperties.put(property.getName(), property);
			}
		}

		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			// Proxy methods.
			if (method.getDeclaringClass() == PartialObjectProxy.class) {
				String name = method.getName();
				if ("$hasUndefinedProperties".equals(name))
					return Boolean.valueOf(definedProperties.size() < allProperties.length);
				if ("$isDefined".equals(name) && args.length == 1)
					return Boolean.valueOf(definedProperties.containsKey(args[0]));
				if ("$undefine".equals(name) && args.length == 1)
					return definedProperties.remove(args[0]);
				if ("$getDefinedProperties".equals(name))
					return definedProperties.values().toArray(new Property[definedProperties.size()]);
				if ("$getActualClass".equals(name))
					return proxy.getClass().getSuperclass();
				throw new UnsupportedOperationException("Internal error: " + method.toString());
			}
			
			// Setters.
			if (method.getParameterTypes().length == 1 && method.getReturnType() == void.class && method.getName().startsWith("set")) {
				for (Property property : allProperties) {
					if (method.equals(property.getSetter())) {
						ProxyBuilder.callSuper(proxy, method, args);
						definedProperties.put(property.getName(), property);
						return null;
					}
				}
				throw new UnsupportedOperationException("Internal error: " + method.toString());
			}
			
			// Getters.
			if (method.getParameterTypes().length == 0 && (method.getName().startsWith("get") || method.getName().startsWith("is"))) {
				for (Property property : definedProperties.values()) {
					if (method.equals(property.getGetter()))
						return ProxyBuilder.callSuper(proxy, method, args);
				}				
				throw new UndefinedPropertyException(method.toString());
			}
			
			return ProxyBuilder.callSuper(proxy, method, args);
		}
	}
}
