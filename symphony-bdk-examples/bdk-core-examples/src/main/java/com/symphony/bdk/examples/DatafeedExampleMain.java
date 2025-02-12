package com.symphony.bdk.examples;

import static com.symphony.bdk.core.config.BdkConfigLoader.loadFromSymphonyDir;

import com.symphony.bdk.core.SymphonyBdk;
import com.symphony.bdk.core.service.datafeed.EventPayload;
import com.symphony.bdk.core.service.datafeed.RealTimeEventListener;
import com.symphony.bdk.gen.api.model.V4Initiator;
import com.symphony.bdk.gen.api.model.V4MessageSent;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DatafeedExampleMain {

  public static void main(String[] args) throws Exception {

    final SymphonyBdk bdk = new SymphonyBdk(loadFromSymphonyDir("config.yaml"));

    bdk.datafeed().subscribe(new RealTimeEventListener() {

      @Override
      public void onMessageSent(V4Initiator initiator, V4MessageSent event) {
        log.info("event was triggered at {}", ((EventPayload) event).getEventTimestamp());
        bdk.messages().send(event.getMessage().getStream(), "<messageML>Hello, World!</messageML>");
      }
    });

    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      log.info("Stopping Datafeed...");
      bdk.datafeed().stop();
    }));

    bdk.datafeed().start();
  }
}
