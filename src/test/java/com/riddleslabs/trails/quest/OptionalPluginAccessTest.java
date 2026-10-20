package com.riddleslabs.trails.quest;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OptionalPluginAccessTest {
    public static class Provider {
        public String lookup(int id){return "npc:"+id;}
        public String lookup(String name){return "name:"+name;}
        public static String registry(){return "registry";}
        public void broken(){throw new IllegalStateException("provider failed");}
    }
    @Test void cachedMethodDiscoveryPreservesOverloadsStaticMethodsAndErrors() throws Exception {
        Provider provider=new Provider();
        for(int i=0;i<10;i++) {
            assertEquals("npc:"+i,OptionalPluginAccess.call(provider,"lookup",i));
            assertEquals("name:Ada",OptionalPluginAccess.call(provider,"lookup","Ada"));
        }
        assertEquals("registry",OptionalPluginAccess.call(Provider.class,"registry"));
        assertThrows(NoSuchMethodException.class,()->OptionalPluginAccess.call(provider,"lookup",new Object()));
        assertThrows(NoSuchMethodException.class,()->OptionalPluginAccess.call(provider,"missing"));
        assertInstanceOf(IllegalStateException.class,assertThrows(ReflectiveOperationException.class,()->OptionalPluginAccess.call(provider,"broken")).getCause());
    }
}
