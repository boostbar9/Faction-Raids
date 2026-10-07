package com.devfarinsky.siegeoverhaul.nativecompat;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.event.VanillaGameEvent;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.ASMEventHandler;
import net.minecraftforge.eventbus.EventBus;
import net.minecraftforge.eventbus.IEventListenerFactory;
import net.minecraftforge.eventbus.ModLauncherFactory;
import net.minecraftforge.eventbus.api.EventListenerHelper;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventListener;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.devfarinsky.siegeoverhaul.nativecompat.NativeDirtIntrospection.raw;
import static com.devfarinsky.siegeoverhaul.nativecompat.NativeDirtPolicy.*;

/** Census only: no event posting/invocation, callback toString or modifier execution. */
final class NativeDirtListeners {
    static final List<Class<?>> EVENTS = List.of(LootTableLoadEvent.class, EntityJoinLevelEvent.class,
            EntityEvent.EntityConstructing.class, AttachCapabilitiesEvent.class,
            BlockEvent.NeighborNotifyEvent.class, VanillaGameEvent.class);
    private static final Class<?> SYNC_LIST = Collections.synchronizedList(new ArrayList<>()).getClass();
    record Read(List<ListenerProof> proof, List<Object> identity, List<Class<?>> dispatchClasses) {
        Read { proof = List.copyOf(proof); identity = List.copyOf(identity); dispatchClasses = List.copyOf(dispatchClasses); }
    }
    interface Origins { CodeOrigin read(Class<?> type) throws Exception; }

    static Read read(Origins origins) throws Exception {
        Object bus = MinecraftForge.EVENT_BUS;
        if (bus.getClass() != EventBus.class) throw new Unresolved("Unknown Forge bus implementation");
        int id = (Integer) raw(bus, EventBus.class, "busID", int.class, false);
        if (id < 0 || (Boolean) raw(bus, EventBus.class, "shutdown", boolean.class, false)) throw new Unresolved("Inactive Forge bus");
        Object factory = raw(bus, EventBus.class, "factory", IEventListenerFactory.class, false);
        if (factory == null || factory.getClass() != ModLauncherFactory.class) throw new Unresolved("Unaudited listener factory");
        Object rawOwners = raw(bus, EventBus.class, "listeners", ConcurrentHashMap.class, false);
        if (rawOwners == null || rawOwners.getClass() != ConcurrentHashMap.class) throw new Unresolved("Unknown listener owner map");
        Map<?, ?> owners = (Map<?, ?>) rawOwners;
        if (owners.size() > MAX_OWNERS) throw new Limit();
        IdentityHashMap<Object, Object> ownership = new IdentityHashMap<>();
        int ownerCount = 0;
        for (var entry : owners.entrySet()) {
            if (++ownerCount > MAX_OWNERS) throw new Limit();
            Object rawList = entry.getValue();
            if (rawList == null || rawList.getClass() != SYNC_LIST || entry.getKey() == null) throw new Unresolved("Unknown owner registration list");
            synchronized (rawList) {
                List<?> list = (List<?>) rawList;
                if (list.size() > MAX_OWNER_LISTENERS) throw new Limit();
                for (Object listener : list) {
                    if (listener == null || ownership.put(listener, entry.getKey()) != null) throw new Unresolved("Duplicate/unstable callback ownership");
                }
            }
        }
        Class<?> cacheType = Class.forName("net.minecraftforge.eventbus.internal.Cache", false, EventBus.class.getClassLoader());
        Object pending = raw(null, ModLauncherFactory.class, "PENDING", cacheType, true);
        Class<?> concurrentCache = Class.forName("net.minecraftforge.eventbus.internal.CacheConcurrent", false, EventBus.class.getClassLoader());
        if (pending == null || pending.getClass() != concurrentCache) throw new Unresolved("Unaudited callback cache");
        Object pendingMap = raw(pending, concurrentCache, "map", ConcurrentHashMap.class, false);
        if (pendingMap == null || pendingMap.getClass() != ConcurrentHashMap.class) throw new Unresolved("Unknown callback cache map");
        List<IEventListener[]> eventArrays = new ArrayList<>();
        List<ListenerProof> proofs = new ArrayList<>();
        List<Object> identities = new ArrayList<>();
        java.util.Set<Class<?>> dispatchClasses = new java.util.LinkedHashSet<>();
        identities.add(bus); identities.add(factory); identities.add(pending);
        Class<?> optimized = Class.forName("net.minecraftforge.eventbus.ASMEventHandler$1", false, ASMEventHandler.class.getClassLoader());
        for (Class<?> event : EVENTS) {
            IEventListener[] listeners = EventListenerHelper.getListenerList(event).getListeners(id);
            if (listeners.length > MAX_LISTENERS) throw new Limit();
            eventArrays.add(listeners.clone());
            IdentityHashMap<Object, Boolean> seen = new IdentityHashMap<>();
            for (IEventListener listener : listeners) {
                if (listener != null && listener.getClass() == EventPriority.class) continue;
                if (listener == null || seen.put(listener, Boolean.TRUE) != null) throw new Unresolved("Duplicate/null inherited callback");
                if (listener.getClass() != ASMEventHandler.class && listener.getClass() != optimized) throw new Unresolved("Unaudited callback form");
                Object target = ownership.get(listener);
                if (target == null) throw new Unresolved("Callback has no exact registered owner");
                Class<?> owner = target instanceof Class<?> c ? c : target.getClass();
                Object delegate = raw(listener, ASMEventHandler.class, "handler", IEventListener.class, false);
                Object annotation = raw(listener, ASMEventHandler.class, "subInfo", SubscribeEvent.class, false);
                Object filter = raw(listener, ASMEventHandler.class, "filter", Type.class, false);
                if (delegate == null || !(annotation instanceof SubscribeEvent sub) || (filter != null && !(filter instanceof Class<?>)))
                    throw new Unresolved("Unknown callback metadata");
                Object cached = ((Map<?, ?>) pendingMap).get(delegate.getClass().getName());
                if (!(cached instanceof Method method) || method.getDeclaringClass() != owner || method.getParameterCount() != 1
                        || !method.getParameterTypes()[0].isAssignableFrom(event) || method.getReturnType() != void.class
                        || !Modifier.isPublic(method.getModifiers()) || !sub.equals(method.getAnnotation(SubscribeEvent.class)))
                    throw new Unresolved("Callback does not match the actual factory Method");
                if (Modifier.isStatic(method.getModifiers()) != (target instanceof Class<?>)) throw new Unresolved("Callback target kind changed");
                Class<?> wrapper = delegate.getClass();
                dispatchClasses.add(listener.getClass()); dispatchClasses.add(wrapper);
                if (wrapper.getSuperclass() != Object.class || wrapper.getClassLoader() != owner.getClassLoader()
                        || wrapper.getInterfaces().length != 1 || wrapper.getInterfaces()[0] != IEventListener.class)
                    throw new Unresolved("Unknown generated callback shape");
                if (target instanceof Class<?>) {
                    if (wrapper.getDeclaredFields().length != 0) throw new Unresolved("Static callback wrapper changed");
                } else if (wrapper.getDeclaredFields().length != 1 || raw(delegate, wrapper, "instance", Object.class, false) != target) {
                    throw new Unresolved("Callback wrapper target changed");
                }
                String callback = owner.getName() + "#" + method.getName() + "(" + method.getParameterTypes()[0].getName() + ")void";
                proofs.add(new ListenerProof(event.getName(), callback, sub.priority().name(), sub.receiveCanceled(),
                        filter == null ? "*" : ((Class<?>) filter).getName(), origins.read(owner), origins.read(method.getDeclaringClass()),
                        listener.getClass().getName() + "/" + wrapper.getName()));
                identities.add(listener); identities.add(target); identities.add(delegate); identities.add(method);
            }
            // Concurrent registration must not be hidden by a cached/partial owner snapshot.
            IEventListener[] after = EventListenerHelper.getListenerList(event).getListeners(id);
            if (after.length != listeners.length) throw new Unresolved("Listener registration changed during census");
            for (int i = 0; i < after.length; i++) if (after[i] != listeners[i]) throw new Unresolved("Listener registration changed during census");
        }
        for (int e = 0; e < EVENTS.size(); e++) {
            IEventListener[] now = EventListenerHelper.getListenerList(EVENTS.get(e)).getListeners(id);
            IEventListener[] before = eventArrays.get(e);
            if (now.length != before.length) throw new Unresolved("Listener census changed during observation");
            for (int i = 0; i < now.length; i++) if (now[i] != before[i]) throw new Unresolved("Listener census changed during observation");
        }
        if (owners.size() != ownerCount) throw new Unresolved("Listener ownership changed during census");
        return new Read(proofs, identities, List.copyOf(dispatchClasses));
    }
    static final class Unresolved extends Exception { Unresolved(String message) { super(message); } }
    static final class Limit extends Exception {}
    private NativeDirtListeners() {}
}
