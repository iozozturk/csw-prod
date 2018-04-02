package csw.services.event.internal.redis;

import akka.actor.ActorSystem;
import akka.actor.Cancellable;
import akka.japi.Pair;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Sink;
import csw.messages.commons.CoordinatedShutdownReasons;
import csw.messages.events.*;
import csw.messages.params.models.Prefix;
import csw.services.event.JRedisFactory;
import csw.services.event.helpers.RegistrationFactory;
import csw.services.event.helpers.Utils;
import csw.services.event.internal.commons.EventServiceConnection;
import csw.services.event.internal.commons.Wiring;
import csw.services.event.javadsl.IEventPublisher;
import csw.services.event.javadsl.IEventSubscriber;
import csw.services.event.javadsl.IEventSubscription;
import csw.services.location.commons.ClusterAwareSettings;
import csw.services.location.commons.ClusterSettings;
import csw.services.location.models.TcpRegistration;
import csw.services.location.scaladsl.LocationService;
import csw.services.location.scaladsl.LocationServiceFactory;
import io.lettuce.core.RedisClient;
import org.junit.*;
import redis.embedded.RedisServer;
import scala.concurrent.Await;
import scala.concurrent.duration.FiniteDuration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class JPubSubTest {
    private static int seedPort = 3562;
    private static int redisPort = 6379;

    private static ClusterSettings clusterSettings;

    private static RedisServer redis;
    private static RedisClient redisClient;
    private static Wiring wiring;
    private static IEventPublisher publisher;
    private static IEventSubscriber subscriber;

    private int counter = -1;
    private Cancellable cancellable;

    @BeforeClass
    public static void beforeClass() throws Exception {
        clusterSettings = ClusterAwareSettings.joinLocal(seedPort);
        redis = RedisServer.builder().setting("bind " + clusterSettings.hostname()).port(redisPort).build();

        TcpRegistration tcpRegistration = RegistrationFactory.tcp(EventServiceConnection.value(), redisPort);
        LocationService locationService = LocationServiceFactory.withSettings(ClusterAwareSettings.onPort(seedPort));
        Await.result(locationService.register(tcpRegistration), new FiniteDuration(10, TimeUnit.SECONDS));

        redisClient = RedisClient.create();
        ActorSystem actorSystem = clusterSettings.system();
        wiring = new Wiring(actorSystem);
        JRedisFactory redisFactory = new JRedisFactory(redisClient, locationService, wiring);

        publisher = redisFactory.publisher().get();
        subscriber = redisFactory.subscriber().get();

        redis.start();
    }

    @AfterClass
    public static void afterClass() throws Exception {
        redisClient.shutdown();
        redis.stop();
        Await.result(wiring.shutdown(CoordinatedShutdownReasons.TestFinishedReason$.MODULE$), new FiniteDuration(10, TimeUnit.SECONDS));
    }

    @Test
    public void shouldBeAbleToPublishAndSubscribeAnEvent() throws InterruptedException, TimeoutException, ExecutionException {
        Event event1 = Utils.makeDistinctEvent(1);
        EventKey eventKey = event1.eventKey();

        java.util.Set<EventKey> set = new HashSet<>();
        set.add(eventKey);

        Pair<IEventSubscription, CompletionStage<List<Event>>> pair = subscriber.subscribe(set).toMat(Sink.seq(), Keep.both()).run(wiring.resumingMat());
        Thread.sleep(2000);

        publisher.publish(event1).get();
        Thread.sleep(1000);

        pair.first().unsubscribe().get(10, TimeUnit.SECONDS);

        List<Event> expectedEvents = new ArrayList<>();
        expectedEvents.add(Event$.MODULE$.invalidEvent());
        expectedEvents.add(event1);

        Assert.assertEquals(expectedEvents, pair.second().toCompletableFuture().get());
    }

    @Test
    public void redisIndependentSubscriptions() throws InterruptedException, ExecutionException, TimeoutException {
        Prefix prefix = new Prefix("test.prefix");
        EventName eventName1 = new EventName("system1");
        EventName eventName2 = new EventName("system2");
        Event event1 = new SystemEvent(prefix, eventName1);
        Event event2 = new SystemEvent(prefix, eventName2);

        java.util.Set<EventKey> set = new HashSet<>();
        set.add(event1.eventKey());

        Pair<IEventSubscription, CompletionStage<List<Event>>> pair = subscriber.subscribe(set).toMat(Sink.seq(), Keep.both()).run(wiring.resumingMat());
        Thread.sleep(1000);
        publisher.publish(event1).get(10, TimeUnit.SECONDS);
        Thread.sleep(1000);

        java.util.Set<EventKey> set2 = new HashSet<>();
        set2.add(event2.eventKey());

        Pair<IEventSubscription, CompletionStage<List<Event>>> pair2 = subscriber.subscribe(set2).toMat(Sink.seq(), Keep.both()).run(wiring.resumingMat());
        Thread.sleep(1000);
        publisher.publish(event2).get(10, TimeUnit.SECONDS);
        Thread.sleep(1000);

        pair.first().unsubscribe().get(10, TimeUnit.SECONDS);
        pair2.first().unsubscribe().get(10, TimeUnit.SECONDS);

        List<Event> expectedEvents = new ArrayList<>();
        expectedEvents.add(Event$.MODULE$.invalidEvent());
        expectedEvents.add(event1);

        Assert.assertEquals(expectedEvents, pair.second().toCompletableFuture().get());

        List<Event> expectedEvents2 = new ArrayList<>();
        expectedEvents2.add(Event$.MODULE$.invalidEvent());
        expectedEvents2.add(event2);
        Assert.assertEquals(expectedEvents2, pair2.second().toCompletableFuture().get());
    }

    @Test
    public void shouldBeAbleToPublishConcurrentlyToTheSameChannel() throws InterruptedException {
        java.util.List<Event> events = new ArrayList<>();
        for (int i = 1; i < 11; i++) {
            events.add(Utils.makeEvent(i));
        }

        Set<EventKey> eventKeys = new HashSet<>();
        eventKeys.add(Utils.makeEvent(0).eventKey());

        List<Event> queue = new ArrayList<>();
        subscriber.subscribe(eventKeys).runForeach(queue::add, ActorMaterializer.create(clusterSettings.system()));

        Thread.sleep(10);

        cancellable = publisher.publish(() -> {
            counter += 1;
            if (counter == 10) cancellable.cancel();
            return events.get(counter);
        }, new FiniteDuration(2, TimeUnit.MILLISECONDS));


        Thread.sleep(1000); //TODO : Try to replace with Await

        // subscriber will receive an invalid event first as subscription happened before publishing started.
        // The 10 published events will follow
        Assert.assertEquals(11, queue.size());

        events.add(0, Event$.MODULE$.invalidEvent());
        Assert.assertEquals(events, queue);
    }
}