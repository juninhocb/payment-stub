package com.example.payment;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.support.DefaultStateMachineContext;
import org.springframework.test.context.ActiveProfiles;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.UUID;


@ActiveProfiles("test")
@SpringBootTest
class StateMachineConfigTest {

    @Autowired
    StateMachineFactory<States, Events> factory;

    @Test
    void shouldSeeStateMachineChanges() {

        StateMachine<States, Events> sm = factory.getStateMachine(UUID.randomUUID());
        Mono<Message<Events>> msgToSm1 = Mono.just(MessageBuilder
                .withPayload(Events.PRE_AUTHORIZE).build());


        Mono<Message<Events>> msgToSm2 = Mono.just(
                MessageBuilder.withPayload(Events.PRE_AUTH_APPROVED).build()
        );

        DefaultStateMachineContext<States, Events> machineContext = new DefaultStateMachineContext<>(States.NEW, null, null, null);

        sm.getStateMachineAccessor()
                .doWithAllRegions(sma -> {
                    sma.resetStateMachineReactively(machineContext).subscribe();
                });


        sm.startReactively().publishOn(Schedulers.boundedElastic()).doFirst(() -> {
            sm.sendEvent(msgToSm1).doOnComplete(() -> {
                System.out.println("Completed task");
            }).subscribe();

            sm.sendEvent(msgToSm2).doOnComplete(() -> {
                System.out.println("Completed Task Two!");
            }).subscribe();
        }).subscribe();

    }
}