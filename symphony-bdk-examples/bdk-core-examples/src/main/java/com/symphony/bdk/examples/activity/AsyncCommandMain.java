package com.symphony.bdk.examples.activity;

import static com.symphony.bdk.core.activity.command.SlashCommand.slash;
import static com.symphony.bdk.core.config.BdkConfigLoader.loadFromSymphonyDir;

import com.symphony.bdk.core.SymphonyBdk;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AsyncCommandMain {
  public static void main(String[] args) throws Exception {

    // setup SymphonyBdk facade object
    final SymphonyBdk bdk = new SymphonyBdk(loadFromSymphonyDir("config.yaml"));

    bdk.activities().register(slash("/async", true, true, context ->
            bdk.messages().send(context.getStreamId(),
                "This is an asynchronous command that should not block next commands"),
        "Asynchronous command example"
    ));

    // finally, start the datafeed loop
    bdk.datafeed().start();
  }
}
