package csw.framework.javadsl.components;

import akka.typed.ActorRef;
import akka.typed.javadsl.ActorContext;
import csw.framework.javadsl.JComponentBehaviorFactory;
import csw.framework.javadsl.JComponentHandlers;
import csw.framework.models.ComponentInfo;
import csw.param.messages.ComponentMessage;
import csw.param.messages.PubSub;
import csw.param.states.CurrentState;
import csw.services.location.javadsl.ILocationService;

public class JSampleComponentBehaviorFactory extends JComponentBehaviorFactory<JComponentDomainMessage> {
    public JSampleComponentBehaviorFactory() {
        super(JComponentDomainMessage.class);
    }

    @Override
    public JComponentHandlers<JComponentDomainMessage> jHandlers(
            ActorContext<ComponentMessage> ctx,
            ComponentInfo componentInfo,
            ActorRef<PubSub.PublisherMessage<CurrentState>> pubSubRef,
            ILocationService locationService) {
        return new JSampleComponentHandlers(ctx, componentInfo, pubSubRef, locationService, JComponentDomainMessage.class);
    }
}