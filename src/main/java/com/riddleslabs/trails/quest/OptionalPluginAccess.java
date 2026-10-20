package com.riddleslabs.trails.quest;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/** Small reflective boundary keeps optional plugin APIs out of the runtime dependency graph. */
final class OptionalPluginAccess {
    private OptionalPluginAccess() {}
    // ClassValue follows the optional plugin's classloader lifetime without retaining plugin instances.
    private static final Method[] NO_METHODS=new Method[0];
    private static final ClassValue<Map<String,Method[]>> METHODS=new ClassValue<>() {
        @Override protected Map<String,Method[]> computeValue(Class<?> type) {
            Map<String,List<Method>> grouped=new HashMap<>();
            for(Method method:type.getMethods())grouped.computeIfAbsent(method.getName(),ignored->new ArrayList<>()).add(method);
            Map<String,Method[]> result=new HashMap<>();
            grouped.forEach((name,methods)->result.put(name,methods.toArray(Method[]::new)));
            return Map.copyOf(result);
        }
    };

    static Class<?> type(String pluginName, String className) throws ReflectiveOperationException {
        Plugin plugin = Bukkit.getPluginManager().getPlugin(pluginName);
        if (plugin == null || !plugin.isEnabled()) throw new IllegalStateException(pluginName + " is unavailable");
        return Class.forName(className, true, plugin.getClass().getClassLoader());
    }

    static Object call(Object receiver, String name, Object... args) throws ReflectiveOperationException {
        Class<?> type = receiver instanceof Class<?> c ? c : receiver.getClass();
        Method method=null;
        for(Method candidate:METHODS.get(type).getOrDefault(name,NO_METHODS))
            if(candidate.getParameterCount()==args.length && matches(candidate.getParameterTypes(),args)){method=candidate;break;}
        if(method==null)throw new NoSuchMethodException(type.getName()+"."+name);
        try {
            return method.invoke(receiver instanceof Class<?> ? null : receiver, args);
        } catch (InvocationTargetException ex) {
            throw new ReflectiveOperationException(name + " failed", ex.getCause());
        }
    }

    private static boolean matches(Class<?>[] types, Object[] args) {
        for (int i = 0; i < types.length; i++) {
            Class<?> type = types[i] == int.class ? Integer.class : types[i];
            if (args[i] != null && !type.isInstance(args[i])) return false;
        }
        return true;
    }
}
